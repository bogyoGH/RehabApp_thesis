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
import org.example.project.SessionViewModel

@Composable
fun ExecutionScreen(viewModel: SessionViewModel, onStop: () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. BLOKK: FELADAT CÍME ---
        Text(
            text = viewModel.currentTaskName,
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF64B5F6),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
        )

        // --- 2. BLOKK: EGYYMÁS MELLETTI VIDEÓK (50-50%) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
        ) {
            // BAL OLDAL: FELADAT VIDEÓ
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Black)
                    .clipToBounds()
            ) {
                // A key ide kerül, a viewModel.currentTaskName-t vagy currentVideoName-t figyeli
                key(viewModel.currentVideoName) {
                    viewModel.currentVideoName?.let { videoName ->
                        VideoPlayer(
                            videoName = videoName,
                            modifier = Modifier.fillMaxSize()
                        )
                    } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No video", color = Color.DarkGray)
                    }
                }
            }

            // JOBB OLDAL: SAJÁT KAMERA (Ez most már újra a Row-n belül van, a bal oldali Box mellett)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Black)
                    .clipToBounds()
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize()
                )
            }
        } // A Row itt ér véget!

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
                    text = viewModel.currentInstruction,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                if (viewModel.currentFallback.isNotBlank()) {
                    Text(
                        text = viewModel.currentFallback,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFB74D)
                    )
                }
            }

            // Időzítő és Stabilitás
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = viewModel.timerText,
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = viewModel.stabilityScore,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = viewModel.progressValue,
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    color = Color(0xFF4CAF50),
                    trackColor = Color.DarkGray
                )
            }

            // Stop gomb
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
            ) {
                Text("STOP SESSION", fontWeight = FontWeight.Bold)
            }
        }

        LaunchedEffect(viewModel.sessionCompleted) {
            if (viewModel.sessionCompleted) {
                delay(1000)
                onStop()
            }
        }
    }
}