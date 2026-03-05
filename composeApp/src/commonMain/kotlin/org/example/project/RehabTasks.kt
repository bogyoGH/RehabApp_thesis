package org.example.project

/**
 * Enum a fix rehabilitációs feladatokhoz.
 * Minden elemhez közvetlenül hozzárendeljük a Python constants.py-ban megadott adatokat.
 */
enum class RehabTaskType(
    val displayName: String,
    val instruction: String,
    val fallback: String,
    val videoPath: String
) {
    TWO_LEG_STANDING(
        "Two-leg standing",
        "Stand still with both feet on the ground",
        "Stand with feet shoulder-width apart",
        "videos/standing.mp4"
    ),
    ONE_LEG_LEFT(
        "One-leg standing (Left)",
        "Stand on your left foot, keep balance",
        "Balance on LEFT foot",
        "videos/oneleg_left.mp4"
    ),
    ONE_LEG_RIGHT(
        "One-leg standing (Right)",
        "Stand on your right foot, keep balance",
        "Balance on RIGHT foot",
        "videos/oneleg_right.mp4"
    ),
    TOE_WALKING(
        "Toe walking",
        "Walk on your toes, maintain stability",
        "Walk on TIPTOES",
        "videos/toewalk.mp4"
    ),
    HEEL_WALKING(
        "Heel walking",
        "Walk on your heels, careful with balance",
        "Walk on HEELS",
        "videos/heelwalk.mp4"
    ),
    TANDEM_WALKING(
        "Tandem walking",
        "Walk heel-to-toe in a straight line",
        "Heel-to-toe walking",
        "videos/tandem.mp4"
    )
}