package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.project.viewmodel.SessionViewModel
import org.example.project.viewmodel.ViewPreference
import kotlin.time.Clock

@Composable
fun ExecutionScreen(viewModel: SessionViewModel, onStop: () -> Unit) {

    // Lokális állapot annak tárolására, hogy a felhasználó engedélyezte-e a felvételt
    var isRecordingApproved by remember { mutableStateOf(false) }

    // Rögzítés engedélyezése dialógus
    if (viewModel.uiState.showViewModeDialog) {
        AlertDialog(
            onDismissRequest = { /* Ne lehessen bezárni döntés nélkül */ },
            title = { Text("Mérés rögzítése") },
            text = { Text("Szeretné rögzíteni a gyakorlatot a kamerával? A videó segít a rendszernek a felhőalapú vizuális egyensúly-elemzésben.") },
            confirmButton = {
                Button(
                    onClick = {
                        isRecordingApproved = true
                        viewModel.confirmViewModeAndStart(ViewPreference.SELF)
                        viewModel.dismissViewModeDialog()
                    }
                ) { Text("Igen, rögzítés") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isRecordingApproved = false
                        viewModel.confirmViewModeAndStart(ViewPreference.SELF)
                        viewModel.dismissViewModeDialog()
                    }
                ) { Text("Nem, csak mérés") }
            }
        )
    }

    // ÚJ KÜLSŐ BOX: Ez teszi lehetővé, hogy a töltőképernyő minden mást letakarjon a Z tengelyen
    Box(modifier = Modifier.fillMaxSize()) {

        // FŐ KONTÉNER (Korábban ez volt a legkülső elem)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- 1. BLOKK: FELADAT CÍME ---
            Text(
                text = viewModel.uiState.currentTaskName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF64B5F6),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // --- 2. BLOKK: KAMERAKÉP (Teljes rendelkezésre álló tér) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .clipToBounds()
            ) {
                val isPreview = viewModel.uiState.isPreviewPhase
                // JAVÍTÁS: remember-be tesszük, hogy ne változzon a másodperces visszaszámláláskor!
                val videoFileName = remember { "session_${viewModel.uiState.virtualPatientId}_${Clock.System.now().toEpochMilliseconds()}.mp4" }

                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    // Csak akkor rögzít, ha az engedély megvan ÉS fut a mérés
                    isRecording = viewModel.uiState.isRunning && !viewModel.uiState.isPaused && !isPreview && isRecordingApproved,
                    outputFileName = videoFileName,
                    onVideoSaved = { savedFilePath ->
                        println("Sikeres videó mentés: $savedFilePath")
                        viewModel.setRecordedVideoPath(savedFilePath)
                    }
                )

                // ELŐKÉSZÜLETI FÁZIS OVERLAY
                if (isPreview) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "KÉSZÜLJÖN FEL!",
                            color = Color(0xFFFFB74D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // SZÜNETELTETVE OVERLAY
                if (viewModel.uiState.isPaused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SZÜNETELTETVE",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 40.sp,
                            letterSpacing = 4.sp
                        )
                    }
                }
            }

            // --- 3. BLOKK: VEZÉRLŐPULT (Nagy méretű gombok és időzítő) ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {

                    // Időzítő
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = viewModel.uiState.timerText,
                            fontSize = if (viewModel.uiState.isPreviewPhase) 30.sp else 80.sp,
                            fontWeight = FontWeight.Black,
                            color = if (viewModel.uiState.isPreviewPhase) Color(0xFFFFB74D) else Color.White
                        )
                    }

                    // Progress Bar
                    if (!viewModel.uiState.isPreviewPhase) {
                        LinearProgressIndicator(
                            progress = viewModel.uiState.progressValue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(50)),
                            color = Color(0xFF4CAF50),
                            trackColor = Color.DarkGray
                        )
                    }

                    // Gombok egymás mellett (50-50%)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { viewModel.requestStop() },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("LEÁLLÍTÁS", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }

                        if (viewModel.uiState.isRunning) {
                            Button(
                                onClick = {
                                    if (viewModel.uiState.isPaused) viewModel.resumeSession()
                                    else viewModel.pauseSession()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (viewModel.uiState.isPaused) Color(0xFF4CAF50) else Color(0xFFFFA000)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (viewModel.uiState.isPaused) "▶ FOLYTATÁS" else "⏸ SZÜNET",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (viewModel.uiState.isPaused) Color.White else Color.Black
                                )
                            }
                        }
                    }
                }
            }

            LaunchedEffect(viewModel.uiState.sessionCompleted) {
                if (viewModel.uiState.sessionCompleted) {
                    delay(1000)
                    onStop()
                }
            }

            // --- MENTÉS DIALÓGUS ---
            if (viewModel.uiState.showSaveDialog) {
                // Ellenőrizzük, hogy ha kértünk videót, akkor megérkezett-e már az elérési út a kamerától
                val isVideoReady = !isRecordingApproved || viewModel.uiState.recordedVideoPath != null

                AlertDialog(
                    onDismissRequest = { },
                    title = { Text(if (isVideoReady) "Mérés befejeződött" else "Videó lezárása...") },
                    text = { Text(if (isVideoReady) "Szeretné elmenteni és szinkronizálni az eredményeket?" else "Kérjük, várjon 1-2 másodpercet a videófájl feldolgozására.") },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmSaveAndFinish() },
                            enabled = isVideoReady, // JAVÍTÁS: Csak akkor engedi menteni, ha a videó tényleg lemezen van
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("Mentés", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.discardSession() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Elvetés", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        } // Fő Column vége

        // --- TÖLTŐKÉPERNYŐ OVERLAY (Kikerült a Column-ból, így rátakar mindenre) ---
        if (viewModel.uiState.saveStatus.isNotEmpty() && !viewModel.uiState.sessionCompleted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Félig átlátszó sötét háttér, ami mindent eltakar
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF4CAF50), // Zöld töltés
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = viewModel.uiState.saveStatus,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A videó méretétől függően ez eltarthat pár másodpercig.\nKérjük, ne zárd be az alkalmazást!",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

    }
}