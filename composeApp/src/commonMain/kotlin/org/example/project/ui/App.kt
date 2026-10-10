package org.example.project.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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

        val currentUser = SupabaseManager.supabaseClient.auth.currentUserOrNull()
        val autoUsername = currentUser?.email?.substringBefore("@") ?: "Ismeretlen"

        val sessionStatus by SupabaseManager.supabaseClient.auth.sessionStatus.collectAsState(SessionStatus.Initializing)

        LaunchedEffect(sessionStatus) {
            when (sessionStatus) {
                is SessionStatus.Authenticated -> {
                    navController.navigate("config") { popUpTo("login") { inclusive = true } }
                }
                is SessionStatus.NotAuthenticated -> {
                    navController.navigate("login") { popUpTo("config") { inclusive = true } }
                }
                else -> { /* Initializing */ }
            }
        }

        LaunchedEffect(viewModel.uiState.sessionCompleted) {
            if (viewModel.uiState.sessionCompleted && viewModel.sessionResult != null) {
                navController.navigate("results") { popUpTo("execution") { inclusive = true } }
            }
        }

        LaunchedEffect(currentUser) {
            if (currentUser != null) {
                viewModel.updatePatientIds(currentUser.id, autoUsername)
            }
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val showBottomNav = currentRoute in listOf("config", "saved_sessions", "profile")

        Scaffold(
            bottomBar = {
                if (showBottomNav) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {

                        // ÚJ: Navigációs elemek egyedi színbeállításai
                        val navItemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White, // Fehér ikon az aktív fülön
                            selectedTextColor = MaterialTheme.colorScheme.primary, // Lila szöveg
                            indicatorColor = MaterialTheme.colorScheme.primary, // Lila háttér az aktív ikon mögött
                            unselectedIconColor = Color.Gray, // Halvány szürke ikon inaktív állapotban
                            unselectedTextColor = Color.Gray // Halvány szürke szöveg inaktív állapotban
                        )

                        NavigationBarItem(
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Mérés") },
                            label = {
                                Text(
                                    text = "Mérés",
                                    fontWeight = if (currentRoute == "config") androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            },
                            selected = currentRoute == "config",
                            colors = navItemColors, // Színek alkalmazása
                            onClick = {
                                navController.navigate("config") {
                                    popUpTo(navController.graph.findStartDestination().route ?: "login") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )

                        NavigationBarItem(
                            icon = { Icon(Icons.Default.List, contentDescription = "Előzmények") },
                            label = {
                                Text(
                                    text = "Előzmények",
                                    fontWeight = if (currentRoute == "saved_sessions") androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            },
                            selected = currentRoute == "saved_sessions",
                            colors = navItemColors, // Színek alkalmazása
                            onClick = {
                                navController.navigate("saved_sessions") {
                                    popUpTo(navController.graph.findStartDestination().route ?: "login") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )

                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                            label = {
                                Text(
                                    text = "Profil",
                                    fontWeight = if (currentRoute == "profile") androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            },
                            selected = currentRoute == "profile",
                            colors = navItemColors, // Színek alkalmazása
                            onClick = {
                                navController.navigate("profile") {
                                    popUpTo(navController.graph.findStartDestination().route ?: "login") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                NavHost(navController = navController, startDestination = "login") {

                    composable("login") { LoginScreen() }

                    composable("profile") {
                        val uid = currentUser?.id
                        if (uid != null) {
                            ProfileScreen(userId = uid, onBack = { navController.popBackStack() })
                        } else {
                            navController.navigate("login") { popUpTo("profile") { inclusive = true } }
                        }
                    }

                    composable("config") {
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(Unit) { viewModel.loadExercisesFromCloud() }

                        ConfigScreen(
                            patientId = viewModel.uiState.virtualPatientId,
                            onStartSingleTask = { exercise, duration ->
                                viewModel.startSingleTaskSession(exercise, duration)
                                navController.navigate("execution")
                            },
                            isBluetoothEnabled = viewModel.uiState.isBluetoothEnabled,
                            bluetoothStatus = viewModel.uiState.bluetoothStatus,
                            onToggleBluetooth = { isChecked -> viewModel.toggleBluetooth(isChecked) },
                            onLogout = {
                                scope.launch {
                                    org.example.project.network.AuthRepository.logoutUser()
                                    navController.navigate("login") { popUpTo("config") { inclusive = true } }
                                }
                            },
                            availableExercises = viewModel.availableExercises,
                            onCreateNewExercise = {
                                viewModel.setEditingExercise(null)
                                navController.navigate("exercise_form")
                            },
                            onEditExercise = { exerciseToEdit ->
                                viewModel.setEditingExercise(exerciseToEdit)
                                navController.navigate("exercise_form")
                            }
                        )
                    }
                    composable("exercise_form") {
                        val scope = rememberCoroutineScope()
                        var isSaving by remember { mutableStateOf(false) }

                        ExerciseFormScreen(
                            initialExercise = viewModel.exerciseToEdit,
                            isSaving = isSaving,
                            onSave = { savedExercise ->
                                scope.launch {
                                    isSaving = true
                                    val result = if (viewModel.exerciseToEdit != null) {
                                        org.example.project.network.ExerciseRepository.updateCustomExercise(savedExercise)
                                    } else {
                                        org.example.project.network.ExerciseRepository.saveCustomExercise(savedExercise)
                                    }

                                    isSaving = false
                                    if (result.isSuccess) {
                                        viewModel.loadExercisesFromCloud()
                                        navController.popBackStack()
                                    }
                                }
                            },
                            onDelete = { exerciseToDelete ->
                                scope.launch {
                                    isSaving = true
                                    // MÓDOSÍTÁS: A teljes objektumot adjuk át a törléshez
                                    val result = org.example.project.network.ExerciseRepository.deleteCustomExercise(exerciseToDelete)
                                    isSaving = false
                                    if (result.isSuccess) {
                                        viewModel.loadExercisesFromCloud() // Lista frissítése
                                        navController.popBackStack() // Vissza a főképernyőre
                                    }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )

                    }

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
                                    navController.navigate("saved_sessions") { popUpTo("config") }
                                }
                            )
                        }
                    }

                    composable("saved_sessions") {
                        SavedSessionsScreen(
                            patientId = viewModel.uiState.patientId, // ÚJ: Átadjuk az azonosítót
                            sessions = viewModel.mySessions,
                            deletedSessions = viewModel.deletedSessions,
                            onRefresh = {
                                viewModel.refreshSessions()
                                viewModel.refreshDeletedSessions()
                            },
                            onBack = { navController.popBackStack() },
                            onOpenSession = { sessionExport ->
                                viewModel.openCloudSession(sessionExport)
                                navController.navigate("results")
                            },
                            onRestore = { sessionId ->
                                viewModel.restoreCloudSession(sessionId)
                            }
                        )
                    }
                }
            }
        }
    }
}