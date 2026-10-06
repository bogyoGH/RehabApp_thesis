package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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

@Composable
fun ExecutionScreen(viewModel: SessionViewModel, onStop: () -> Unit) {

    if (viewModel.uiState.showViewModeDialog) {
        AlertDialog(
            onDismissRequest = { /* Ne lehessen bezárni döntés nélkül */ },
            title = { Text("Nézeti beállítások") },
            text = { Text("Mit szeretne látni a feladat végzése közben?") },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                            viewModel.confirmViewModeAndStart(ViewPreference.SELF)
                            viewModel.dismissViewModeDialog()
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) { Text("Csak önmagamat") }

                    Button(
                        onClick = {
                            viewModel.confirmViewModeAndStart(ViewPreference.VIDEO)
                            viewModel.dismissViewModeDialog()
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) { Text("Csak a feladatot") }

                    Button(
                        onClick = {
                            viewModel.confirmViewModeAndStart(ViewPreference.BOTH)
                            viewModel.dismissViewModeDialog()
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) { Text("Mindkettőt") }
                }
            }
        )
    }

    // FŐ KONTÉNER (Fix elrendezés, NINCS GÖRGETÉS)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. BLOKK: FELADAT CÍME (Felső sáv) ---
        Text(
            text = viewModel.uiState.currentTaskName,
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF64B5F6),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // --- 2. BLOKK: EGYYMÁS MELLETTI VIDEÓK (Rugalmas magasság - weight 1f) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Kitölti a maradék helyet a címsor és az alsó vezérlőpult között!
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                val pref = viewModel.uiState.viewPreference
                val isPreview = viewModel.uiState.isPreviewPhase

                // LOGIKA A BAL OLDALHOZ (VIDEÓ)
                if (pref == ViewPreference.BOTH || pref == ViewPreference.VIDEO || (pref == ViewPreference.SELF && isPreview)) {
                    Box(
                        modifier = Modifier
                            .weight(if (pref == ViewPreference.BOTH && !isPreview) 1f else 2f)
                            .fillMaxHeight()
                            .background(Color.Black)
                            .clipToBounds()
                    ) {
                        key(viewModel.uiState.currentVideoName) {
                            viewModel.uiState.currentVideoName?.let { videoName ->
                                VideoPlayer(videoName = videoName, isPaused = viewModel.uiState.isPaused, modifier = Modifier.fillMaxSize())
                            }
                        }
                        if (isPreview) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    text = "FIGYELJE A BEMUTATÓT!",
                                    color = Color(0xFFFFB74D),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    modifier = Modifier.padding(top = 24.dp)
                                )
                            }
                        }
                    }
                }

                // LOGIKA A JOBB OLDALHOZ (KAMERA)
                if (!isPreview && (pref == ViewPreference.BOTH || pref == ViewPreference.SELF)) {
                    Box(
                        modifier = Modifier
                            .weight(if (pref == ViewPreference.BOTH) 1f else 2f)
                            .fillMaxHeight()
                            .background(Color.Black)
                            .clipToBounds()
                    ) {
                        CameraPreview(modifier = Modifier.fillMaxSize())
                    }
                }
            }

            // SZÜNETELTETVE OVERLAY (A teljes videó/kamera felületet lefedi)
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
                        fontSize = 32.sp,
                        letterSpacing = 4.sp
                    )
                }
            }
        }

        // --- 3. BLOKK: VEZÉRLŐPULT (Alsó, fix kártya) ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Instrukciók
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = viewModel.uiState.currentInstruction,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (viewModel.uiState.currentFallback.isNotBlank()) {
                        Text(
                            text = viewModel.uiState.currentFallback,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFFB74D),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Időzítő és Stabilitás
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center // <--- EZT KELLETT ÁTÍRNI
                ) {
                    Text(
                        text = viewModel.uiState.timerText,
                        fontSize = if (viewModel.uiState.isPreviewPhase) 36.sp else 64.sp,
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
                            .height(12.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Color(0xFF4CAF50),
                        trackColor = Color.DarkGray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Gombok egymás mellett (50-50%)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { viewModel.requestStop() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("LEÁLLÍTÁS", fontWeight = FontWeight.Bold)
                    }

                    if (viewModel.uiState.isRunning) {
                        Button(
                            onClick = {
                                if (viewModel.uiState.isPaused) viewModel.resumeSession()
                                else viewModel.pauseSession()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.uiState.isPaused) Color(0xFF4CAF50) else Color(0xFFFFA000)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (viewModel.uiState.isPaused) "▶ FOLYTATÁS" else "⏸ SZÜNET",
                                fontWeight = FontWeight.Bold,
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

        if (viewModel.uiState.showSaveDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Mérés befejeződött") },
                text = { Text("Szeretné elmenteni és szinkronizálni az eredményeket, vagy elveti a mérést?") },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmSaveAndFinish() },
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
    }
}