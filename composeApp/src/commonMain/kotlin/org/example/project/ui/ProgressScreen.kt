package org.example.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.max
import org.example.project.data.BiomechanicsResult
import org.example.project.viewmodel.SessionViewModel
import kotlin.math.roundToInt
import kotlin.time.Instant

// Kibővített adatstruktúra, ami tárolja a formázott dátumot és a feladat nevét is
 data class TimedScore(
    val timeMillis: Long,
    val score: Float,
    val dateLabel: String,
    val exerciseName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: SessionViewModel, onBack: () -> Unit) {
    val results = viewModel.allBiomechanicsResults

    // FELTÉTELEZÉS: A ViewModel-ben benne vannak a mérések adatai (MeasurementDocument listaként),
    // hogy tudjuk a feladat nevét és a kezdés idejét. Ha nálad máshogy hívják, írd át ezt a sort!
    val sessions = viewModel.mySessions

    var selectedMetric by remember { mutableStateOf("Összpontszám") }
    var expanded by remember { mutableStateOf(false) }
    val metrics = listOf("Összpontszám", "Stabilitás", "Alátámasztás", "Szimmetria")

    // Összekapcsoljuk a BiomechanicsResult-ot a Sessions adatokkal
    val points = results.mapNotNull { result ->
        val score = result.metricValue(selectedMetric)?.toFloat()
        val session = sessions.find { it.measurement_id == result.session_id }

        if (session != null && score != null && score.isFinite()) {
            val startStr = session.start_time ?: ""
            // Kinyerjük a formázott időpontot: "2026-10-07 18:51:06"
            val label = startStr.replace("T", " ").substringBefore(".")
            val millis = parseStartTimeToMillis(startStr)
            val exerciseName = session.exercise_name ?: "Ismeretlen feladat"

            TimedScore(millis, score.coerceIn(0f, 10f), label, exerciseName)
        } else {
            null
        }
    }.sortedBy { it.timeMillis }

    // Feladatok alapján csoportosítjuk a pontokat
    val groupedPoints = points.groupBy { it.exerciseName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Haladásom") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Vissza")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "A mérések pontszámainak időbeli alakulása feladatonként.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedMetric,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Megjelenített mutató") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    metrics.forEach { metric ->
                        DropdownMenuItem(text = { Text(metric) }, onClick = {
                            selectedMetric = metric
                            expanded = false
                        })
                    }
                }
            }

            if (groupedPoints.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Még nincs elegendő kiértékelt mérés ehhez a mutatóhoz.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                // Minden feladathoz külön kártyát és grafikont rajzolunk
                groupedPoints.forEach { (exerciseName, exercisePoints) ->
                    Text(
                        text = "Feladat: $exerciseName",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        ProgressLineChart(data = exercisePoints)
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressLineChart(data: List<TimedScore>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = Color.LightGray
    val textMeasurer = rememberTextMeasurer()

    // Dinamikus szélesség kiszámítása, hogy lapozható maradjon sok adat esetén
    val minWidth = 300.dp
    val pointSpacing = 80.dp // Ennyi hely kell két adatpont közé, hogy kiférjen a dátum
    val calculatedWidth = (data.size * pointSpacing.value).dp
    val chartWidth = max(minWidth, calculatedWidth)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray.copy(alpha = 0.2f))
            .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
    ) {
        // --- Y TENGELY (RAGADÓS, NEM GÖRDÜL) ---
        Column(
            modifier = Modifier
                .height(200.dp)
                .padding(end = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(text = "10", color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = "5", color = textColor, fontSize = 10.sp)
            Text(text = "0", color = textColor, fontSize = 10.sp)
        }

        // --- X TENGELY ÉS DIAGRAM (VÍZSZINTESEN GÖRDÜL) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            Canvas(
                modifier = Modifier
                    .width(chartWidth)
                    .height(200.dp)
            ) {
                val canvasWidth = size.width
                val xAxisHeight = 32.dp.toPx()
                val chartHeight = size.height - xAxisHeight
                val maxScore = 10f

                // --- BELSŐ MARGÓ A SZÖVEGEK MIATT ---
                // Eltoljuk 36 dp-vel a grafikon kezdetét és végét, hogy a szöveg ne lógjon le a Canvasról
                val horizontalPadding = 36.dp.toPx()
                val usableWidth = canvasWidth - (2 * horizontalPadding)

                // 1. Háttér rácsvonalak (vízszintes)
                drawLine(color = Color.Gray.copy(alpha = 0.2f), start = Offset(0f, 0f), end = Offset(canvasWidth, 0f))
                drawLine(color = Color.Gray.copy(alpha = 0.2f), start = Offset(0f, chartHeight / 2), end = Offset(canvasWidth, chartHeight / 2))

                // 2. X és Y főtengely vonalai a Canvas szélén
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(0f, 0f),
                    end = Offset(0f, chartHeight),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(0f, chartHeight),
                    end = Offset(canvasWidth, chartHeight),
                    strokeWidth = 3f
                )

                val path = Path()

                // Kiszámoljuk az X távolságot a BELSŐ használható szélesség alapján
                val stepX = if (data.size > 1) usableWidth / (data.size - 1) else usableWidth / 2f

                data.forEachIndexed { index, point ->
                    // Az adatpont X koordinátájához hozzáadjuk a belső margót (horizontalPadding)
                    val x = horizontalPadding + (if (data.size > 1) index * stepX else stepX)
                    val y = chartHeight - ((point.score / maxScore) * chartHeight)

                    // Görbe összekötése
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }

                    // 3. X-tengely jelölő (Tick mark)
                    drawLine(
                        color = Color.White.copy(alpha = 0.6f),
                        start = Offset(x, chartHeight),
                        end = Offset(x, chartHeight + 10f),
                        strokeWidth = 2f
                    )

                    // 4. Dátum kiírása 2 sorban
                    val dateParts = point.dateLabel.split(" ")
                    val datePart = dateParts.getOrNull(0) ?: ""
                    val timePart = dateParts.getOrNull(1) ?: ""

                    val dateLayout = textMeasurer.measure(
                        text = datePart,
                        style = TextStyle(color = textColor, fontSize = 9.sp)
                    )
                    val timeLayout = textMeasurer.measure(
                        text = timePart,
                        style = TextStyle(color = textColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    )

                    // A margó miatt most már a "x - (width / 2f)" is pozitív szám lesz az első pontnál!
                    drawText(
                        textLayoutResult = dateLayout,
                        topLeft = Offset(x - (dateLayout.size.width / 2f), chartHeight + 4.dp.toPx())
                    )
                    drawText(
                        textLayoutResult = timeLayout,
                        topLeft = Offset(x - (timeLayout.size.width / 2f), chartHeight + 16.dp.toPx())
                    )

                    // 5. Pontszám kiírása
                    val scoreText = "${(point.score * 10).roundToInt() / 10f}"
                    val scoreLayout = textMeasurer.measure(
                        text = scoreText,
                        style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                    drawText(
                        textLayoutResult = scoreLayout,
                        topLeft = Offset(x - (scoreLayout.size.width / 2f), y - scoreLayout.size.height - 8.dp.toPx())
                    )

                    // Adatpont pötty
                    drawCircle(
                        color = primaryColor,
                        radius = 12f,
                        center = Offset(x, y)
                    )
                }

                // Vonal megrajzolása
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

// Segédfüggvény a metrikák értékeinek kinyeréséhez a BiomechanicsResult objektumból
private fun BiomechanicsResult.metricValue(metricName: String): Double? {
    return when (metricName) {
        "Stabilitás" -> this.stabilitas_pont
        "Alátámasztás" -> this.alatamasztas_pont
        "Szimmetria" -> this.szimmetria_pont
        else -> this.osszpontszam
    }
}

// Segédfüggvény a start_time mező időbélyéggé alakításához (rendezéshez)
private fun parseStartTimeToMillis(timestampStr: String): Long {
    if (timestampStr.isBlank()) return 0L
    return try {
        // Hozzácsapjuk a 'Z' (UTC) jelölőt a szabványos parse-oláshoz, ha hiányzik
        val isoString = if (!timestampStr.endsWith("Z")) "${timestampStr}Z" else timestampStr
        kotlinx.datetime.Instant.parse(isoString).toEpochMilliseconds()
    } catch (e: Exception) {
        0L
    }
}