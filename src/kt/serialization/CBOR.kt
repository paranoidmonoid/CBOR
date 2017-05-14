package kt.serialization

inline fun <reified T : Any?> encode(input: T): ByteArray {
    return encodeInternal(input).toByteArray()
}


inline fun <reified T : Any> decode(input: ByteArray): T? {
    return decodeInternal(input.toMutableList())
}