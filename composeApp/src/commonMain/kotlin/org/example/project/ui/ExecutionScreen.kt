package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.project.viewmodel.SessionViewModel
import org.example.project.viewmodel.ViewPreference

@Composable
fun ExecutionScreen(viewModel: SessionViewModel, onStop: () -> Unit) {
    val scrollState = rememberScrollState()

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

    // FŐ KONTÉNER (Minden ez alatt van, és együtt scrollozható)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. BLOKK: FELADAT CÍME ---
        Text(
            text = viewModel.uiState.currentTaskName,
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF64B5F6),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
        )

        // --- 2. BLOKK: EGYYMÁS MELLETTI VIDEÓK (50-50%) ---
        // Visszatettem a Row-t, hogy egymás MELLETT legyenek és ne fagyjon ki a Compose a weight miatt!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp) // Fix magasság kell a Row-nak, ezt állítsd be ízlés szerint (pl. 500.dp)
        ) {
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
                            VideoPlayer(videoName = videoName, modifier = Modifier.fillMaxSize())
                        }
                    }
                    if (isPreview) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                text = "FIGYELJE A BEMUTATÓT!",
                                color = Color(0xFFFFB74D),
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                modifier = Modifier.padding(top = 16.dp)
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
        } // <-- ROW VÉGE

        // --- 3. BLOKK: INSTRUKCIÓK, IDŐZÍTŐ ÉS GOMBOK ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Instrukciók
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = viewModel.uiState.currentInstruction,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                if (viewModel.uiState.currentFallback.isNotBlank()) {
                    Text(
                        text = viewModel.uiState.currentFallback,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFB74D)
                    )
                }
            }

            // Időzítő és Stabilitás
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = viewModel.uiState.timerText,
                    fontSize = if (viewModel.uiState.isPreviewPhase) 48.sp else 80.sp,
                    fontWeight = FontWeight.Black,
                    color = if (viewModel.uiState.isPreviewPhase) Color(0xFFFFB74D) else Color.White
                )

                if (!viewModel.uiState.isPreviewPhase) {
                    Text(
                        text = viewModel.uiState.stabilityScore,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = viewModel.uiState.progressValue,
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        color = Color(0xFF4CAF50),
                        trackColor = Color.DarkGray
                    )
                } else {
                    Spacer(modifier = Modifier.height(36.dp))
                }
            }

            // Stop gomb
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
            ) {
                Text("LEÁLLÍTÁS", fontWeight = FontWeight.Bold)
            }
        } // 3. Blokk vége

        LaunchedEffect(viewModel.uiState.sessionCompleted) {
            if (viewModel.uiState.sessionCompleted) {
                delay(1000)
                onStop()
            }
        }
    } // <-- FŐ COLUMN VÉGE (Ide került a zárójel)
}