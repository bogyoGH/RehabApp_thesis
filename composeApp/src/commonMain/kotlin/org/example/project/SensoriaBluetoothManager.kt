// SensoriaBluetoothManager.kt (commonMain)

package org.example.project



import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.flow.Flow



expect class SensoriaBluetoothManager(scope: CoroutineScope) {

    suspend fun connectAndListen(): Flow<SensoriaData>

}

