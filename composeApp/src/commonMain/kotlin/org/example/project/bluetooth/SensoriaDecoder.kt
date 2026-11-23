package org.example.project.bluetooth

import org.example.project.data.*

object SensoriaDecoder {

    fun decodePacket(byteArray: ByteArray): SensorSample? {
        // A teljes keret pontosan 20 bájt (160 bit)
        if (byteArray.size < 20) return null

        return try {
            val reader = BitReader(byteArray)

            // Fejléc metaadatok
            val msgType = reader.readBits(4)
            val context = reader.readBits(4)
            val gs = reader.readBits(2)
            val s = reader.readBits(2)
            val g = reader.readBits(2)
            val hz = reader.readBits(2)
            val tick = reader.readBits(24)

            // Nyomásszenzorok
            val ch0 = reader.readBits(10)
            val ch1 = reader.readBits(10)
            val ch2 = reader.readBits(10)

            // Magnetometer
            val magX = reader.readBits(10)
            val magY = reader.readBits(10)
            val magZ = reader.readBits(10)

            // Gyorsulásmérő
            val accelX = reader.readBits(10)
            val accelY = reader.readBits(10)
            val accelZ = reader.readBits(10)

            // Giroszkóp
            val gyroX = reader.readBits(10)
            val gyroY = reader.readBits(10)
            val gyroZ = reader.readBits(10)

            SensorSample(
                t = tick,
                host_time_ms = null, // Ezt majd a Bluetooth réteg vagy a ViewModel tölti ki
                pressure = Pressure(
                    heel = ch0.toDouble(),
                    met_1 = ch1.toDouble(),
                    met_5 = ch2.toDouble()
                ),
                imu = IMU(
                    ax = accelX.toDouble(),
                    ay = accelY.toDouble(),
                    az = accelZ.toDouble(),
                    gx = gyroX.toDouble(),
                    gy = gyroY.toDouble(),
                    gz = gyroZ.toDouble()
                ),
                magnetometer = Magnetometer(
                    mx = magX.toDouble(),
                    my = magY.toDouble(),
                    mz = magZ.toDouble()
                ),
                msgType = msgType,
                context = context,
                gs = gs,
                s = s,
                g = g,
                hz = hz
            )
        } catch (e: Exception) {
            println("Hiba a Sensoria csomag dekódolásakor: ${e.message}")
            null
        }
    }
}