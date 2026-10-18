package org.example.project.data

import kotlinx.serialization.Serializable

@Serializable
data class BiomechanicsResult(
    val session_id: String,
    val osszpontszam: Double? = null,
    val mean_velocity: Double? = null,
    val sway_path: Double? = null,
    val sway_area: Double? = null,
    val torzs_ingadozas_sd: Double? = null,
    val stabilitas_pont: Double? = null,
    val alatamasztas_pont: Double? = null,
    val szimmetria_pont: Double? = null,
    val terhelesi_irany: String? = null,
    val ertekeles: String? = null,
    val kiszamitva: String? = null,
    val torzsszog_idosor: List<TrunkAnglePoint>? = null

)