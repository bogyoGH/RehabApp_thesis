package org.example.project

// Statikus eredmények (pl. Állás, Pihenő)
data class StaticAnalysisResult(
    val swayVariability: Double,      // Súlypont vándorlásának mértéke
    val backwardFallRisk: Boolean,    // Hátrafelé dőlés kockázata
    val heelPressurePercentage: Double, // Sarokterhelés átlagos %-a
    val tremorIndex: Double           // Nyugalmi remegés intenzitása
)

// Dinamikus eredmények (pl. Járás)
data class DynamicAnalysisResult(
    val stepCount: Int,               // Lépésszám
    val cadence: Double,              // Lépés/perc (ha tudjuk az időt)
    val heelStrikeQuality: Double,    // Mennyire használja a sarkát (0-100%)
    val amplitudeDropoff: Double      // Fáradás: Amplitúdó csökkenés a feladat végére (%)
)