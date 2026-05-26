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
import kotlin.math.sqrt

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
        RhythmLineChart(result.rawTaskData[RehabTaskType.MARCHING_IN_PLACE.displayName] ?: emptyList())

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

    // 1. Kiszűrjük azokat a pillanatokat, amikor a beteg felemelte a lábát (levegőben volt).
    // Csak a tényleges "talajfogást" akarjuk átlagolni!
    val activeData = data.filter { (it.ch0 + it.ch1 + it.ch2) > 100 }

    // Ha valamiért nem volt aktív adat, biztonságból használjuk az eredetit, hogy ne fagyjon le
    val validData = if (activeData.isNotEmpty()) activeData else data

    // 2. Kiszámoljuk az aktív fázisok átlagát
    val avgCh0 = validData.map { it.ch0 }.average().toFloat()
    val avgCh1 = validData.map { it.ch1 }.average().toFloat()
    val avgCh2 = validData.map { it.ch2 }.average().toFloat()

    // 3. Fix 1023-as hardveres maximumhoz viszonyítunk.
    // Így egy közepes terhelés szépen sárga marad, és csak a durva aszimmetria lesz piros.
    val maxPressure = 1023f

    Canvas(modifier = Modifier.size(200.dp).background(Color.Transparent)) {
        // ch0 - Előtalp belső (Nagylábujj alatti párna)
        drawCircle(
            color = getHeatmapColor(avgCh0.toInt(), maxPressure),
            radius = 45f,
            center = Offset(size.width * 0.35f, size.height * 0.25f)
        )

        // ch1 - Előtalp külső (Kisujj alatti párna)
        drawCircle(
            color = getHeatmapColor(avgCh1.toInt(), maxPressure),
            radius = 45f,
            center = Offset(size.width * 0.65f, size.height * 0.25f)
        )

        // ch2 - Sarok
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
    val enhanced = kotlin.math.sqrt(normalized) // Kontrasztkiemelés
    val hue = 120f * (1f - enhanced) // 120 = Zöld, 0 = Piros
    return Color.hsv(hue = hue, saturation = 1f, value = 1f)
}

@Composable
fun SwayScatterPlot(data: List<SensoriaData>) {
    Canvas(modifier = Modifier.size(250.dp).padding(16.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val maxRadius = size.width / 2

        // Céltábla (Háttér) kirajzolása
        drawCircle(Color.DarkGray.copy(alpha = 0.4f), style = Stroke(2f), radius = maxRadius)
        drawCircle(Color.DarkGray.copy(alpha = 0.4f), style = Stroke(2f), radius = maxRadius * 0.5f)
        drawLine(Color.DarkGray.copy(alpha = 0.3f), start = Offset(cx, 0f), end = Offset(cx, size.height))
        drawLine(Color.DarkGray.copy(alpha = 0.3f), start = Offset(0f, cy), end = Offset(size.width, cy))

        // Csak az utolsó 300 pontot rajzoljuk, hogy ne legyen egy hatalmas paca az egész
        data.takeLast(300).forEach { point ->
            val total = (point.ch0 + point.ch1 + point.ch2).toFloat()

            // Csak akkor rajzolunk pontot, ha a beteg tényleg ráállt a zoknira (nem a levegőben lóbálja)
            if (total > 10f) {
                val frontTotal = (point.ch0 + point.ch1).toFloat()

                // X tengely: Bal (ch0) és Jobb (ch1) egyensúly. Értéke: -1.0 és +1.0 között
                val rawXRatio = if (frontTotal > 0) (point.ch1 - point.ch0) / frontTotal else 0f

                // Y tengely: Sarok (ch2) és Előtalp (frontTotal) egyensúly. Értéke: -1.0 és +1.0 között
                val rawYRatio = (point.ch2 - frontTotal) / total

                // Vektorhossz számítása (Pitagorasz-tétel) a kilógás megakadályozására
                val magnitude = kotlin.math.sqrt((rawXRatio * rawXRatio) + (rawYRatio * rawYRatio))

                // Ha a pont kilógna a körből (magnitude > 1), visszaskálázzuk pontosan 1-re!
                val scale = if (magnitude > 1f) 1f / magnitude else 1f

                val finalXRatio = rawXRatio * scale
                val finalYRatio = rawYRatio * scale

                // Kiszámoljuk a végső koordinátákat a 0.85-ös szorzóval (így hagyunk egy kis belső margót is)
                val x = cx + (finalXRatio * maxRadius * 0.85f)
                val y = cy + (finalYRatio * maxRadius * 0.85f)

                // Pont kirajzolása (áttetsző kékkel, hogy a sűrűsödés szépen látszódjon)
                drawCircle(Color(0xFF64B5F6), radius = 4f, center = Offset(x, y), alpha = 0.6f)
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