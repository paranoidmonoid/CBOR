package kt.serialization

inline fun <reified T : Any?> encode(input: T): ByteArray {
    return encodeInternal(input).toByteArray()
}


inline fun <reified T : Any> decode(input: ByteArray): T? {
    return decodeInternal(input.toMutableList())
}

fun main(args: Array<String>) {
//    val e = null
    val message = arrayOf((-10).toByte()).toByteArray()
    println(message.toList())
    println(decode<Boolean>(message))
}

enum class Ex {
    ONE, TWO
}

data class example(val e: String = "aedve", val i: Int = 900, val f: Float = 1.6F)