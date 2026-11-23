package org.example.project.network


import com.benasher44.uuid.uuid4
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.project.data.Exercise
import org.example.project.data.FileStorage

object ExerciseRepository {
    private const val FILE_NAME = "exercises_cache.json"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getExercises(): Result<List<Exercise>> = withContext(Dispatchers.IO) {
        try {
            // 1. Próbáljuk letölteni a felhőből (Ez csak a globális és a saját custom feladatokat hozza le)
            val exercises = SupabaseManager.supabaseClient
                .from("exercises")
                .select()
                .decodeList<Exercise>()

            // 2. Sikeres letöltés esetén elmentjük helyi JSON fájlba (Cache)
            FileStorage.saveText(FILE_NAME, json.encodeToString(exercises))

            Result.success(exercises)
        } catch (e: Exception) {
            // 3. Ha nincs internet, megpróbáljuk betölteni a helyi fájlt
            println("Hálózat hiba, próbálkozás helyi cache-ből...")
            val localContent = FileStorage.readText(FILE_NAME)
            if (localContent != null) {
                val localExercises = json.decodeFromString<List<Exercise>>(localContent)
                Result.success(localExercises)
            } else {
                Result.failure(Exception("Nincs internet és nincs helyileg mentett feladat!"))
            }
        }
    }

    suspend fun saveCustomExercise(exercise: Exercise): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentUser = SupabaseManager.supabaseClient.auth.currentUserOrNull()

            if (currentUser == null) {
                return@withContext Result.failure(Exception("Nincs bejelentkezett felhasználó!"))
            }

            // Biztosítjuk, hogy a created_by a jelenlegi felhasználó (patient_id) legyen,
            // és a custom exercise ne legyen globális
            val newExercise = exercise.copy(
                created_by = currentUser.id,
                is_global = false
            )

            // Beszúrás az adatbázisba
            SupabaseManager.supabaseClient
                .from("exercises")
                .insert(newExercise)

            Result.success(true)
        } catch (e: Exception) {
            println("Hiba a gyakorlat mentésekor: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 3. Meglévő saját gyakorlat frissítése
    suspend fun updateCustomExercise(exercise: Exercise): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            SupabaseManager.supabaseClient
                .from("exercises")
                .update(exercise) {
                    filter { eq("id", exercise.id) }
                }
            Result.success(true)
        } catch (e: Exception) {
            println("Hiba a gyakorlat módosításakor: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // Videó feltöltése a Supabase Storage-ba
    suspend fun uploadVideo(videoBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {

            val uuid = uuid4()
            val fileName = "video_$uuid.mp4"
            val bucket = SupabaseManager.supabaseClient.storage.from("exercise-videos")

            // Fájl feltöltése a felhőbe
            bucket.upload(fileName, videoBytes)

            // Nyilvános letöltési URL lekérése
            val publicUrl = bucket.publicUrl(fileName)
            Result.success(publicUrl)
        } catch (e: Exception) {
            println("Hiba a videó feltöltésekor: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    //Saját gyakorlat törlése
    suspend fun deleteCustomExercise(exercise: Exercise): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            // 1. Ha van hozzá feltöltött videó, töröljük a Storage bucketből
            val videoUrl = exercise.video_url
            if (!videoUrl.isNullOrBlank() && videoUrl.contains("exercise-videos")) {
                try {
                    // Kinyerjük a pontos fájlnevet a nyilvános URL végéről
                    val fileName = videoUrl.substringAfterLast("/")
                    if (fileName.isNotBlank()) {
                        val bucket = SupabaseManager.supabaseClient.storage.from("exercise-videos")
                        bucket.delete(fileName)
                        println("Sikeresen törölve a Storage-ből: $fileName")
                    }
                } catch (e: Exception) {
                    println("Figyelmeztetés: Nem sikerült törölni a videót a bucketből: ${e.message}")
                    // Nem dobunk hibát, hogy a sérült/hiányzó videó miatt ne ragadjon be a rekord az adatbázisba
                }
            }

            // 2. Magának a gyakorlatnak a törlése az adatbázisból (Hard delete)
            SupabaseManager.supabaseClient
                .from("exercises")
                .delete {
                    filter { eq("id", exercise.id) }
                }
            Result.success(true)
        } catch (e: Exception) {
            println("Hiba a gyakorlat törlésekor: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}