// SensoriaBluetoothManager.kt (commonMain)

package org.example.project



import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.flow.Flow



expect class SensoriaBluetoothManager(scope: CoroutineScope) {

    suspend fun connectAndListen(onStatusChange: (String) -> Unit): Flow<SensoriaData>

    suspend fun disconnect()

}

