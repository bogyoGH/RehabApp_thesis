package org.example.project.network

import androidx.compose.ui.text.font.FontWeight
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import org.example.project.data.Athlete
import org.example.project.network.SupabaseManager

object AuthRepository {


    suspend fun registerUser(
        emailStr: String,
        passwordStr: String,
        age: Int?,
        gender: String?,
        weight_kg: Double?,
        height_cm: Double?
    ): Result<Boolean> {
        return try {
            println("AUTH_DEBUG: 1. Regisztráció indítása az auth rendszerben: $emailStr")
            // 1. Hitelesítési fiók létrehozása (Nincs szükség a visszatérési érték eltárolására)
            SupabaseManager.supabaseClient.auth.signUpWith(Email) {
                email = emailStr
                password = passwordStr
            }

            // 2. Azonnali bejelentkezés, hogy meglegyen a hitelesített munkamenet (Session) az athletes tábla írásához
            println("AUTH_DEBUG: 2. Automatikus beléptetés a profil mentéséhez...")
            SupabaseManager.supabaseClient.auth.signInWith(Email) {
                email = emailStr
                password = passwordStr
            }

            // 3. Kinyerjük a frissen bejelentkezett felhasználó azonosítóját (UUID)
            val currentUser = SupabaseManager.supabaseClient.auth.currentUserOrNull()
            val userId = currentUser?.id
                ?: throw Exception("Nem sikerült lekérni a bejelentkezett felhasználó azonosítóját (UUID)!")

            // 4. Sportoló profil mentése az athletes táblába
            println("AUTH_DEBUG: 3. Biometrikus profil rögzítése az athletes táblában...")
            val newAthlete = Athlete(
                id = userId,
                age = age,
                gender = gender,
                height_cm = height_cm,
                weight_kg = weight_kg
            )

            SupabaseManager.supabaseClient.from("athletes").insert(newAthlete)
            println("AUTH_DEBUG: 4. Sportoló profil sikeresen mentve!")

            Result.success(true)
        } catch (e: Exception) {
            println("AUTH_DEBUG: Hiba a regisztrációs folyamatban: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    suspend fun loginUser(emailStr: String, passwordStr: String): Result<Boolean> {
        return try {
            SupabaseManager.supabaseClient.auth.signInWith(Email) {
                email = emailStr
                password = passwordStr
            }
            Result.success(true)
        } catch (e: Exception) {
            println("AUTH_DEBUG: Bejelentkezési hiba: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun logoutUser() {
        try {
            println("AUTH_DEBUG: -> SupabaseManager signOut() hívása indult...")
            SupabaseManager.supabaseClient.auth.signOut()
            println("AUTH_DEBUG: -> SupabaseManager signOut() sikeresen befejeződött.")
        } catch (e: Exception) {
            println("AUTH_DEBUG: -> HIBA a Supabase kijelentkezésnél: ${e.message}")
            e.printStackTrace()
        }
    }
}