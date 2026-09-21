package org.example.project.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import org.example.project.analysis.FullSessionResult
import org.example.project.analysis.DynamicAnalysisResult
import org.example.project.analysis.StaticAnalysisResult
import org.example.project.data.SensoriaData
import org.example.project.data.RehabTaskType

@Composable
fun ResultsScreen(
    result: FullSessionResult,
    onBackToMenu: () -> Unit,
    onGoToSavedSessions: () -> Unit
) {
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
        // JAVÍTVA: Az emptyList<SensoriaData>() megmondja a fordítónak a pontos típust.
        RhythmLineChart(result.rawTaskData[RehabTaskType.MARCHING_IN_PLACE.displayName] ?: emptyList<SensoriaData>())

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

        // 5. Részletes Feladat Eredmények
        Text("Részletes Feladat Eredmények", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        result.taskResults.forEach { (taskName, taskData) ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(taskName, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
                    Spacer(modifier = Modifier.height(8.dp))

                    if (taskData is DynamicAnalysisResult) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Lépésszám: ${taskData.stepCount}", color = Color.LightGray)
                            Text("Sarokhasználat: ${taskData.heelStrikeQuality.toInt()}%", color = Color.LightGray)
                        }
                        Text(
                            "Amplitúdó csökkenés (Fáradás): ${taskData.amplitudeDropoff.toInt()}%",
                            color = Color.LightGray
                        )
                    } else if (taskData is StaticAnalysisResult) {
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

        // 6. Navigációs Gombok
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onBackToMenu,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("VISSZA A FŐMENÜBE", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onGoToSavedSessions,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("MENTETT MÉRÉSEK", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// --- Vizuális Segédkomponensek ---

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

@Composable
fun FootHeatmap(data: List<SensoriaData>) {
    if (data.isEmpty()) return

    val activeData = data.filter { (it.ch0 + it.ch1 + it.ch2) > 100 }
    val validData = if (activeData.isNotEmpty()) activeData else data

    val avgCh0 = validData.map { it.ch0 }.average().toFloat()
    val avgCh1 = validData.map { it.ch1 }.average().toFloat()
    val avgCh2 = validData.map { it.ch2 }.average().toFloat()
    val maxPressure = 1023f

    Canvas(modifier = Modifier.size(200.dp).background(Color.Transparent)) {
        drawTrapezoidShape(size)
        drawCircle(
            color = getHeatmapColor(avgCh0.toInt(), maxPressure),
            radius = 45f,
            center = Offset(size.width * 0.35f, size.height * 0.25f)
        )
        drawCircle(
            color = getHeatmapColor(avgCh1.toInt(), maxPressure),
            radius = 45f,
            center = Offset(size.width * 0.65f, size.height * 0.25f)
        )
        drawCircle(
            color = getHeatmapColor(avgCh2.toInt(), maxPressure),
            radius = 55f,
            center = Offset(size.width * 0.5f, size.height * 0.75f)
        )
    }
}

fun getHeatmapColor(rawPressure: Int, maxInSession: Float = 1023f): Color {
    val safetyMax = if (maxInSession <= 0f) 1023f else maxInSession
    val normalized = (rawPressure.toFloat() / safetyMax).coerceIn(0f, 1f)
    val enhanced = kotlin.math.sqrt(normalized)
    val hue = 120f * (1f - enhanced)
    return Color.hsv(hue = hue, saturation = 1f, value = 1f)
}

fun DrawScope.drawTrapezoidShape(size: Size) {
    val path = Path().apply {
        moveTo(size.width * 0.1f, size.height * 0.1f)
        lineTo(size.width * 0.9f, size.height * 0.1f)
        lineTo(size.width * 0.7f, size.height * 0.95f)
        lineTo(size.width * 0.3f, size.height * 0.95f)
        close()
    }
    drawPath(path = path, color = Color.LightGray, style = Stroke(width = 3f))
    drawPath(path = path, color = Color.LightGray, style = Stroke(width = 3f))
}

@Composable
fun SwayScatterPlot(data: List<SensoriaData>) {
    Canvas(modifier = Modifier.size(250.dp).padding(16.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val maxRadius = size.width / 2

        drawCircle(Color.White.copy(alpha = 1.0f), style = Stroke(3f), radius = maxRadius)
        drawCircle(Color.White.copy(alpha = 1.0f), style = Stroke(3f), radius = maxRadius * 0.5f)
        drawLine(Color.White.copy(alpha = 1.0f), start = Offset(cx, 0f), end = Offset(cx, size.height))
        drawLine(Color.White.copy(alpha = 1.0f), start = Offset(0f, cy), end = Offset(size.width, cy))

        val validPoints = data.mapNotNull { point ->
            val total = (point.ch0 + point.ch1 + point.ch2).toFloat()
            if (total > 10f) {
                val frontTotal = (point.ch0 + point.ch1).toFloat()
                val rawXRatio = if (frontTotal > 0) (point.ch1 - point.ch0) / frontTotal else 0f
                val rawYRatio = (point.ch2 - frontTotal) / total
                Pair(rawXRatio, rawYRatio)
            } else null
        }

        if (validPoints.isEmpty()) return@Canvas

        val avgX = validPoints.map { it.first }.average().toFloat()
        val avgY = validPoints.map { it.second }.average().toFloat()
        val zoomFactor = 2.5f

        validPoints.forEach { (rawX, rawY) ->
            val centeredX = (rawX - avgX) * zoomFactor
            val centeredY = (rawY - avgY) * zoomFactor
            val magnitude = kotlin.math.sqrt((centeredX * centeredX) + (centeredY * centeredY))
            val scale = if (magnitude > 1f) 1f / magnitude else 1f
            val finalXRatio = centeredX * scale
            val finalYRatio = centeredY * scale
            val x = cx + (finalXRatio * maxRadius * 0.85f)
            val y = cy + (finalYRatio * maxRadius * 0.85f)
            drawCircle(Color(0xFF64B5F6), radius = 4f, center = Offset(x, y), alpha = 0.6f)
        }
    }
}

@Composable
fun RhythmLineChart(
    data: List<SensoriaData>,
    sampleRateMs: Long = 50L
) {
    if (data.isEmpty()) return

    val scrollState = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()

    val magnitudes = data.map {
        kotlin.math.sqrt(it.accelX.toDouble().pow(2) + it.accelY.toDouble().pow(2) + it.accelZ.toDouble().pow(2)).toFloat()
    }

    val minVal = magnitudes.minOrNull() ?: 0f
    val maxVal = magnitudes.maxOrNull() ?: 1024f
    val range = (maxVal - minVal).coerceAtLeast(1f)
    val pixelsPerPoint = 5.dp
    val chartWidth = pixelsPerPoint * magnitudes.size.coerceAtLeast(1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(16.dp)
            .horizontalScroll(scrollState)
    ) {
        Canvas(
            modifier = Modifier
                .width(chartWidth)
                .fillMaxHeight()
        ) {
            val chartHeight = size.height - 40.dp.toPx()
            val stepX = size.width / magnitudes.size.coerceAtLeast(1).toFloat()
            val path = Path()

            magnitudes.forEachIndexed { i, mag ->
                val x = i * stepX
                val y = chartHeight - ((mag - minVal) / range) * chartHeight
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(path, Color(0xFF64B5F6), style = Stroke(width = 2.dp.toPx()))

            val axisY = chartHeight + 10.dp.toPx()
            drawLine(
                color = Color.LightGray,
                start = Offset(0f, axisY),
                end = Offset(size.width, axisY),
                strokeWidth = 2f
            )

            val pointsPerSecond = (1000 / sampleRateMs).toInt()

            for (i in 0 until magnitudes.size step pointsPerSecond) {
                val x = i * stepX
                drawLine(
                    color = Color.Gray,
                    start = Offset(x, axisY - 10f),
                    end = Offset(x, axisY + 10f),
                    strokeWidth = 2f
                )
                val elapsedSeconds = (i * sampleRateMs) / 1000
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${elapsedSeconds}s",
                    topLeft = Offset(x - 15f, axisY + 15f),
                    style = TextStyle(color = Color.Gray, fontSize = 12.sp)
                )
            }
        }
    }
}