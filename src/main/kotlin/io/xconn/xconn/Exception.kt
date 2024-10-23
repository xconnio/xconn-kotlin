package io.xconn.xconn

class ApplicationError(
    override val message: String,
    val args: List<Any>? = null,
    val kwargs: Map<String, Any>? = null,
) : Exception() {
    override fun toString(): String {
        var errStr = message
        if (!args.isNullOrEmpty()) {
            val argsStr = args.joinToString(", ") { it.toString() }
            errStr += ": $argsStr"
        }
        if (!kwargs.isNullOrEmpty()) {
            val kwargsStr = kwargs.entries.joinToString(", ") { "${it.key}=${it.value}" }
            errStr += ": $kwargsStr"
        }
        return errStr
    }
}

class ProtocolError(override val message: String) : Exception()
