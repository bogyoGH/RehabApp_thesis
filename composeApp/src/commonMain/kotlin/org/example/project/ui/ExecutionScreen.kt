package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.project.SessionViewModel

@Composable
fun ExecutionScreen(viewModel: SessionViewModel, onStop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), // Kicsit kisebb padding, hogy több hely legyen
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // --- 1. BLOKK: FELADAT CÍME ÉS VIDEÓ ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = viewModel.currentTaskName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF64B5F6),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // A videólejátszó meghívása
            viewModel.currentVideoName?.let { videoName ->
                VideoPlayer(
                    videoName = videoName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp) // Kényelmes magasság a videónak
                )
            } ?: run {
                // Ha nincs videó (Custom feladat), egy üres helykitöltőt mutatunk
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No video available", color = Color.DarkGray)
                }
            }
        }

        // --- 2. BLOKK: INSTRUKCIÓK ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = viewModel.currentInstruction,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )

            // A Fallback csak akkor jelenik meg, ha van szövege
            if (viewModel.currentFallback.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = viewModel.currentFallback,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFB74D) // Narancssárga figyelemfelhívás
                )
            }
        }

        // --- 3. BLOKK: IDŐZÍTŐ, STABILITÁS ÉS PROGRESS BAR ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = viewModel.timerText,
                fontSize = 80.sp, // Lekicsinyítve 120-ról, hogy minden kiférjen
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Text(
                text = viewModel.stabilityScore,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = viewModel.progressValue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = Color(0xFF4CAF50),
                trackColor = Color.DarkGray
            )
        }

        // --- 4. BLOKK: GOMBOK ÉS MENTÉS STÁTUSZ ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
            ) {
                Text("STOP SESSION", fontWeight = FontWeight.Bold)
            }

            if (viewModel.saveStatus.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = viewModel.saveStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Automatikus leállítás, ha a feladatok befejeződtek
        LaunchedEffect(viewModel.sessionCompleted) {
            if (viewModel.sessionCompleted) {
                delay(1000)
                onStop()
            }
        }
    }
}