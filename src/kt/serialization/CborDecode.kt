package kt.serialization

import java.lang.Double.longBitsToDouble
import java.lang.Float.intBitsToFloat
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findParameterByName
import kotlin.reflect.full.primaryConstructor

inline fun <reified T : Any> decodeInternal(input: MutableList<Byte>): T? {
    if (input.isEmpty()) return null
    val kClass = T::class

    val elem = input.removeAt(0).toInt()
    val header = if (elem < 0) elem + 256 else elem
    val majorType = header.ushr(5)
    val additionalInfo = header.and(0b11111)
    return when {
        majorType == MAJOR_UNSIGNED_INTEGER -> parseNumber(input, true, additionalInfo, kClass)
        majorType == MAJOR_NEGATIVE_INTEGER -> parseNumber(input, false, additionalInfo, kClass)
        majorType == MAJOR_BYTE_STRING -> parseByteString(input, additionalInfo, kClass) as T
        majorType == MAJOR_TEXT_STRING -> parseTextString(input, additionalInfo, kClass)
//        majorType == MAJOR_ARRAY -> parseArray(input, additionalInfo, kClass)
        majorType == MAJOR_MAP -> parseMap(input, additionalInfo, kClass)
        majorType == MAJOR_OTHER && additionalInfo in listOf(FALSE, TRUE) -> parseBoolean(additionalInfo, kClass) as T
        majorType == MAJOR_OTHER && additionalInfo == NULL -> null
        majorType == MAJOR_OTHER && additionalInfo == SINGLE_PRECISION_FLOAT -> parseFloat(input, kClass) as T
        majorType == MAJOR_OTHER && additionalInfo == DOUBLE_PRECISION_FLOAT -> parseDouble(input, kClass) as T
        else -> throw IllegalArgumentException()
    }
}

fun <T : Any> parseMap(input: MutableList<Byte>, additionalInfo: Int, kClass: KClass<T>): T {
    val size = when (additionalInfo) {
        UINT8_T -> input.removeAt(0).toInt()
        UINT16_T -> input.removeAt(0).toInt().shl(8) + input.removeAt(0)
        UINT32_T -> bytesToInt(input)
        in (0..23) -> additionalInfo
        else -> throw IllegalArgumentException()
    }
    val filledProperties = HashMap<KParameter, Any?>()
    val primaryConstructor = kClass.primaryConstructor!!
    for (i in 0..size - 1) {
        val propertyName = decodeInternal<String>(input)
        val find = primaryConstructor.findParameterByName(propertyName!!)
        filledProperties.put(find!!, decodeInternal(input))
    }
    return primaryConstructor.callBy(filledProperties)
}

fun <T : Any> parseArray(input: MutableList<Byte>, additionalInfo: Int, kClass: KClass<Array<T>>): Array<T> {
//    if (kClass != Array<*>::class) {
//        throw IllegalArgumentException()
//    }
    val size = when (additionalInfo) {
        UINT8_T -> input.removeAt(0).toInt()
        UINT16_T -> input.removeAt(0).toInt().shl(8) + input.removeAt(0)
        UINT32_T -> bytesToInt(input)
        in (0..23) -> additionalInfo
        else -> throw IllegalArgumentException()
    }
    val result = Array<Any?>(size) { null }
    for (i in 0..size - 1) {
        result[i] = decodeInternal(input)
    }
    return result as Array<T>
}

fun <T : Any> parseFloat(input: MutableList<Byte>, kClass: KClass<T>): Float {
//    if (kClass != Float::class) {
//        throw IllegalArgumentException()
//    }
    return intBitsToFloat(bytesToInt(input))

}

fun <T : Any> parseDouble(input: MutableList<Byte>, kClass: KClass<T>): Double {
//    if (kClass != Double::class) {
//        throw IllegalArgumentException()
//    }
    return longBitsToDouble(bytesToLong(input))
}

fun bytesToInt(input: MutableList<Byte>): Int {
    return normalizeByte(input.removeAt(0).toInt()).shl(24) + normalizeByte(input.removeAt(0).toInt()).shl(16) +
            normalizeByte(input.removeAt(0).toInt()).shl(8) + normalizeByte(input.removeAt(0).toInt())
}

