package io.xconn.xconn

import io.xconn.wampproto.Session
import io.xconn.wampproto.SessionScopeIDGenerator
import io.xconn.wampproto.messages.Call
import io.xconn.wampproto.messages.Error
import io.xconn.wampproto.messages.Goodbye
import io.xconn.wampproto.messages.Message
import io.xconn.wampproto.messages.Publish
import io.xconn.wampproto.messages.Published
import io.xconn.wampproto.messages.Register
import io.xconn.wampproto.messages.Registered
import io.xconn.wampproto.messages.Subscribe
import io.xconn.wampproto.messages.Subscribed
import io.xconn.wampproto.messages.Unregister
import io.xconn.wampproto.messages.Unregistered
import io.xconn.wampproto.messages.Unsubscribe
import io.xconn.wampproto.messages.Unsubscribed
import io.xconn.wampproto.messages.Yield
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import io.xconn.wampproto.messages.Event as EventMsg
import io.xconn.wampproto.messages.Invocation as InvocationMsg
import io.xconn.wampproto.messages.Result as ResultMsg

class Session(private val baseSession: BaseSession) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
    private val wampSession: Session = Session(baseSession.serializer())
    private var idGen: SessionScopeIDGenerator = SessionScopeIDGenerator()

    private val callRequests: MutableMap<Long, CompletableDeferred<Result>> = mutableMapOf()
    private val registerRequests: MutableMap<Long, RegisterRequest> = mutableMapOf()
    private val registrations: MutableMap<Long, (Invocation) -> Result> = mutableMapOf()
    private val unregisterRequests: MutableMap<Long, UnregisterRequest> = mutableMapOf()

    private val publishRequests: MutableMap<Long, CompletableDeferred<Unit>> = mutableMapOf()
    private val subscribeRequests: MutableMap<Long, SubscribeRequest> = mutableMapOf()
    private val subscriptions: MutableMap<Long, (Event) -> Unit> = mutableMapOf()
    private val unsubscribeRequests: MutableMap<Long, UnsubscribeRequest> = mutableMapOf()

    private val goodbyeRequest: CompletableDeferred<Unit> = CompletableDeferred()

    init {
        coroutineScope.launch {
            while (isActive) {
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

        try {
            withTimeout(10_000L) {
                goodbyeRequest.await()
            }
        } catch (e: TimeoutCancellationException) {
            coroutineScope.cancel()
            baseSession.close()
        } finally {
            coroutineScope.cancel()
            baseSession.close()
        }
    }

    private suspend fun processIncomingMessage(message: Message) {
        when (message) {
            is ResultMsg -> {
                val request = callRequests.remove(message.requestID)
                request?.complete(Result(message.args, message.kwargs, message.details))
            }
            is Registered -> {
                val request = registerRequests.remove(message.requestID)
                if (request != null) {
                    registrations[message.registrationID] = request.endpoint
                    request.completable.complete(Registration(message.registrationID))
                }
            }
            is InvocationMsg -> {
                val endpoint = registrations[message.registrationID]
                if (endpoint != null) {
                    var msgToSend: Message
                    try {
                        val result = endpoint.invoke(Invocation(message.args, message.kwargs, message.details))
                        msgToSend = Yield(message.requestID, result.args, result.kwargs, result.details)
                    } catch (e: ApplicationError) {
                        msgToSend = Error(message.type(), message.requestID, e.message, e.args, e.kwargs)
                    } catch (e: Exception) {
                        msgToSend = Error(message.type(), message.requestID, errorRuntimeError, listOf(e.toString()))
                    }

                    val data = wampSession.sendMessage(msgToSend)
                    baseSession.send(data)
                }
            }
            is Unregistered -> {
                val request = unregisterRequests.remove(message.requestID)
                if (request != null) {
                    registrations.remove(request.registrationID)
                    request.completable.complete(Unit)
                }
            }
            is Published -> {
                val request = publishRequests.remove(message.requestID)
                request?.complete(Unit)
            }
            is Subscribed -> {
                val request = subscribeRequests.remove(message.requestID)
                if (request != null) {
                    subscriptions[message.subscriptionID] = request.endpoint
                    request.completable.complete(Subscription(message.subscriptionID))
                }
            }
            is EventMsg -> {
                val endpoint = subscriptions[message.subscriptionID]
                if (endpoint != null) {
                    endpoint(Event(message.args, message.kwargs, message.details))
                }
            }
            is Unsubscribed -> {
                val request = unsubscribeRequests.remove(message.requestID)
                if (request != null) {
                    subscriptions.remove(request.subscriptionID)
                    request.completable.complete(Unit)
                }
            }
            is Goodbye -> {
                goodbyeRequest.complete(Unit)
            }
            is Error -> {
                when (message.messageType) {
                    Call.TYPE -> {
                        val callRequest = callRequests.remove(message.requestID)
                        callRequest?.completeExceptionally(ApplicationError(message.uri, message.args, message.kwargs))
                    }
                    Register.TYPE -> {
                        val registerRequest = registerRequests.remove(message.requestID)
                        registerRequest?.completable?.completeExceptionally(
                            ApplicationError(message.uri, message.args, message.kwargs),
                        )
                    }
                    Unregister.TYPE -> {
                        val unregisterRequest = unregisterRequests.remove(message.requestID)
                        unregisterRequest?.completable?.completeExceptionally(
                            ApplicationError(message.uri, message.args, message.kwargs),
                        )
                    }
                }
            }
            else -> {
                throw ProtocolError("Unexpected message type ${message.javaClass.name}")
            }
        }
    }

    suspend fun call(
        procedure: String,
        args: List<Any>? = null,
        kwargs: Map<String, Any>? = null,
        options: Map<String, Any> = emptyMap(),
    ): CompletableDeferred<Result> {
        val call = Call(nextID, procedure, args, kwargs, options)

        val completer = CompletableDeferred<Result>()
        callRequests[call.requestID] = completer

        baseSession.send(wampSession.sendMessage(call))

        return completer
    }

    suspend fun register(
        procedure: String,
        endpoint: (Invocation) -> Result,
        options: Map<String, Any>? = emptyMap(),
    ): CompletableDeferred<Registration> {
        val register = Register(nextID, procedure, options)

        val completable = CompletableDeferred<Registration>()
        registerRequests[register.requestID] = RegisterRequest(completable, endpoint)

        baseSession.send(wampSession.sendMessage(register))

        return completable
    }

    suspend fun unregister(reg: Registration): CompletableDeferred<Unit> {
        val unregister = Unregister(nextID, reg.registrationID)

        val completable = CompletableDeferred<Unit>()
        unregisterRequests[unregister.requestID] = UnregisterRequest(completable, reg.registrationID)

        baseSession.send(wampSession.sendMessage(unregister))

        return completable
    }

    suspend fun publish(
        topic: String,
        args: List<Any>? = null,
        kwargs: Map<String, Any>? = null,
        options: Map<String, Any> = emptyMap(),
    ): CompletableDeferred<Unit>? {
        val publish = Publish(nextID, topic, args, kwargs, options)

        baseSession.send(wampSession.sendMessage(publish))

        val ack = options["acknowledge"] as? Boolean ?: false
        if (ack) {
            val completer = CompletableDeferred<Unit>()
            publishRequests[publish.requestID] = completer

            return completer
        }

        return null
    }

    suspend fun subscribe(
        topic: String,
        endpoint: (Event) -> Unit,
        options: Map<String, Any>? = emptyMap(),
    ): CompletableDeferred<Subscription> {
        val subscribe = Subscribe(nextID, topic, options)

        val completable = CompletableDeferred<Subscription>()
        subscribeRequests[subscribe.requestID] = SubscribeRequest(completable, endpoint)

        baseSession.send(wampSession.sendMessage(subscribe))

        return completable
    }

    suspend fun unsubscribe(sub: Subscription): CompletableDeferred<Unit> {
        val unsubscribe = Unsubscribe(nextID, sub.subscriptionID)

        val completable = CompletableDeferred<Unit>()
        unsubscribeRequests[unsubscribe.requestID] = UnsubscribeRequest(completable, sub.subscriptionID)

        baseSession.send(wampSession.sendMessage(unsubscribe))

        return completable
    }
}
