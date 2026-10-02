package org.example.project.bluetooth

import org.example.project.data.SensoriaData

object SensoriaDecoder {

    fun decodePacket(byteArray: ByteArray): SensoriaData? {
        if (byteArray.size < 12) return null

        return try {
            val reader = BitReader(byteArray)

            reader.readBits(4)  // msg_type
            reader.readBits(4)  // context
            reader.readBits(2)  // gs
            reader.readBits(2)  // s
            reader.readBits(2)  // g
            reader.readBits(2)  // hz
            reader.readBits(24) // tick

            val ch0 = reader.readBits(10)
            val ch1 = reader.readBits(10)
            val ch2 = reader.readBits(10)

            reader.readBits(10) // mag_x
            reader.readBits(10) // mag_y
            reader.readBits(10) // mag_z

            val accelX = reader.readBits(10)
            val accelY = reader.readBits(10)
            val accelZ = reader.readBits(10)

            SensoriaData(
                ch0 = ch0,
                ch1 = ch1,
                ch2 = ch2,
                accelX = accelX,
                accelY = accelY,
                accelZ = accelZ
            )
        } catch (e: Exception) {
            null
        }
    }
}