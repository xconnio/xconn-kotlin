package io.xconn.xconn

import io.xconn.wampproto.Session
import io.xconn.wampproto.SessionScopeIDGenerator
import io.xconn.wampproto.messages.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class Session(private val baseSession: BaseSession) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
    private val wampSession: Session = Session(baseSession.serializer())
    private var idGen: SessionScopeIDGenerator = SessionScopeIDGenerator()

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

    suspend fun close() {}

    private suspend fun processIncomingMessage(message: Message) {}
}
