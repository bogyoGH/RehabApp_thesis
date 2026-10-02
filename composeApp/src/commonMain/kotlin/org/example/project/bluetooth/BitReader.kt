package org.example.project.bluetooth

/**
 * Utility class for reading arbitrary bits from a byte array.
 *
 * This class provides functionality for sequentially reading a specified number of bits
 * from a given byte array, maintaining an internal bit position to track progress. Each
 * bit read operation consumes the corresponding bits from the array.
 *
 * @constructor Initializes the BitReader with the given byte array.
 * @param bytes The byte array to read bits from.
 */
class BitReader(private val bytes: ByteArray) {
    private var bitPosition = 0

    fun readBits(numBits: Int): Int {
        var result = 0
        for (i in 0 until numBits) {
            val byteIndex = bitPosition / 8
            val bitIndex = 7 - (bitPosition % 8)
            val byteVal = bytes[byteIndex].toInt() and 0xFF
            val bit = (byteVal ushr bitIndex) and 1
            result = (result shl 1) or bit
            bitPosition++
        }
        return result
    }
}