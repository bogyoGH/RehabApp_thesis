package org.example.project.network

import io.github.jan.supabase.postgrest.from
import org.example.project.data.Athlete

object ProfileRepository {

    // Profil lekérdezése az ID alapján
    suspend fun getProfile(userId: String): Result<Athlete?> {
        return try {
            val athlete = SupabaseManager.supabaseClient
                .from("athletes")
                .select {
                    filter { eq("id", userId) }
                }.decodeSingleOrNull<Athlete>()
            Result.success(athlete)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // Profil frissítése (upsert: ha létezik, frissíti, ha nem, létrehozza)
    suspend fun updateProfile(athlete: Athlete): Result<Boolean> {
        return try {
            SupabaseManager.supabaseClient
                .from("athletes")
                .upsert(athlete)
            Result.success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}