package org.example.project.data

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.project.dto.SessionExport
import org.example.project.network.SupabaseManager

object SessionRepository {
    suspend fun saveSession(
        sessionId: String,
        patientId: String,
        exportData: SessionExport
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonString = Json { prettyPrint = true }.encodeToString(exportData)
            val fileName = "$sessionId.json"

            // 1. Helyi mentés
            FileStorage.saveText(fileName, jsonString)

            // 2. Felhős mentés
            val bucket = SupabaseManager.supabaseClient.storage.from("raw_sessions")
            bucket.upload(path = fileName, data = jsonString.toByteArray()) {
                upsert = true
            }

            val dbEntity = SessionEntity(
                sessionId = sessionId,
                patientId = patientId,
                json_file_path = fileName
            )
            SupabaseManager.supabaseClient.postgrest["sessions"].insert(dbEntity)

            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadLocalSession(fileName: String): Result<SessionExport> = withContext(Dispatchers.IO) {
        try {
            // 1. Fájl beolvasása a helyi tárhelyről (FileStorage)
            val jsonString = FileStorage.readText(fileName)

            if (jsonString != null) {
                // 2. Deszerializálás JSON-ből Kotlin objektummá
                val sessionExport = Json { ignoreUnknownKeys = true }.decodeFromString<SessionExport>(jsonString)
                Result.success(sessionExport)
            } else {
                Result.failure(Exception("A fájl nem található a helyi tárhelyen."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

suspend fun loadSession(fileName: String): Result<SessionExport> = withContext(Dispatchers.IO) {
    try {
        val bucket = SupabaseManager.supabaseClient.storage.from("raw_sessions")
        val bytes = bucket.downloadAuthenticated(fileName)
        val jsonString = bytes.decodeToString()

        val sessionExport = Json { ignoreUnknownKeys = true }.decodeFromString<SessionExport>(jsonString)
        Result.success(sessionExport)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
