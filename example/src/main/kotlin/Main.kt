package io.xconn

import io.xconn.wampproto.auth.CRAAuthenticator
import io.xconn.wampproto.auth.CryptoSignAuthenticator
import io.xconn.wampproto.auth.TicketAuthenticator
import io.xconn.wampproto.serializers.CBORSerializer
import io.xconn.wampproto.serializers.JSONSerializer
import io.xconn.wampproto.serializers.Serializer
import io.xconn.xconn.Client
import io.xconn.xconn.Result
import io.xconn.xconn.Session
import kotlinx.coroutines.runBlocking

const val URL = "ws://localhost:8080/ws"
const val REALM = "realm1"

fun main() =
    runBlocking {
        val callee = connectAnonymous()
        // register a procedure
        val reg =
            callee.register("test", { invocation ->
                Result(args = invocation.args, kwargs = invocation.kwargs)
            }).await()

        val caller = connectTicket("ticket-user", "ticket-pass", JSONSerializer())
        // call a procedure
        val result = caller.call("test", args = listOf("abc", 123)).await()
        println(result)

        // unregister a procedure
        callee.unregister(reg).await()

        val subscriber = connectCRA("wamp-cra-user", "cra-secret", CBORSerializer())
        // subscribe to a topic
        val subscription =
            subscriber.subscribe("test", { event ->
                println(event)
            }).await()

        val publisher =
            connectCryptosign(
                "cryptosign-user",
                "150085398329d255ad69e82bf47ced397bcec5b8fbeecd28a80edbbd85b49081",
                CBORSerializer(),
            )
        // publish to a topic
        publisher.publish("test", args = listOf("abc", 123), kwargs = mapOf("key" to "value"))?.await()

        // unsubscribe to topic
        subscriber.unsubscribe(subscription).await()
    }

suspend fun connectAnonymous(): Session {
    val client = Client()

    return client.connect(URL, REALM)
}

suspend fun connectTicket(authID: String, ticket: String, serializer: Serializer): Session {
    val client = Client(TicketAuthenticator(authID, emptyMap(), ticket), serializer)

    return client.connect(URL, REALM)
}

suspend fun connectCRA(authID: String, secret: String, serializer: Serializer): Session {
    val client = Client(CRAAuthenticator(authID, emptyMap(), secret), serializer)

    return client.connect(URL, REALM)
}

suspend fun connectCryptosign(authID: String, privateKey: String, serializer: Serializer): Session {
    val client = Client(CryptoSignAuthenticator(authID, privateKey, mutableMapOf()), serializer)

    return client.connect(URL, REALM)
}
