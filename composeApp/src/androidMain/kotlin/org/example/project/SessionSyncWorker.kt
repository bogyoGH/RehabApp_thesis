package org.example.project.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.example.project.data.SessionRepository
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
                // Betöltjük a fájlt a neve alapján
                val sessionResult = SessionRepository.loadLocalSession(fileName)

                sessionResult.onSuccess { sessionExport ->
                    // Próbáljuk feltölteni a Supabase-be
                    val uploadResult = SessionRepository.saveSession(
                        sessionExport.sessionInfo.sessionId,
                        sessionExport.sessionInfo.patientId,
                        sessionExport
                    )

                    uploadResult.onSuccess { message ->
                        // JAVÍTÁS: Ellenőrizzük, hogy a Repository valóban feltöltötte-e, vagy csak visszaadta az offline fallback üzenetet!
                        if (message.contains("Offline mentve")) {
                            // A hálózat továbbra sem jó, később újra kell próbálni a szinkronizálást
                            return Result.retry()
                        } else {
                            // A felhős mentés tényleg sikeres volt, jöhet a _synced utótag
                            FileStorage.markAsUploaded(fileName)
                        }
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