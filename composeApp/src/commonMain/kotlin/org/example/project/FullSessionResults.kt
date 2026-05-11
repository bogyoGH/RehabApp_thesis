package org.example.project

// SessionAnalyzer.kt vagy külön fájl
data class FullSessionResult(
    val taskResults: Map<String, Any>,
    val rawTaskData: Map<String, List<SensoriaData>>, // Grafikonokhoz szükséges nyers adatok
    val fatigueIndex: Double,
    val globalFogEvents: Int
)