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
import kotlin.math.roundToInt

@Composable
fun ResultsScreen(
    result: FullSessionResult,
    biomechanicsResult: BiomechanicsResult?,
    onBackToMenu: () -> Unit,
    onGoToSavedSessions: () -> Unit
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
            // --- FŐ PONTSZÁM KÁRTYA ---
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

            // --- STABILITÁS KÁRTYA ---
            ScoreCard(
                title = "Stabilitás",
                score = biomechanicsResult.stabilitas_pont,
                onInfoClick = {
                    infoDialogTitle = "Stabilitás"
                    infoDialogText =
                        "A testtartás egyenletességét mutatja. A magasabb érték nyugodtabb, stabilabb tartást, kisebb ingadozást jelent."
                }
            )

            // --- ALÁTÁMASZTÁS KÁRTYA ---
            ScoreCard(
                title = "Alátámasztás",
                score = biomechanicsResult.alatamasztas_pont,
                onInfoClick = {
                    infoDialogTitle = "Alátámasztás"
                    infoDialogText =
                        "Azt mutatja, mennyire stabil a lábak helyzete a talajon/labdán. A magasabb érték azt jelenti, hogy nem volt csúszkálás vagy lelépés a mérés alatt."
                }
            )

            // --- SZIMMETRIA KÁRTYA ---
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

            // --- TERHELÉSI IRÁNY KÁRTYA ---
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

            // --- LENYITHATÓ TECHNIKAI ADATOK (CSAK TÖRZSINGADOZÁS ÉS GRAFIKON) ---
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

                            // Grafikon kirajzolása, ha vannak adatok
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
            // FELTÖLTVE, FELDOLGOZÁSRA VÁR NÉZET
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

        // --- GOMBOK ---
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

    // Extrém értékek keresése a függőleges (Y) skálázáshoz
    val rawMinAngle = data.minOf { it.angle_deg }.toFloat()
    val rawMaxAngle = data.maxOf { it.angle_deg }.toFloat()

    // Hozzáadunk 2 fok paddingot (margót) felül-alul, hogy ne tapadjon a plafonhoz a vonal
    val minAngle = rawMinAngle - 2f
    val maxAngle = rawMaxAngle + 2f
    val angleRange = if (maxAngle == minAngle) 1f else (maxAngle - minAngle)

    // Kiszámoljuk a dinamikus szélességet: 8 dp pontonként, hogy vízszintesen lapozható maradjon
    val minWidth = 300.dp
    val calculatedWidth = (data.size * 3).dp
    val chartWidth = max(minWidth, calculatedWidth)

    // Teljes mérés időtartama (az automatikus X-tengely feliratozáshoz)
    val totalDuration = data.last().time_s - data.first().time_s
    val timeLabelInterval = when {
        totalDuration > 60 -> 1.0 // Ha több mint 1 perc, 10 másodpercenként írjuk ki
        totalDuration > 20 -> 1.0  // Ha több mint 20 mp, 5 másodpercenként
        totalDuration > 10 -> 2.0  // Egyébként 2 mp
        else -> 1.0                // Rövid mérésnél másodpercenként
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
    ) {
        // --- Y TENGELY (RAGADÓS, NEM GÖRDÜL) ---
        Column(
            modifier = Modifier
                .height(200.dp)
                .padding(end = 8.dp, bottom = 24.dp), // A 24.dp helyet hagy az X tengely szövegeinek lent
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(text = "${(maxAngle * 10).roundToInt() / 10f}°", color = textColor, fontSize = 10.sp)
            Text(text = "${(((maxAngle + minAngle) / 2) * 10).roundToInt() / 10f}°", color = textColor, fontSize = 10.sp)
            Text(text = "${(minAngle * 10).roundToInt() / 10f}°", color = textColor, fontSize = 10.sp)
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
                    .height(200.dp) // A teljes doboz magassága
            ) {
                val canvasWidth = size.width
                val xAxisHeight = 24.dp.toPx() // Az alsó sáv, ahova a másodperceket írjuk
                val chartHeight = size.height - xAxisHeight // A tényleges grafikon magassága

                // 1. Háttér rácsvonalak (vízszintes)
                drawLine(color = Color.Gray.copy(alpha = 0.2f), start = Offset(0f, 0f), end = Offset(canvasWidth, 0f))
                drawLine(color = Color.Gray.copy(alpha = 0.2f), start = Offset(0f, chartHeight / 2), end = Offset(canvasWidth, chartHeight / 2))

                // 2. Nulla-vonal megrajzolása, ha a 0 fok beleesik a tartományba
                if (0f in minAngle..maxAngle) {
                    val zeroY = chartHeight - ((0f - minAngle) / angleRange) * chartHeight
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(0f, zeroY),
                        end = Offset(canvasWidth, zeroY),
                        strokeWidth = 2f
                    )
                }

                // 3. X és Y főtengely vonalai a Canvas szélén
                drawLine( // Y tengely vonala
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(0f, 0f),
                    end = Offset(0f, chartHeight),
                    strokeWidth = 3f
                )
                drawLine( // X tengely vonala
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(0f, chartHeight),
                    end = Offset(canvasWidth, chartHeight),
                    strokeWidth = 3f
                )

                // 4. A dőlésszög-görbe megrajzolása
                val path = Path()
                var nextLabelTime = 0.0

                data.forEachIndexed { index, point ->
                    val x = (index.toFloat() / (data.size - 1).coerceAtLeast(1)) * canvasWidth

                    // Normalizálás és invertálás (hogy a magasabb érték feljebb legyen)
                    val normalizedY = (point.angle_deg.toFloat() - minAngle) / angleRange
                    val y = chartHeight - (normalizedY * chartHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }

                    // 5. X-tengely (Idő) beosztások és szövegek rajzolása (pl. 2 másodpercenként)
                    if (point.time_s >= nextLabelTime) {
                        // Kis függőleges vonalka (Tick mark) az X tengelyen
                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(x, chartHeight),
                            end = Offset(x, chartHeight + 10f),
                            strokeWidth = 2f
                        )

                        // Idő felirat megrajzolása (pl. "2s")
                        val text = "${point.time_s.toInt()}s"
                        val textLayout = textMeasurer.measure(
                            text = text,
                            style = TextStyle(color = textColor, fontSize = 10.sp)
                        )
                        // Középre igazítjuk a vonalka alá
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(x - (textLayout.size.width / 2f), chartHeight + 12f)
                        )

                        nextLabelTime += timeLabelInterval
                    }
                }

                // Maga a kék (primary) vonal
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