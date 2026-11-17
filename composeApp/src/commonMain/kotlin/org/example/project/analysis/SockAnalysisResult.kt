package org.example.project.analysis


import kotlinx.serialization.Serializable

@Serializable
data class SockAnalysisResult(
    val session_id: String,
    val left_metrics: SockMetrics? = null,
    val right_metrics: SockMetrics? = null,
    val combined_metrics: CombinedSockMetrics? = null,
    val left_cop_series: List<CopPoint>? = null,
    val right_cop_series: List<CopPoint>? = null,
    val analysis_status: String = "pending"
)

@Serializable
data class SockMetrics(
    val ml_sd: Double? = null,
    val ap_sd: Double? = null,
    val resultant_sd: Double? = null,
    val path_length: Double? = null,
    val mean_velocity: Double? = null,
    val heel_relative_mean: Double? = null,
    val met_1_relative_mean: Double? = null,
    val met_5_relative_mean: Double? = null
)

@Serializable
data class CombinedSockMetrics(
    val resultant_sd_mean: Double? = null,
    val path_length_mean: Double? = null,
    val mean_velocity_mean: Double? = null,
    val ml_variability_asymmetry: Double? = null,
    val left_forefoot_fraction: Double? = null,
    val right_forefoot_fraction: Double? = null
)

@Serializable
data class CopPoint(
    val time_s: Double,
    val x: Double,
    val y: Double
)