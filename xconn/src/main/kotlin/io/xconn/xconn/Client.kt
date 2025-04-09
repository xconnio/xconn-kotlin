package io.xconn.xconn

class Client(
    private var config: ClientConfig = ClientConfig(),
) {
    suspend fun connect(url: String, realm: String): Session {
        val joiner = WAMPSessionJoiner(config)
        val baseSession: BaseSession = joiner.join(url, realm)

        return Session(baseSession)
    }
}
