package io.xconn.xconn

import io.xconn.wampproto.serializers.CBORSerializer
import io.xconn.wampproto.serializers.JSONSerializer
import io.xconn.wampproto.serializers.MsgPackSerializer
import io.xconn.wampproto.serializers.Serializer

const val JSON_SUB_PROTOCOL = "wamp.2.json"
const val CBOR_SUB_PROTOCOL = "wamp.2.cbor"
const val MSGPACK_SUB_PROTOCOL = "wamp.2.msgpack"

fun getSubProtocol(serializer: Serializer): String {
    return when (serializer) {
        is JSONSerializer -> JSON_SUB_PROTOCOL
        is CBORSerializer -> CBOR_SUB_PROTOCOL
        is MsgPackSerializer -> MSGPACK_SUB_PROTOCOL
        else -> throw IllegalArgumentException("invalid serializer")
    }
}