fun bytesToLong(input: MutableList<Byte>): Long {
    return normalizeByte(input.removeAt(0).toLong()).shl(56) + normalizeByte(input.removeAt(0).toLong()).shl(48) +
            normalizeByte(input.removeAt(0).toLong()).shl(40) + normalizeByte(input.removeAt(0).toLong()).shl(32) +
            normalizeByte(input.removeAt(0).toLong()).shl(24) + normalizeByte(input.removeAt(0).toLong()).shl(16) +
            normalizeByte(input.removeAt(0).toLong()).shl(8) + normalizeByte(input.removeAt(0).toLong())
}

fun <T : Any> parseTextString(input: MutableList<Byte>, additionalInfo: Int, kClass: KClass<T>): T {
//    if (kClass != String::class || kClass != Enum::class) {
//        throw IllegalArgumentException()
//    }
    val size = when (additionalInfo) {
        UINT8_T -> normalizeByte(input.removeAt(0).toInt())
        UINT16_T -> normalizeByte(input.removeAt(0).toInt()).shl(8) + normalizeByte(input.removeAt(0).toInt())
        UINT32_T -> bytesToInt(input)
        in (0..23) -> additionalInfo
        else -> throw IllegalArgumentException()
    }
    val result = StringBuilder()
    for (i in 0..size - 1) {
        result.append(normalizeByte(input.removeAt(0).toInt()).toChar())
    }
    return kClass.java.enumConstants?.find { (it as Enum<*>).name == result.toString() } ?: result.toString() as T

}

fun <T : Any> parseByteString(input: MutableList<Byte>, additionalInfo: Int, kClass: KClass<T>): ByteArray {
//    if (kClass != ByteArray::class) {
//        throw IllegalArgumentException()
//    }
    val size = when (additionalInfo) {
        UINT8_T -> input.removeAt(0).toInt()
        UINT16_T -> input.removeAt(0).toInt().shl(8) + input.removeAt(0)
        UINT32_T -> bytesToInt(input)
        in (0..23) -> additionalInfo
        else -> throw IllegalArgumentException()
    }
    val result = ByteArray(size)
    for (i in 0..size - 1) {
        result[i] = input.removeAt(0)
    }
    return result
}

fun <T : Any> parseBoolean(input: Int, kClass: KClass<T>): Boolean {
//    if (kClass != Boolean::class) {
//        throw IllegalArgumentException()
//    }
    return input == TRUE
}

fun <T : Any> parseNumber(input: MutableList<Byte>, unsigned: Boolean, additionalInfo: Int, kClass: KClass<T>): T {
    val number = when (additionalInfo) {
        UINT8_T -> normalizeByte(input.removeAt(0).toLong())
        UINT16_T -> normalizeByte(input.removeAt(0).toLong()).shl(8) + normalizeByte(input.removeAt(0).toLong())
        UINT32_T -> bytesToInt(input).toLong()
        UINT64_T -> bytesToLong(input)
        in (0..23) -> additionalInfo.toLong()
        else -> throw IllegalArgumentException()
    }
    val convertedNumber = if (unsigned) number else number.inv()
    when (kClass.simpleName) {
        "Byte" -> if (convertedNumber in (Byte.MIN_VALUE..Byte.MAX_VALUE)) return convertedNumber.toByte() as T
        "Short" -> if (convertedNumber in (Short.MIN_VALUE..Short.MAX_VALUE)) return convertedNumber.toShort() as T
        "Int" -> if (convertedNumber in (Int.MIN_VALUE..Int.MAX_VALUE)) return convertedNumber.toInt() as T
        "Long" -> if (convertedNumber in (Long.MIN_VALUE..Long.MAX_VALUE)) return convertedNumber as T
    }
    return convertedNumber as T
    //throw IllegalArgumentException()
}

fun normalizeByte(b: Int): Int {
    return if (b < 0) b + 256 else b
}

fun normalizeByte(b: Long): Long {
    return if (b < 0) b + 256 else b
}