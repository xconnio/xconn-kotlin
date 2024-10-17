package io.xconn.xconn

import io.xconn.wampproto.serializers.CBORSerializer
import io.xconn.wampproto.serializers.JSONSerializer
import io.xconn.wampproto.serializers.MsgPackSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HelpersTest {
    @Test
    fun getSubProtocol() {
        // with json Serializer
        val jsonProtocol = getSubProtocol(JSONSerializer())
        assertEquals(JSON_SUB_PROTOCOL, jsonProtocol)

        // with cbor Serializer
        val cborProtocol = getSubProtocol(CBORSerializer())
        assertEquals(CBOR_SUB_PROTOCOL, cborProtocol)

        // with msgpack Serializer
        val msgpackProtocol = getSubProtocol(MsgPackSerializer())
        assertEquals(MSGPACK_SUB_PROTOCOL, msgpackProtocol)
    }
}
