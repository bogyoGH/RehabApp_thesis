package org.example.project

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.example.project.bluetooth.SockSide
import org.example.project.data.IMU
import org.example.project.data.Magnetometer
import org.example.project.data.Pressure
import org.example.project.data.SensorSample

object MockDataGenerator {
    private var tick = 0

    fun generatePacket(taskName: String = "", side: SockSide = SockSide.LEFT): SensorSample {
        tick++
        // ... (A korábbi logika alapján számolhatsz ch0, ch1, ch2, accel értékeket)
        // Itt egy egyszerűsített visszatérés a példa kedvéért:

        return SensorSample(
            t = tick,
            host_time_ms = (tick * 50).toDouble(), // 50ms-es frissítés szimulálása
            pressure = Pressure(
                heel = (500 + Random.nextInt(-50, 50)).toDouble(),
                met_1 = (300 + Random.nextInt(-50, 50)).toDouble(),
                met_5 = (800 + Random.nextInt(-50, 50)).toDouble()
            ),
            imu = IMU(
                ax = (512 + Random.nextInt(-20, 20)).toDouble(),
                ay = (512 + Random.nextInt(-20, 20)).toDouble(),
                az = (512 + Random.nextInt(-20, 20)).toDouble(),
                gx = 0.0, gy = 0.0, gz = 0.0 // Szimulált giroszkóp
            ),
            magnetometer = Magnetometer(mx = 0.0, my = 0.0, mz = 0.0),
            msgType = 1, context = 0, gs = 0, s = 0, g = 0, hz = 0
        )
    }
}