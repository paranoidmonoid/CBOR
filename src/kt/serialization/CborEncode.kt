package kt.serialization

import kotlin.reflect.full.memberProperties

fun <T : Any?> encodeInternal(input: T): MutableList<Byte> {
    return when (input) {
        null -> encodeNull()
        is Boolean -> encodeBoolean(input)
        is String -> encodeString(input)
        is Enum<*> -> encodeString(input.name)
        is ByteArray -> encodeByteArray(input)

        is Int -> encodeNumber(input.toLong())
        is Byte -> encodeNumber(input.toLong())
        is Short -> encodeNumber(input.toLong())
        is Long -> encodeNumber(input)

        is Float -> encodeFloat(input)
        is Double -> encodeDouble(input)

        is Array<*> -> encodeArray(input)
        else -> encodeMap(input as Any)
    }
}

private fun encodeArray(input: Array<*>): MutableList<Byte> {
    val result = ArrayList<Byte>()
    val additionalInformation = when {
        input.size < 24 -> input.size
        input.size < (1L shl 8) -> UINT8_T
        input.size < (1L shl 16) -> UINT16_T
        else -> UINT32_T
    }
    result.add((MAJOR_ARRAY.shl(5) + additionalInformation).toByte())
    when (additionalInformation) {
        UINT8_T -> result.add(input.size.toByte())
        UINT16_T -> result.addAll(shortToBytes(input.size))
        UINT32_T -> result.addAll(intToBytes(input.size))
    }
    input.forEach { result.addAll(encodeInternal(it)) }
    return result
}

private fun encodeNull(): MutableList<Byte> {
    val result = ArrayList<Byte>()
    result[0] = (MAJOR_OTHER.shl(5) + NULL).toByte()
    return result
}

private inline fun <reified T : Any> encodeMap(input: T): MutableList<Byte> {
    val memberProperties = input.javaClass.kotlin.memberProperties
    val result = ArrayList<Byte>()
    val size = memberProperties.size
    val additionalInformation = when {
        size < 24 -> size
        size < (1L shl 8) -> UINT8_T
        size < (1L shl 16) -> UINT16_T
        else -> UINT32_T
    }
    result.add((MAJOR_MAP.shl(5) + additionalInformation).toByte())
    when (additionalInformation) {
        UINT8_T -> result.add(size.toByte())
        UINT16_T -> result.addAll(shortToBytes(size))
        UINT32_T -> result.addAll(intToBytes(size))
    }
    for (it in memberProperties) {
        result.addAll(encodeString(it.name))
        result.addAll(encodeInternal(it.get(input)))
    }
    return result
}

private fun encodeBoolean(input: Boolean): MutableList<Byte> {
    val result = ArrayList<Byte>()
    result.add((MAJOR_OTHER.shl(5) + if (input) TRUE else FALSE).toByte())
    return result
}

private fun encodeDouble(input: Double): MutableList<Byte> {
    val result = ArrayList<Byte>()
    result.add((MAJOR_OTHER.shl(5) + DOUBLE_PRECISION_FLOAT).toByte())
    val doubleToRawIntBits = java.lang.Double.doubleToRawLongBits(input)
    result.addAll(longToBytes(doubleToRawIntBits))
    return result
}

private fun encodeFloat(input: Float): MutableList<Byte> {
    val result = ArrayList<Byte>()
    result.add((MAJOR_OTHER.shl(5) + SINGLE_PRECISION_FLOAT).toByte())
    val floatToRawIntBits = java.lang.Float.floatToRawIntBits(input)
    result.addAll(intToBytes(floatToRawIntBits))
    return result
}

private fun encodeByteArray(input: ByteArray): MutableList<Byte> {
    val result = ArrayList<Byte>()
    val size = input.size
    val additionalInformation = when {
        size < 24 -> size
        size < 256 -> UINT8_T
        size < 65536 -> UINT16_T
        else -> UINT32_T
    }
    result.add((MAJOR_BYTE_STRING.shl(5) + additionalInformation).toByte())
    when (additionalInformation) {
        UINT8_T -> result.add(size.toByte())
        UINT16_T -> result.addAll(shortToBytes(size))
        UINT32_T -> result.addAll(intToBytes(size))
    }
    result.addAll(input.toList())
    return result
}

private fun encodeString(input: String): MutableList<Byte> {
    val result = ArrayList<Byte>()
    val size = input.length
    val additionalInformation = when {
        size < 24 -> size
        size < (1L shl 8) -> UINT8_T
        size < (1L shl 16) -> UINT16_T
        else -> UINT32_T
    }
    result.add((MAJOR_TEXT_STRING.shl(5) + additionalInformation).toByte())
    when (additionalInformation) {
        UINT8_T -> result.add(size.toByte())
        UINT16_T -> result.addAll(shortToBytes(size))
        UINT32_T -> result.addAll(intToBytes(size))
    }
    result.addAll(input.toByteArray().toList())
    return result
}

private fun encodeNumber(input: Long): MutableList<Byte> {
    val unsigned = input >= 0
    val convertedInput = if (unsigned) input else input.inv()
    val additionalInformation = when {
        convertedInput < 24 -> convertedInput
        convertedInput < (1L shl 8) -> UINT8_T.toLong()
        convertedInput < (1L shl 16) -> UINT16_T.toLong()
        convertedInput < (1L shl 32) -> UINT32_T.toLong()
        else -> UINT64_T.toLong()
    }
    val result = ArrayList<Byte>()
    result.add((((if (unsigned) MAJOR_UNSIGNED_INTEGER else MAJOR_NEGATIVE_INTEGER) shl 5) + additionalInformation).toByte())
    when (additionalInformation.toInt()) {
        UINT8_T -> result.add(convertedInput.toByte())
        UINT16_T -> result.addAll(shortToBytes(convertedInput))
        UINT32_T -> result.addAll(intToBytes(convertedInput))
        UINT64_T -> result.addAll(longToBytes(convertedInput))
    }
    return result
}


private fun longToBytes(value: Long): MutableList<Byte> {
    val result = ArrayList<Byte>()
    result.add((value ushr 56).toByte())
    result.add((value ushr 48).toByte())
    result.add((value ushr 40).toByte())
    result.add((value ushr 32).toByte())
    result.add((value ushr 24).toByte())
    result.add((value ushr 16).toByte())
    result.add((value ushr 8).toByte())
    result.add(value.toByte())
    return result
}

private fun intToBytes(value: Int): MutableList<Byte> {
    val result = ArrayList<Byte>()
    result.add((value ushr 24).toByte())
    result.add((value ushr 16).toByte())
    result.add((value ushr 8).toByte())
    result.add(value.toByte())
    return result
}

private fun intToBytes(value: Long): MutableList<Byte> = intToBytes(value.toInt())

private fun shortToBytes(value: Int): MutableList<Byte> {
    val result = ArrayList<Byte>()
    result.add((value ushr 8).toByte())
    result.add(value.toByte())
    return result
}

private fun shortToBytes(value: Long): MutableList<Byte> = shortToBytes(value.toInt())