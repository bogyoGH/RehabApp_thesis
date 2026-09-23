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
                // Betöltjük a fájlt a neve alapján a meglévő függvényeddel
                val sessionResult = SessionRepository.loadLocalSession(fileName)

                sessionResult.onSuccess { sessionExport ->
                    // Próbáljuk feltölteni a Supabase-be
                    val uploadResult = SessionRepository.saveSession(
                        sessionExport.sessionInfo.sessionId,
                        sessionExport.sessionInfo.patientId,
                        sessionExport
                    )

                    uploadResult.onSuccess {
                        // Siker esetén azonnal "_synced.json" lesz belőle
                        FileStorage.markAsUploaded(fileName)
                    }.onFailure {
                        // Ha a hálózat közben megint elment, újra ütemezzük
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