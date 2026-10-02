package org.example.project.data

import kotlinx.serialization.Serializable

@Serializable
data class Athlete(
    val id: String,
    val age:Int? = null,
    val gender:String? = null,
    val height_cm:Double? = null,
    val weight_kg: Double? = null,
)
