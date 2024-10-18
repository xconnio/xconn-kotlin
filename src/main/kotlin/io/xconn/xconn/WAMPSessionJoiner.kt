package io.xconn.xconn

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.xconn.wampproto.Joiner
import io.xconn.wampproto.auth.AnonymousAuthenticator
import io.xconn.wampproto.auth.ClientAuthenticator
import io.xconn.wampproto.serializers.JSONSerializer
import io.xconn.wampproto.serializers.Serializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

class WAMPSessionJoiner(
    private val authenticator: ClientAuthenticator = AnonymousAuthenticator(""),
    private val serializer: Serializer = JSONSerializer(),
) {
    private val client = HttpClient(CIO) { install(WebSockets) }

    suspend fun join(host: String, port: Int, realm: String): BaseSession {
        val welcomeCompleter = CompletableDeferred<BaseSession>()
        val subProtocol = getSubProtocol(serializer)
        val joiner = Joiner(realm, serializer, authenticator)

        client.webSocket(
            method = HttpMethod.Get,
            host = host,
            port = port,
            request = { header("Sec-WebSocket-Protocol", subProtocol) },
        ) {
            // Send initial Hello message
            sendFrame(joiner.sendHello())

            // Handle incoming messages
            launch {
                for (frame in incoming) {
                    try {
                        val receivedData = receiveFrame(frame)
                        val toSend = joiner.receive(receivedData)

                        if (toSend == null) {
                            // Complete handshake and session creation
                            welcomeCompleter.complete(BaseSession(this@webSocket, joiner.getSessionDetails(), serializer))
                        } else {
                            sendFrame(toSend)
                        }
                    } catch (error: Exception) {
                        welcomeCompleter.completeExceptionally(error)
                    }
                }
            }
        }

        return welcomeCompleter.await()
    }
}

internal suspend fun DefaultWebSocketSession.sendFrame(data: Any) {
    when (data) {
        is String -> outgoing.send(Frame.Text(data))
        is ByteArray -> outgoing.send(Frame.Binary(true, data))
        else -> throw IllegalArgumentException("Unsupported frame type")
    }
}

internal fun receiveFrame(frame: Frame): Any {
    return when (frame) {
        is Frame.Text -> frame.readText()
        is Frame.Binary -> frame.readBytes()
        else -> throw Exception("Unsupported frame type")
    }
}
