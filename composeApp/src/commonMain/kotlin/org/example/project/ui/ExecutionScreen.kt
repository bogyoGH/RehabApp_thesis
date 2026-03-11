package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.SessionViewModel

@Composable
fun ExecutionScreen(viewModel: SessionViewModel, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(viewModel.currentTaskName, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF64B5F6))
            Text(viewModel.stabilityScore, style = MaterialTheme.typography.bodyLarge, color = Color.LightGray)
        }

        Text(
            text = viewModel.timerText,
            fontSize = 120.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )

        LinearProgressIndicator(
            progress = viewModel.progressValue,
            modifier = Modifier.fillMaxWidth().height(12.dp),
            color = Color(0xFF4CAF50),
            trackColor = Color.DarkGray
        )

        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(0.7f).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
        ) {
            Text("STOP SESSION", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (viewModel.saveStatus.isNotBlank()) {
            Text(
                text = viewModel.saveStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}