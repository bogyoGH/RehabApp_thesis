
package org.example.project.bluetooth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.example.project.data.SensorSample

expect class SensoriaBluetoothManager(scope: CoroutineScope) {
    // Visszatérési érték: Melyik oldalról (SockSide) milyen adat (SensorSample) érkezett
    suspend fun connectAndListen(onStatusChange: (String) -> Unit): Flow<Pair<SockSide, SensorSample>>
    suspend fun disconnect()
}