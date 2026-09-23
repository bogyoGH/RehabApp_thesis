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
import org.example.project.scheduleOfflineSync

object SessionRepository {
    suspend fun saveSession(
        sessionId: String,
        patientId: String,
        exportData: SessionExport
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonString = Json { prettyPrint = true }.encodeToString(exportData)
            val fileName = "$sessionId.json"

            // 1. Helyi mentés (ez hálózat nélkül is lefut)
            FileStorage.saveText(fileName, jsonString)

            // 2. Felhős mentés
            val bucket = SupabaseManager.supabaseClient.storage.from("raw_sessions")
            bucket.upload(path = fileName, data = jsonString.toByteArray()) {
                upsert = true
            }

            val dbEntity = SessionEntity(
                session_id = sessionId,
                patient_id = patientId,
                json_file_path = fileName
            )
            SupabaseManager.supabaseClient.postgrest["sessions"].insert(dbEntity)

            Result.success(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            println("SUPABASE HIBA: ${e.message}")

            // 3. OFFLINE SZINKRONIZÁCIÓ BEÜTEMEZÉSE
            scheduleOfflineSync()

            // Mivel a helyi mentés (FileStorage) az 1. pontban már sikeresen lefutott,
            // a folyamatot nem teljes kudarcként (failure) kezeljük, hanem jelezzük a felületnek,
            // hogy az adatok biztonságban vannak a telefonon.
            Result.success("Offline mentve (Szinkronizálásra vár)")
        }
    }

    suspend fun deleteSession(fileName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // A fájlnévből (pl. "session_123_synced.json" vagy "session_123.json") kinyerjük a tiszta ID-t
            val sessionId = fileName.replace("_synced.json", "").replace(".json", "")
            val storageFileName = "$sessionId.json"

            // 1. Törlés a Supabase Storage-ből (Bucket)
            val bucket = SupabaseManager.supabaseClient.storage.from("raw_sessions")
            bucket.delete(listOf(storageFileName))

            SupabaseManager.supabaseClient.postgrest["sessions"].delete {
                filter {
                    eq("session_id", sessionId) // Ha a DB oszlop neve "session_id", így marad
                }
            }

            // 3. Törlés a helyi tárhelyről
            FileStorage.deleteFile(fileName)

            Result.success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            // Ha nincs net, a helyi fájlt akkor is töröljük, hogy a felületről eltűnjön
            FileStorage.deleteFile(fileName)
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


