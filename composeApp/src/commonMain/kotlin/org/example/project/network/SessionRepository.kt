package org.example.project.network

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.example.project.analysis.SockAnalysisResult
import org.example.project.data.FileStorage
import org.example.project.data.SessionEntity
import org.example.project.dto.MeasurementDocument
import org.example.project.scheduleOfflineSync

object SessionRepository {

    @Serializable
    private data class RestoreSessionRequest(val p_session_id: String)

    suspend fun saveSession(
        sessionId: String,
        patientId: String,
        exportData: MeasurementDocument
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Helyi mentés  (az offline szinkronizációhoz)
            val jsonString = Json { prettyPrint = true }.encodeToString(exportData)
            val fileName = "$sessionId.json"
            FileStorage.saveText(fileName, jsonString)

            // 2. Felhős mentés
            val sessionJsonElement =
                Json.Default.encodeToJsonElement(MeasurementDocument.serializer(), exportData).jsonObject

            val intialStatus = if (exportData.video?.file_name.isNullOrBlank()) "sock_only" else "pending"
            // Entitás létrehozása az adatbázis számára
            val dbEntity = SessionEntity(
                session_id = sessionId,
                patient_id = patientId,
                session_data = sessionJsonElement,
                video_url = exportData.video?.file_name, // Módosítva a MeasurementDocument alapján
                analysis_status = intialStatus
            )

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
            e.printStackTrace()
            println("FATÁLIS HIBA A MENTÉSNÉL: ${e.message}")
            Result.failure(Exception("Kritikus hiba: ${e.message}"))
        }
    }

    suspend fun deleteSession(sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            SupabaseManager.supabaseClient.from("sessions").delete {
                filter { eq("session_id", sessionId) }
            }
            // 2. Törlés a felhőbeli tárolóból (Bucket)
            try {
                SupabaseManager.supabaseClient.storage.from("measurement-videos").delete(listOf("$sessionId.mp4"))
            } catch (e: Exception) {
                println("Hiba a videó törlésekor a bucketből: ${e.message}")
            }
            FileStorage.deleteFile("$sessionId.json")
            FileStorage.deleteFile("${sessionId}_synced.json")
            Result.success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun loadLocalSession(fileName: String): Result<MeasurementDocument> = withContext(Dispatchers.IO) {
        try {
            val jsonString = FileStorage.readText(fileName)

            if (jsonString != null) {
                val sessionExport = Json { ignoreUnknownKeys = true }.decodeFromString<MeasurementDocument>(jsonString)
                Result.success(sessionExport)
            } else {
                Result.failure(Exception("A fájl nem található a helyi tárhelyen."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMySessions(): Result<List<MeasurementDocument>> = withContext(Dispatchers.IO) {
        try {
            println("ZOKNI_DEBUG: getMySessions() lekérdezés indítása...")
            val sessionEntities = SupabaseManager.supabaseClient
                .from("sessions")
                .select()
                .decodeList<SessionEntity>()

            println("ZOKNI_DEBUG: Talált sessionök száma: ${sessionEntities.size}")
            val sessionExports = sessionEntities.map { entity ->
                val document = Json { ignoreUnknownKeys = true }.decodeFromJsonElement(
                    MeasurementDocument.serializer(),
                    entity.session_data
                )

                // Felülírjuk a JSON-ben lévő azonosítót az adatbázis valós session_id-jával
                document.copy(measurement_id = entity.session_id)
            }

            Result.success(sessionExports)
        } catch (e: Exception) {
            println("ZOKNI_DEBUG: Hiba a getMySessions lekérdezéskor: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getDeletedSessions(): Result<List<MeasurementDocument>> = withContext(Dispatchers.IO) {
        try {
            val sessionEntities = SupabaseManager.supabaseClient
                .from("deleted_sessions")
                .select()
                .decodeList<SessionEntity>()

            val sessionExports = sessionEntities.map { entity ->

                val document = Json { ignoreUnknownKeys = true }.decodeFromJsonElement(
                    MeasurementDocument.serializer(),
                    entity.session_data
                )


                document.copy(measurement_id = entity.session_id)
            }
            Result.success(sessionExports)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    suspend fun restoreSession(sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            SupabaseManager.supabaseClient.postgrest.rpc(
                "restore_session",
                RestoreSessionRequest(sessionId)
            )
            Result.success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun uploadMeasurementVideo(videoBytes: ByteArray, sessionId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val fileName = "$sessionId.mp4"
                val bucket = SupabaseManager.supabaseClient.storage.from("measurement-videos")

                bucket.upload(fileName, videoBytes)
                //TODO ez a lépés security rés, elég lenne csak a session_id-t és a szerver oldalon kéne tudni az urlt
                val publicUrl = bucket.publicUrl(fileName)
                Result.success(publicUrl)
            } catch (e: Exception) {
                println("Hiba a mérési videó feltöltésekor: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }


    @Serializable
    private data class SockLookupRequest(val p_session_id: String)

    suspend fun getSockAnalysis(
        sessionId: String
    ): Result<SockAnalysisResult?> = withContext(Dispatchers.IO) {
        try {
            println("ZOKNI DEBUG - Keresett sessionId: [$sessionId]")

            val authUserId = SupabaseManager.supabaseClient
                .auth.currentUserOrNull()?.id

            println("ZOKNI DEBUG - Bejelentkezett user ID: $authUserId")

            val response = SupabaseManager.supabaseClient.postgrest.rpc(
                "get_my_sock_analysis",
                SockLookupRequest(sessionId)
            )

            println("ZOKNI DEBUG - RPC válasz: ${response.data.take(500)}")

            val value =
                if (response.data.trim() == "null") {
                    null
                } else {
                    Json { ignoreUnknownKeys = true }
                        .decodeFromString<SockAnalysisResult>(response.data)
                }

            Result.success(value)

        } catch (e: Exception) {
            println("ZOKNI DEBUG - HIBA: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getBiomechanicsResult(sessionId: String): Result<org.example.project.data.BiomechanicsResult?> =
        withContext(Dispatchers.IO) {
            try {
                println("ZOKNI_DEBUG: Biomechanikai eredmény lekérése ehhez: $sessionId")
                val resultList = SupabaseManager.supabaseClient
                    .from("biomechanics_results")
                    .select {
                        filter { eq("session_id", sessionId) }
                    }.decodeList<org.example.project.data.BiomechanicsResult>()

                println("ZOKNI_DEBUG: Lekérdezés sikeres. Talált eredmények száma: ${resultList.size}")
                Result.success(resultList.firstOrNull())
            } catch (e: Exception) {
                println("ZOKNI_DEBUG: Hiba a biomechanikai adatok lekérésekor ($sessionId): ${e.message}")
                Result.failure(e)
            }
        }
}
