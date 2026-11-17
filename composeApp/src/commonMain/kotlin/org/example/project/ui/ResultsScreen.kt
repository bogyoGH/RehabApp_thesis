package org.example.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.max
import org.example.project.analysis.FullSessionResult
import org.example.project.data.BiomechanicsResult
import org.example.project.data.TrunkAnglePoint
import org.example.project.analysis.SockAnalysisResult
import org.example.project.analysis.CopPoint
import kotlin.math.roundToInt
import kotlin.math.abs

@Composable
fun ResultsScreen(
    result: FullSessionResult,
    biomechanicsResult: BiomechanicsResult?,
    onBackToMenu: () -> Unit,
    onGoToSavedSessions: () -> Unit,
    sockAnalysisResult: SockAnalysisResult? = null,
    resultsLoading: Boolean = false,
    resultsError: String? = null,
    onRefreshResults: () -> Unit = {}
) {
    var expandedTechDetails by remember { mutableStateOf(false) }
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }

    if (infoDialogTitle != null) {
        AlertDialog(
            onDismissRequest = { infoDialogTitle = null },
            title = { Text(infoDialogTitle!!) },
            text = { Text(infoDialogText!!) },
            confirmButton = {
                TextButton(onClick = { infoDialogTitle = null }) { Text("Értem") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (biomechanicsResult != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("ÖSSZPONTSZÁM", color = Color.LightGray, fontWeight = FontWeight.Bold)

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp).padding(16.dp)) {
                        CircularProgressIndicator(
                            progress = ((biomechanicsResult.osszpontszam ?: 0.0) / 10.0).toFloat(),
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.DarkGray,
                            strokeWidth = 8.dp
                        )
                        Text(
                            text = "${biomechanicsResult.osszpontszam ?: "-"}",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    Surface(
                        color = when (biomechanicsResult.ertekeles) {
                            "Kiváló" -> Color(0xFF2E7D32)
                            "Jó" -> Color(0xFFF57C00)
                            else -> Color(0xFFC62828)
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = biomechanicsResult.ertekeles ?: "-",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Az aktuális mérés alapján az egyensúly megtartása ${biomechanicsResult.ertekeles?.lowercase() ?: "-"}, a test súlypontja leginkább ${biomechanicsResult.terhelesi_irany?.lowercase() ?: "-"} helyezkedett el.",
                        textAlign = TextAlign.Center,
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            ScoreCard(
                title = "Stabilitás",
                score = biomechanicsResult.stabilitas_pont,
                onInfoClick = {
                    infoDialogTitle = "Stabilitás"
                    infoDialogText =
                        "A testtartás egyenletességét mutatja. A magasabb érték nyugodtabb, stabilabb tartást, kisebb ingadozást jelent."
                }
            )

            ScoreCard(
                title = "Alátámasztás",
                score = biomechanicsResult.alatamasztas_pont,
                onInfoClick = {
                    infoDialogTitle = "Alátámasztás"
                    infoDialogText =
                        "Azt mutatja, mennyire stabil a lábak helyzete a talajon/labdán. A magasabb érték azt jelenti, hogy nem volt csúszkálás vagy lelépés a mérés alatt."
                }
            )

            ScoreCard(
                title = "Szimmetria",
                score = biomechanicsResult.szimmetria_pont,
                isSymmetry = true,
                direction = biomechanicsResult.terhelesi_irany,
                onInfoClick = {
                    infoDialogTitle = "Szimmetria"
                    infoDialogText =
                        "A terhelés bal és jobb láb közötti eloszlását mutatja. Ha a mutató középen van, a terhelés egyenletes."
                }
            )

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Terhelési irány", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = biomechanicsResult.terhelesi_irany?.uppercase() ?: "-",
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    .clickable { expandedTechDetails = !expandedTechDetails },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Törzsingadozás grafikon", color = Color.White, fontWeight = FontWeight.Bold)
                        Icon(
                            imageVector = if (expandedTechDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Lenyitás",
                            tint = Color.LightGray
                        )
                    }

                    AnimatedVisibility(visible = expandedTechDetails) {
                        Column(modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                            TechRow(
                                "Törzsingadozás szórása",
                                "${biomechanicsResult.torzs_ingadozas_sd ?: "-"}°",
                                "A felsőtest dőlésének statisztikai szórása."
                            )

                            biomechanicsResult.torzsszog_idosor?.let { angleData ->
                                if (angleData.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Szög (Y tengely) és Idő (X tengely)",
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TrunkAngleChart(angleData)
                                }
                            }
                        }
                    }
                }
            }

        } else {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Feldolgozás alatt",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Adatok feltöltve a felhőbe!",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "A videó és a szenzoradatok sikeresen megérkeztek a szerverre. A biomechanikai elemzés (MMPose) és a pontszámok kiszámítása a háttérben zajlik.\n\nAmint a feldolgozás befejeződött, a mérés eredményei elérhetővé válnak az Előzmények fülön és a Haladás grafikonon.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (resultsLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }
        resultsError?.let { error ->
            Text("Eredmények betöltési hibája: $error", color = MaterialTheme.colorScheme.error)
        }

        if (sockAnalysisResult != null) {
            SockAnalysisCard(sockAnalysisResult)
        } else if (!resultsLoading) {
            Text("A zoknis eredmény még nem érhető el ennél a mérésnél.",
                color = Color.LightGray, modifier = Modifier.padding(bottom = 12.dp))
        }
        OutlinedButton(onClick = onRefreshResults, modifier = Modifier.fillMaxWidth()) {
            Text("Elemzési eredmények frissítése")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onGoToSavedSessions,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Előzmények", textAlign = TextAlign.Center)
            }

            Button(
                onClick = onBackToMenu,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Főmenü", textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun TrunkAngleChart(data: List<TrunkAnglePoint>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = Color.LightGray
    val textMeasurer = rememberTextMeasurer()

    val rawMinAngle = data.minOf { it.angle_deg }.toFloat()
    val rawMaxAngle = data.maxOf { it.angle_deg }.toFloat()

    val minAngle = rawMinAngle - 2f
    val maxAngle = rawMaxAngle + 2f
    val angleRange = if (maxAngle == minAngle) 1f else (maxAngle - minAngle)

    val minWidth = 300.dp
    val calculatedWidth = (data.size * 3).dp
    val chartWidth = max(minWidth, calculatedWidth)

    val totalDuration = data.last().time_s - data.first().time_s
    val timeLabelInterval = when {
        totalDuration > 60 -> 1.0
        totalDuration > 20 -> 1.0
        totalDuration > 10 -> 2.0
        else -> 1.0
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .height(200.dp)
                .padding(end = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(text = "${(maxAngle * 10).roundToInt() / 10f}°", color = textColor, fontSize = 10.sp)
            Text(text = "${(((maxAngle + minAngle) / 2) * 10).roundToInt() / 10f}°", color = textColor, fontSize = 10.sp)
            Text(text = "${(minAngle * 10).roundToInt() / 10f}°", color = textColor, fontSize = 10.sp)
        }

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
                val xAxisHeight = 24.dp.toPx()
                val chartHeight = size.height - xAxisHeight

                drawLine(color = Color.Gray.copy(alpha = 0.2f), start = Offset(0f, 0f), end = Offset(canvasWidth, 0f))
                drawLine(color = Color.Gray.copy(alpha = 0.2f), start = Offset(0f, chartHeight / 2), end = Offset(canvasWidth, chartHeight / 2))

                if (0f in minAngle..maxAngle) {
                    val zeroY = chartHeight - ((0f - minAngle) / angleRange) * chartHeight

                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(0f, zeroY),
                        end = Offset(canvasWidth, zeroY),
                        strokeWidth = 2f
                    )
                }

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
                var nextLabelTime = 0.0

                data.forEachIndexed { index, point ->
                    val x = (index.toFloat() / (data.size - 1).coerceAtLeast(1)) * canvasWidth

                    val normalizedY = (point.angle_deg.toFloat() - minAngle) / angleRange
                    val y = chartHeight - (normalizedY * chartHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }

                    if (point.time_s >= nextLabelTime) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(x, chartHeight),
                            end = Offset(x, chartHeight + 10f),
                            strokeWidth = 2f
                        )

                        val text = "${point.time_s.toInt()}s"
                        val textLayout = textMeasurer.measure(
                            text = text,
                            style = TextStyle(color = textColor, fontSize = 10.sp)
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(x - (textLayout.size.width / 2f), chartHeight + 12f)
                        )

                        nextLabelTime += timeLabelInterval
                    }
                }

                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
fun ScoreCard(
    title: String,
    score: Double?,
    isSymmetry: Boolean = false,
    direction: String? = null,
    onInfoClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text("${score ?: "-"} / 10", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (isSymmetry) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bal", color = Color.Gray, fontSize = 12.sp)
                    Slider(
                        value = if (direction == "Balra") 0.2f else if (direction == "Jobbra") 0.8f else 0.5f,
                        onValueChange = {},
                        enabled = false,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            disabledThumbColor = MaterialTheme.colorScheme.primary,
                            disabledActiveTrackColor = Color.Gray
                        )
                    )
                    Text("Jobb", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LinearProgressIndicator(
                    progress = ((score ?: 0.0) / 10.0).toFloat(),
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                        .background(Color.DarkGray, RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
fun TechRow(label: String, value: String, desc: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Text(desc, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun SockAnalysisCard(sock: SockAnalysisResult) {
    val left = sock.left_metrics
    val right = sock.right_metrics
    val combined = sock.combined_metrics
    val leftForefoot = combined?.left_forefoot_fraction
        ?: left?.heel_relative_mean?.let { 1.0 - it }
    val rightForefoot = combined?.right_forefoot_fraction
        ?: right?.heel_relative_mean?.let { 1.0 - it }
    val mlLeft = left?.ml_sd
    val mlRight = right?.ml_sd
    val computedAsymmetry = if (mlLeft != null && mlRight != null && mlLeft + mlRight > 0.0) {
        abs(mlLeft - mlRight) / (mlLeft + mlRight)
    } else null
    val asymmetry = combined?.ml_variability_asymmetry ?: computedAsymmetry

    val dominant = when {
        mlLeft == null || mlRight == null -> null
        mlLeft > mlRight -> "Bal oldalon nagyobb az oldalirányú bizonytalanság"
        mlRight > mlLeft -> "Jobb oldalon nagyobb az oldalirányú bizonytalanság"
        else -> "Tökéletesen szimmetrikus oldalirányú stabilitás"
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Sensoria – talpi elemzés", color = Color.White,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("A három talpi szenzor relatív jelmegoszlása és a becsült jelközéppont (CoP) mozgása.",
                color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(18.dp))

            Text("Sarok–előláb jelmegoszlás", fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(10.dp))
            SockForefootBar("Bal láb", leftForefoot)
            Spacer(Modifier.height(12.dp))
            SockForefootBar("Jobb láb", rightForefoot)

            HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp))

            Text("Oldalirányú (ML) stabilitás és Aszimmetria", fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            if (asymmetry != null) {
                Text("Aszimmetriaindex: ${formatSockPercent(asymmetry)}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
            } else {
                Text("Az aszimmetriaindex nem érhető el.", color = Color.LightGray)
            }
            dominant?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Mit jelentenek ezek az értékek?\n" +
                        "• ML (Mediolateral) változékonyság: Az oldalirányú (jobb-bal) bizonytalanságot és a boka finom korrekciós mozgásait mutatja.\n" +
                        "• Aszimmetriaindex: Azt jelzi, hogy az egyik lábnak mennyivel kell többet dolgoznia az egyensúly megtartásáért. (Ez nem a testsúly eloszlása, hanem a stabilitási munka különbsége!) A 0% a tökéletes szimmetria.",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp))
            Text("CoP-proxy pálya a talpon", fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FootCopPlot("Bal láb", sock.left_cop_series.orEmpty(), true,
                    Color(0xFF64B5F6), Modifier.weight(1f))
                FootCopPlot("Jobb láb", sock.right_cop_series.orEmpty(), false,
                    Color(0xFFEF9A9A), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text("A fenti grafikon a relatív nyomásközéppont vándorlását mutatja a mérés alatt. " +
                    "A kiterjedtebb firka bizonytalanabb tartást jelent.",
                color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SockForefootBar(title: String, fraction: Double?) {
    val proportion = fraction?.coerceIn(0.0, 1.0)?.toFloat()
    Text(title, color = Color.White, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    if (proportion == null) {
        Text("Nincs adat", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    } else {
        Row(Modifier.fillMaxWidth().height(12.dp)) {
            Box(Modifier.fillMaxHeight().weight(proportion.coerceAtLeast(0.001f))
                .background(Color(0xFF64B5F6), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)))
            Box(Modifier.fillMaxHeight().weight((1f - proportion).coerceAtLeast(0.001f))
                .background(Color(0xFFFFCC80), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)))
        }
        Spacer(Modifier.height(4.dp))
        Text("Előláb: ${formatSockPercent(proportion.toDouble())}    Sarok: ${formatSockPercent(1.0 - proportion)}",
            color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
private fun FootCopPlot(
    title: String,
    points: List<CopPoint>,
    isLeftFoot: Boolean,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))

        // A Canvas visszatért az eredeti 220 dp-s magasságra
        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            val w = size.width
            val h = size.height

            // 1. Cipőtalp és szenzorháromszög megrajzolása normál méretben
            val footprint = Path().apply {
                moveTo(.50f*w, .95f*h)
                cubicTo(.31f*w, .96f*h, .22f*w, .81f*h, .29f*w, .64f*h)
                cubicTo(.32f*w, .50f*h, .20f*w, .37f*h, .23f*w, .20f*h)
                cubicTo(.25f*w, .06f*h, .40f*w, .06f*h, .50f*w, .07f*h)
                cubicTo(.69f*w, .04f*h, .79f*w, .12f*h, .77f*w, .28f*h)
                cubicTo(.76f*w, .44f*h, .67f*w, .52f*h, .71f*w, .65f*h)
                cubicTo(.78f*w, .83f*h, .65f*w, .96f*h, .50f*w, .95f*h)
                close()
            }
            drawPath(footprint, Color.LightGray.copy(alpha = .13f))
            drawPath(footprint, Color.LightGray.copy(alpha = .65f),
                style = Stroke(width = 1.5.dp.toPx()))

            val mirror = if (isLeftFoot) -1f else 1f
            fun map(x: Float, y: Float): Offset = Offset(
                w * (.5f + mirror*x*.48f),
                h * (.82f - y*.58f)
            )
            val heel = map(0f, 0f)
            val met1 = map(-.5f, 1f)
            val met5 = map(.5f, 1f)

            val triangle = Path().apply {
                moveTo(heel.x, heel.y)
                lineTo(met1.x, met1.y)
                lineTo(met5.x, met5.y)
                close()
            }
            drawPath(triangle, Color.Gray.copy(alpha = .25f),
                style = Stroke(width = 1.dp.toPx()))
            listOf(heel, met1, met5).forEach { sensor ->
                drawCircle(Color.White.copy(alpha = .7f), radius = 3.8.dp.toPx(), center = sensor)
            }

            // 2. Csak a CoP pontokat "fújjuk fel" a saját középpontjukhoz képest
            val valid = points.filter { it.x.isFinite() && it.y.isFinite() }
            if (valid.isNotEmpty()) {

                // Kiszámoljuk a pálya saját középpontját (átlag X és Y)
                val avgX = valid.map { it.x.toFloat() }.average().toFloat()
                val avgY = valid.map { it.y.toFloat() }.average().toFloat()

                // Milyen szorzóval nagyítsuk fel a kilengéseket (Pl. 3.5-szörösére)
                val amplification = 3.5f

                val trace = Path()
                valid.forEachIndexed { index, point ->
                    // Felnagyítjuk a pont távolságát az átlagtól
                    val expandedX = avgX + (point.x.toFloat() - avgX) * amplification
                    val expandedY = avgY + (point.y.toFloat() - avgY) * amplification

                    val xy = map(expandedX, expandedY)

                    if (index == 0 || point.time_s - valid[index - 1].time_s > 0.10) {
                        trace.moveTo(xy.x, xy.y)
                    } else trace.lineTo(xy.x, xy.y)
                }

                drawPath(trace, lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round,
                        join = StrokeJoin.Round))

                // A legutolsó pontra rajzolt pöttyöt is ugyanígy kiszámítjuk
                val lastX = avgX + (valid.last().x.toFloat() - avgX) * amplification
                val lastY = avgY + (valid.last().y.toFloat() - avgY) * amplification
                drawCircle(lineColor, radius = 4.dp.toPx(),
                    center = map(lastX, lastY))
            }
        }
        if (points.isEmpty()) Text("Nincs pályaadat", color = Color.Gray, fontSize = 11.sp)
    }
}

private fun formatSockPercent(value: Double): String =
    "${(value.coerceIn(0.0, 1.0) * 1000.0).roundToInt() / 10.0}%"