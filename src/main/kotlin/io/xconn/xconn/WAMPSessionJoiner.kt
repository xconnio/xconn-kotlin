package io.xconn.xconn

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class WAMPSessionJoiner(
    private val authenticator: ClientAuthenticator = AnonymousAuthenticator(""),
    private val serializer: Serializer = JSONSerializer(),
) {
    private val subProtocol = getSubProtocol(serializer)
    private val client =
        HttpClient(CIO) {
            install(WebSockets)
            defaultRequest {
                header("Sec-WebSocket-Protocol", subProtocol)
            }
        }

    suspend fun join(url: String, realm: String): BaseSession {
        val welcomeCompleter = CompletableDeferred<BaseSession>()
        val joiner = Joiner(realm, serializer, authenticator)

        val session = client.webSocketSession(url)

        // Send initial Hello message
        session.sendFrame(joiner.sendHello())

        coroutineScope {
            val handshakeJob =
                async {
                    for (frame in session.incoming) {
                        try {
                            val receivedData = receiveFrame(frame)
                            val toSend = joiner.receive(receivedData)

                            if (toSend == null) {
                                // Complete handshake and session creation
                                welcomeCompleter.complete(BaseSession(session, joiner.getSessionDetails(), serializer))
                                break
                            } else {
                                session.sendFrame(toSend)
                            }
                        } catch (error: Exception) {
                            welcomeCompleter.completeExceptionally(error)
                            break
                        }
                    }
                }

            handshakeJob.await()
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
