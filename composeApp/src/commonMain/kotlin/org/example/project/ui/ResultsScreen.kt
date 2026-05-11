package org.example.project.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.*

@Composable
fun ResultsScreen(result: FullSessionResult, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Mérés Eredményei",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 1. Összegző Kártyák
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoCard(
                modifier = Modifier.weight(1f),
                title = "Fáradási Index",
                value = "${result.fatigueIndex.toInt()}%",
                color = if (result.fatigueIndex > 15) Color(0xFFEF5350) else Color(0xFF4CAF50)
            )
            InfoCard(
                modifier = Modifier.weight(1f),
                title = "Lefagyások (FoG)",
                value = "${result.globalFogEvents}",
                color = if (result.globalFogEvents > 0) Color(0xFFFFB74D) else Color(0xFF4CAF50)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Talp-hőtérkép
        Text("Nyomáseloszlás (Hőtérkép)", style = MaterialTheme.typography.titleMedium, color = Color.LightGray)
        FootHeatmap(result.rawTaskData.values.flatten())

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Céltábla (Súlypont vándorlás)
        Text("Egyensúly-stabilitás (CoP)", style = MaterialTheme.typography.titleMedium, color = Color.LightGray)
        SwayScatterPlot(result.rawTaskData[RehabTaskType.TANDEM_WALKING.displayName] ?: emptyList())

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Ritmus-vonal (Vonaldiagram)
        Text("Járásritmus (Lépésközök)", style = MaterialTheme.typography.titleMedium, color = Color.LightGray)
        RhythmLineChart(result.rawTaskData[RehabTaskType.MARCHING_IN_PLACE.displayName] ?: emptyList())

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

        // 5. ÚJ RÉSZ: Részletes Feladat Eredmények (Még mindig a Column-on belül!)
        Text("Részletes Feladat Eredmények", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        // Végigmegyünk a ViewModel által kiszámolt eredményeken
        result.taskResults.forEach { (taskName, taskData) ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(taskName, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Ha dinamikus feladat (pl. járás)
                    if (taskData is DynamicAnalysisResult) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Lépésszám: ${taskData.stepCount}", color = Color.LightGray)
                            Text("Sarokhasználat: ${taskData.heelStrikeQuality.toInt()}%", color = Color.LightGray)
                        }
                        Text(
                            "Amplitúdó csökkenés (Fáradás): ${taskData.amplitudeDropoff.toInt()}%",
                            color = Color.LightGray
                        )
                    }
                    // Ha statikus feladat (pl. állás)
                    else if (taskData is StaticAnalysisResult) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Remegés index: ${taskData.tremorIndex.toInt()}", color = Color.LightGray)
                            Text(
                                "Hátraesés kockázata: ${if (taskData.backwardFallRisk) "Magas" else "Alacsony"}",
                                color = if (taskData.backwardFallRisk) Color(0xFFEF5350) else Color(0xFF4CAF50)
                            )
                        }
                        Text("Súlypont kilengés: ${taskData.swayVariability.toInt()}", color = Color.LightGray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Gomb a legvégén
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("VISSZA A FŐMENÜBE", fontWeight = FontWeight.Bold)
        }
    } // Itt zárul a Column!
} // Itt zárul a ResultsScreen!

@Composable
fun InfoCard(modifier: Modifier, title: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// --- Vizuális Komponensek ---

@Composable
fun FootHeatmap(data: List<SensoriaData>) {
    val avgCh0 = data.map { it.ch0 }.average().toFloat()
    val avgCh1 = data.map { it.ch1 }.average().toFloat()
    val avgCh2 = data.map { it.ch2 }.average().toFloat()
    val maxVal = 1023f // Sensoria max értéke

    Canvas(modifier = Modifier.size(200.dp).padding(16.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.3f, size.height * 0.1f)
            quadraticTo(size.width * 0.1f, size.height * 0.5f, size.width * 0.3f, size.height * 0.9f)
            lineTo(size.width * 0.7f, size.height * 0.9f)
            quadraticTo(size.width * 0.9f, size.height * 0.5f, size.width * 0.7f, size.height * 0.1f)
            close()
        }
        drawPath(path, color = Color.DarkGray, style = Stroke(width = 2f))

        fun getColor(value: Float) = Color(
            red = (value / maxVal).coerceIn(0f, 1f),
            green = (1f - (value / maxVal)).coerceIn(0f, 1f),
            blue = 0.2f
        )

        drawCircle(getColor(avgCh0), radius = 25f, center = Offset(size.width * 0.35f, size.height * 0.25f))
        drawCircle(getColor(avgCh1), radius = 25f, center = Offset(size.width * 0.65f, size.height * 0.25f))
        drawCircle(getColor(avgCh2), radius = 35f, center = Offset(size.width * 0.5f, size.height * 0.75f))
    }
}

@Composable
fun SwayScatterPlot(data: List<SensoriaData>) {
    Canvas(modifier = Modifier.size(250.dp).background(Color.Black).padding(8.dp)) {
        drawCircle(Color.DarkGray, style = Stroke(1f), radius = size.width / 2)
        drawCircle(Color.DarkGray, style = Stroke(1f), radius = size.width / 4)

        data.takeLast(200).forEach { point ->
            val total = (point.ch0 + point.ch1 + point.ch2).toFloat()
            if (total > 0) {
                val x = ((point.ch1 - point.ch0).toFloat() / total) * size.width + (size.width / 2)
                val y = (point.ch2.toFloat() / total) * size.height
                drawCircle(Color(0xFF64B5F6), radius = 3f, center = Offset(x, y), alpha = 0.5f)
            }
        }
    }
}

@Composable
fun RhythmLineChart(data: List<SensoriaData>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp).padding(16.dp)) {
        val zAccels = data.map { it.accelZ.toFloat() }.takeLast(100)
        if (zAccels.isEmpty()) return@Canvas

        val maxZ = 1024f
        val stepX = size.width / zAccels.size

        for (i in 0 until zAccels.size - 1) {
            drawLine(
                color = Color(0xFF4CAF50),
                start = Offset(i * stepX, size.height - (zAccels[i] / maxZ * size.height)),
                end = Offset((i + 1) * stepX, size.height - (zAccels[i+1] / maxZ * size.height)),
                strokeWidth = 3f
            )
        }
    }
}