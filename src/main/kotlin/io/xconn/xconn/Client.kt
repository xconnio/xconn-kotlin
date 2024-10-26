package io.xconn.xconn

import io.xconn.wampproto.auth.AnonymousAuthenticator
import io.xconn.wampproto.auth.ClientAuthenticator
import io.xconn.wampproto.serializers.JSONSerializer
import io.xconn.wampproto.serializers.Serializer

class Client(
    private var authenticator: ClientAuthenticator = AnonymousAuthenticator(""),
    private var serializer: Serializer = JSONSerializer(),
) {
    suspend fun connect(url: String, realm: String): Session {
        val joiner = WAMPSessionJoiner(authenticator, serializer)
        val baseSession: BaseSession = joiner.join(url, realm)

        return Session(baseSession)
    }
}
