package org.example.project.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.example.project.network.SessionRepository
import org.example.project.data.FileStorage

class SessionSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // Fájlnevek listáját kapjuk (pl. ["session_PT-001...json"])
            val pendingFiles = FileStorage.getPendingUploads()

            if (pendingFiles.isEmpty()) {
                return Result.success()
            }

            for (fileName in pendingFiles) {
                // Betöltjük a fájlt a neve alapján (most már MeasurementDocument-et ad vissza)
                val sessionResult = SessionRepository.loadLocalSession(fileName)

                sessionResult.onSuccess { document ->
                    // A Repository elintézi a felhős beszúrást az új JSONB oszlopba
                    val uploadResult = SessionRepository.saveSession(
                        document.measurement_id,
                        document.athlete_id ?: "", // Ha valamiért null lenne, üres stringet adunk át
                        document
                    )

                    uploadResult.onSuccess { message ->
                        if (message.contains("Offline mentve")) {
                            // A hálózat továbbra sem jó, később újra kell próbálni
                            return Result.retry()
                        }
                        // A FileStorage.markAsUploaded(fileName) hívást innen kivettük,
                        // mert a SessionRepository.saveSession sikeres ága már elvégezte!
                    }.onFailure {
                        // Ha valamilyen kritikus hiba miatt failure-t dob a repo
                        return Result.retry()
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Bármilyen egyéb összeomlás (pl. timeout) esetén próbálja újra később
            Result.retry()
        }
    }
}