package org.example.project.data

import kotlinx.serialization.Serializable

@Serializable
data class TrunkAnglePoint(
    val time_s:Double,
    val angle_deg:Double,
    val frame_index:Int

)
