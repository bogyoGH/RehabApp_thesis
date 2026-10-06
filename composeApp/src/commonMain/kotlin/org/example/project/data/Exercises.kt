package org.example.project.data

import kotlinx.serialization.Serializable

@Serializable
data class Exercise(
    val id: String = "",
    val name: String,
    val category: String? = null,
    val description: String? = null,
    val instructions: String? = null,
    val video_url: String? = "buffer",
    val is_global: Boolean = false,
    val created_by: String? = null,
    val created_at: String? = null, // EZ HIÁNYZOTT
    val is_deleted: Boolean = false // ÉS EZ IS
)