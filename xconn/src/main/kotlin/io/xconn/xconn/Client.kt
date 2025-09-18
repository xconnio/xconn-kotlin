package io.xconn.xconn

import io.xconn.wampproto.auth.AnonymousAuthenticator
import io.xconn.wampproto.auth.CRAAuthenticator
import io.xconn.wampproto.auth.ClientAuthenticator
import io.xconn.wampproto.auth.CryptoSignAuthenticator
import io.xconn.wampproto.auth.TicketAuthenticator
import io.xconn.wampproto.serializers.CBORSerializer
import io.xconn.wampproto.serializers.JSONSerializer
import io.xconn.wampproto.serializers.Serializer

class Client(
    private var authenticator: ClientAuthenticator = AnonymousAuthenticator(""),
    private var serializer: Serializer = JSONSerializer(),
) {
    suspend fun connect(uri: String, realm: String): Session {
        return connect(uri, realm, authenticator, serializer)
    }
}

private suspend fun connect(
    uri: String,
    realm: String,
    authenticator: ClientAuthenticator = AnonymousAuthenticator(""),
    serializer: Serializer = CBORSerializer(),
): Session {
    val joiner = WAMPSessionJoiner(authenticator, serializer)
    val baseSession: BaseSession = joiner.join(uri, realm)

    return Session(baseSession)
}

suspend fun connectAnonymous(uri: String, realm: String): Session {
    return connect(uri, realm)
}

suspend fun connectTicket(uri: String, realm: String, authid: String, ticket: String): Session {
    val ticketAuthenticator = TicketAuthenticator(authid, ticket)

    return connect(uri, realm, authenticator = ticketAuthenticator)
}

suspend fun connectCRA(uri: String, realm: String, authid: String, secret: String): Session {
    val craAuthenticator = CRAAuthenticator(authID = authid, secret = secret)

    return connect(uri, realm, authenticator = craAuthenticator)
}

suspend fun connectCryptosign(uri: String, realm: String, authid: String, privateKey: String): Session {
    val cryptosignAuthenticator = CryptoSignAuthenticator(authid, privateKey)

    return connect(uri, realm, authenticator = cryptosignAuthenticator)
}
