package org.example.project.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
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

            // JAVÍTÁS: Ktor toByteArray() helyett a standard Kotlin encodeToByteArray() !
            bucket.upload(path = fileName, data = jsonString.encodeToByteArray()) {
                upsert = true
            }

            val dbEntity = SessionEntity(
                session_id = sessionId,
                patient_id = patientId,
                json_file_path = fileName
            )

            // JAVÍTÁS: postgrest["sessions"] helyett a from("sessions") használata
            SupabaseManager.supabaseClient.from("sessions").insert(dbEntity)

            FileStorage.markAsUploaded(fileName)

            Result.success(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            println("SUPABASE HIBA: ${e.message}")

            // 3. OFFLINE SZINKRONIZÁCIÓ BEÜTEMEZÉSE
            scheduleOfflineSync()

            Result.success("Offline mentve (Szinkronizálásra vár)")
        } catch (e: Throwable) {
            // JAVÍTÁS: Ez elkapja az olyan fatális hibákat is (pl. NoSuchMethodError), amik eddig némán kifagyasztották a mentést!
            e.printStackTrace()
            println("FATÁLIS HIBA A MENTÉSNÉL: ${e.message}")
            Result.failure(Exception("Kritikus hiba: ${e.message}"))
        }
    }

    suspend fun deleteSession(fileName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val sessionId = fileName.replace("_synced.json", "").replace(".json", "")

            // 1. Törlési (DELETE) parancs küldése a Supabase View-nak (a trigger fogja elkapni)
            SupabaseManager.supabaseClient.from("sessions").delete {
                filter {
                    eq("session_id", sessionId)
                }
            }

            // 2. CSAK AKKOR töröljük a telefonról, ha a hálózati kérés sikeres volt!
            FileStorage.deleteFile(fileName)
            Result.success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            // JAVÍTÁS: Innen kivettük a FileStorage törlést! Ha a felhős törlés elszáll,
            // a fájl megmarad a telefonon, hogy később újra meg lehessen próbálni.
            Result.failure(e)
        }
    }
    suspend fun loadLocalSession(fileName: String): Result<SessionExport> = withContext(Dispatchers.IO) {
        try {
            val jsonString = FileStorage.readText(fileName)

            if (jsonString != null) {
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