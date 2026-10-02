package org.example.project.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import org.example.project.network.SupabaseManager
import org.example.project.viewmodel.SessionViewModel

@Composable
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF9C27B0),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onBackground = Color.White
        )
    ) {
        val viewModel = remember { SessionViewModel() }
        val navController = rememberNavController()

        // Jelenlegi felhasználó lekérdezése és a név kigenerálása az e-mailből
        val currentUser = SupabaseManager.supabaseClient.auth.currentUserOrNull()
        val autoUsername = currentUser?.email?.substringBefore("@") ?: "Ismeretlen"

        // Figyeljük a Supabase bejelentkezési állapotát
        val sessionStatus by SupabaseManager.supabaseClient.auth.sessionStatus.collectAsState(SessionStatus.Initializing)

        // Gatekeeper: Automatikus navigáció az Auth állapot alapján
        LaunchedEffect(sessionStatus) {
            when (sessionStatus) {
                is SessionStatus.Authenticated -> {
                    navController.navigate("config") {
                        popUpTo("login") { inclusive = true }
                    }
                }
                is SessionStatus.NotAuthenticated -> {
                    navController.navigate("login") {
                        // JAVÍTÁS: A configot töröljük, mert arról lépünk le!
                        popUpTo("config") { inclusive = true }
                    }
                }
                else -> { /* Initializing állapot: ide jöhetne egy betöltőképernyő */ }
            }
        }

        // Automatikus kitöltés a felhasználónévvel
        LaunchedEffect(autoUsername) {
            viewModel.updatePatientId(autoUsername)
        }

        // Figyeljük a mérés végét, hogy az eredményekre navigáljunk
        LaunchedEffect(viewModel.uiState.sessionCompleted) {
            if (viewModel.uiState.sessionCompleted && viewModel.sessionResult != null) {
                // Átnavigálunk az eredményekre, és a "vissza" gomb history-jából töröljük az execution képernyőt
                navController.navigate("results") {
                    popUpTo("execution") { inclusive = true }
                }
                // A resetSessionState() innen törölve lett, mert azt a ResultsScreen gombjai intézik!
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NavHost(navController = navController, startDestination = "login") {

                // 1. Bejelentkezési képernyő
                composable("login") {
                    LoginScreen()
                }

                composable("profile") {
                    // Biztosítjuk, hogy csak akkor nyíljon meg, ha van ID (biztonsági lépés)
                    val uid = currentUser?.id
                    if (uid != null) {
                        ProfileScreen(
                            userId = uid,
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        // Ha valamiért nincs ID, visszadobjuk a loginra
                        navController.navigate("login") { popUpTo("profile") {inclusive = true} }
                    }
                }

                // 2. Főképernyő (Config)
                composable("config") {
                    // EZ A SOR KELLET A HIBA JAVÍTÁSÁHOZ:
                    val scope = rememberCoroutineScope()

                    ConfigScreen(
                        patientId = viewModel.uiState.patientId,
                        onPatientIdChange = { /* Ezt most már nem engedjük módosítani a UI-ról */ },
                        athleteTasks = viewModel.standardRehabRoutine,
                        onAthleteTaskDurationChange = { index, duration ->
                            viewModel.updateStandardTaskDuration(index, duration)
                        },
                        onStartSession = {
                            viewModel.startSession(viewModel.standardRehabRoutine)
                            navController.navigate("execution")
                        },
                        isBluetoothEnabled = viewModel.uiState.isBluetoothEnabled,
                        bluetoothStatus = viewModel.uiState.bluetoothStatus,
                        onToggleBluetooth = { isChecked -> viewModel.toggleBluetooth(isChecked) },
                        onOpenSavedSessions = {
                            navController.navigate("saved_sessions")
                        },
                        onOpenProfile = {
                            navController.navigate("profile")
                        },
                        // ÚJ: Kijelentkezés hívása a coroutine scope-on belül
                        onLogout = {
                            println("AUTH_DEBUG: 1. A Kijelentkezés gombot megnyomták a ConfigScreen-en!")
                            scope.launch {
                                try {
                                    println("AUTH_DEBUG: 2. Coroutine elindult, AuthRepository hívása...")
                                    org.example.project.network.AuthRepository.logoutUser()

                                    println("AUTH_DEBUG: 3. Hálózati hívás kész, navigáció a loginra...")
                                    navController.navigate("login") {
                                        popUpTo("config") { inclusive = true }
                                    }
                                    println("AUTH_DEBUG: 4. Navigációs parancs kiadva, kilépés sikeres!")
                                } catch (e: Exception) {
                                    println("AUTH_DEBUG: X. KRITIKUS HIBA a gombnyomás során: ${e.message}")
                                    e.printStackTrace()
                                }
                            }
                        }
                    )
                }

                // 3. Mérés képernyő (Execution)
                composable("execution") {
                    ExecutionScreen(
                        viewModel = viewModel,
                        onStop = {
                            viewModel.stopSession()
                            if (viewModel.sessionResult == null) {
                                navController.popBackStack("config", inclusive = false)
                            }
                        }
                    )
                }

                // 4. Eredmények képernyő (Results)
                composable("results") {
                    viewModel.sessionResult?.let { result ->
                        ResultsScreen(
                            result = result,
                            onBackToMenu = {
                                viewModel.resetSessionState()
                                navController.popBackStack("config", inclusive = false)
                            },
                            onGoToSavedSessions = {
                                viewModel.resetSessionState()
                                navController.navigate("saved_sessions") {
                                    popUpTo("config")
                                }
                            }
                        )
                    }
                }

                // 5. Mentett mérések (Saved Sessions)
                composable("saved_sessions") {
                    SavedSessionsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSession = { fileName ->
                            viewModel.loadAndShowSavedSession(fileName)
                            navController.navigate("results")
                        }
                    )
                }
            }
        }
    }
}