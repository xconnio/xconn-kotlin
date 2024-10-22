package io.xconn.xconn

import io.xconn.wampproto.Session
import io.xconn.wampproto.SessionScopeIDGenerator
import io.xconn.wampproto.messages.Goodbye
import io.xconn.wampproto.messages.Message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

class Session(private val baseSession: BaseSession) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
    private val wampSession: Session = Session(baseSession.serializer())
    private var idGen: SessionScopeIDGenerator = SessionScopeIDGenerator()

    private val goodbyeRequest: CompletableDeferred<Unit> = CompletableDeferred()

    init {
        coroutineScope.launch {
            while (true) {
                try {
                    val message = baseSession.receive()
                    processIncomingMessage(wampSession.receive(message))
                } catch (e: CancellationException) {
                    e.printStackTrace()
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }
        }
    }

    private val nextID: Long
        get() = idGen.next()

    suspend fun close() {
        val goodbyeMsg = Goodbye(mapOf(), closeCloseRealm)
        val data = wampSession.sendMessage(goodbyeMsg)
        baseSession.send(data)
        coroutineScope.cancel()

        try {
            withTimeout(10_000L) {
                goodbyeRequest.await()
            }
        } catch (e: TimeoutCancellationException) {
            baseSession.close()
        } finally {
            baseSession.close()
        }
    }

    private suspend fun processIncomingMessage(message: Message) {
        when (message) {
            is Goodbye -> {
                goodbyeRequest.complete(Unit)
            }
            else -> {
                throw ProtocolError("Unexpected message type ${message.javaClass.name}")
            }
        }
    }
}
