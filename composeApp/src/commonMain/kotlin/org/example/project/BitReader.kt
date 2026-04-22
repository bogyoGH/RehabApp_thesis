package org.example.project

class BitReader(private val bytes: ByteArray) {
    private var bitPosition = 0

    fun readBits(numBits: Int): Int {
        var result = 0
        for (i in 0 until numBits) {
            val byteIndex = bitPosition / 8
            val bitIndex = 7 - (bitPosition % 8)
            // A byte-ot előjel nélküli 8 bites értékként kezeljük
            val byteVal = bytes[byteIndex].toInt() and 0xFF
            val bit = (byteVal ushr bitIndex) and 1
            result = (result shl 1) or bit
            bitPosition++
        }
        return result
    }
}