package io.xconn

import io.xconn.xconn.Result
import io.xconn.xconn.connectAnonymous
import io.xconn.xconn.connectCRA
import io.xconn.xconn.connectCryptosign
import io.xconn.xconn.connectTicket

const val URI = "ws://localhost:8080/ws"
const val REALM = "realm1"

suspend fun main() {
    val callee = connectAnonymous(URI, REALM)

    // register a procedure
    val reg =
        callee.register("test", { invocation ->
            Result(args = invocation.args, kwargs = invocation.kwargs)
        }).await()

    val caller = connectTicket(URI, REALM, "ticket-user", "ticket-pass")
    // call a procedure
    val result = caller.call("test", args = listOf("abc", 123)).await()
    println(result)

    // unregister a procedure
    callee.unregister(reg).await()

    val subscriber = connectCRA(URI, REALM, "wamp-cra-user", "cra-secret")
    // subscribe to a topic
    val subscription =
        subscriber.subscribe("test", { event ->
            println(event)
        }).await()

    val publisher =
        connectCryptosign(
            URI,
            REALM,
            "cryptosign-user",
            "150085398329d255ad69e82bf47ced397bcec5b8fbeecd28a80edbbd85b49081",
        )
    // publish to a topic
    publisher.publish("test", args = listOf("abc", 123), kwargs = mapOf("key" to "value"))
        ?.await()

    // unsubscribe to topic
    subscriber.unsubscribe(subscription).await()
}
