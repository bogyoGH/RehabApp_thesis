// SensoriaBluetoothManager.kt (commonMain)

package org.example.project.bluetooth



import kotlinx.coroutines.CoroutineScope
import org.example.project.data.SensoriaData
import kotlinx.coroutines.flow.Flow



expect class SensoriaBluetoothManager(scope: CoroutineScope) {

    suspend fun connectAndListen(onStatusChange: (String) -> Unit): Flow<SensoriaData>

    suspend fun disconnect()

}

