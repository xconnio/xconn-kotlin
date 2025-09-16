package io.xconn.xconn

import io.xconn.wampproto.auth.AnonymousAuthenticator
import io.xconn.wampproto.auth.CRAAuthenticator
import io.xconn.wampproto.auth.ClientAuthenticator
import io.xconn.wampproto.auth.CryptoSignAuthenticator
import io.xconn.wampproto.auth.TicketAuthenticator
import io.xconn.wampproto.serializers.CBORSerializer
import io.xconn.wampproto.serializers.JSONSerializer
import io.xconn.wampproto.serializers.MsgPackSerializer
import io.xconn.wampproto.serializers.Serializer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class InteropTest {
    private val xconnURL = "ws://localhost:8080/ws"
    private val crossbarURL = "ws://localhost:8081/ws"
    private val realm = "realm1"
    private val procedureAdd = "io.xconn.backend.add2"

    private suspend fun testCall(authenticator: ClientAuthenticator, serializer: Serializer, url: String) {
        val client = Client(authenticator, serializer)
        val session = client.connect(url, realm)
        val result = session.call(procedureAdd, listOf(2, 2)).await()
        val actual = (result.args!![0] as Number).toInt()
        assertEquals(4, actual)
    }

    private suspend fun testRPC(authenticator: ClientAuthenticator, serializer: Serializer, url: String) {
        val client = Client(authenticator, serializer)
        val session = client.connect(url, realm)

        val reg =
            session
                .register("io.xconn.test", { invocation ->
                    Result(args = invocation.args, kwargs = invocation.kwargs)
                })
                .await()

        val args = listOf("Hello", "wamp")
        val result = session.call("io.xconn.test", args).await()
        assertEquals(args, result.args)

        session.unregister(reg)
    }

    private suspend fun testPubSub(authenticator: ClientAuthenticator, serializer: Serializer, url: String) {
        val client = Client(authenticator, serializer)
        val session = client.connect(url, realm)

        val args = listOf("Hello", "wamp")
        val subscription =
            session
                .subscribe("io.xconn.test", { event ->
                    assertEquals(args, event.args)
                })
                .await()

        session.publish("io.xconn.test", args, mapOf("acknowledge" to true))?.await()

        session.unsubscribe(subscription)
    }

    @Nested
    inner class IntegrationTests {
        @Test
        fun runIntegrationTests() =
            runBlocking {
                val serverURLs = listOf(xconnURL, crossbarURL)

                val authenticators =
                    listOf(
                        AnonymousAuthenticator(""),
                        TicketAuthenticator("ticket-user", "ticket-pass"),
                        CRAAuthenticator("wamp-cra-user", "cra-secret"),
                        CRAAuthenticator("wamp-cra-salt-user", "cra-salt-secret"),
                        CryptoSignAuthenticator(
                            "cryptosign-user",
                            "150085398329d255ad69e82bf47ced397bcec5b8fbeecd28a80edbbd85b49081",
                            mutableMapOf(),
                        ),
                    )

                val serializers = listOf(CBORSerializer(), MsgPackSerializer(), JSONSerializer())

                serverURLs.forEach { url ->
                    authenticators.forEach { authenticator ->
                        serializers.forEach { serializer ->
                            testCall(authenticator, serializer, url)
                            testRPC(authenticator, serializer, url)
                            testPubSub(authenticator, serializer, url)
                        }
                    }
                }
            }
    }
}
