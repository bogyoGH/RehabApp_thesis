package org.example.project

/**
 * Enum a fix rehabilitációs feladatokhoz.
 * Minden elemhez közvetlenül hozzárendeljük a Python constants.py-ban megadott adatokat.
 */
enum class RehabTaskType(
    val displayName: String,
    val instruction: String,
    val fallback: String,
    val videoName: String
) {
    TWO_LEG_STANDING(
        "Two-leg standing",
        "Stand still with both feet on the ground",
        "Stand with feet shoulder-width apart",
        "buffer"
    ),
    ONE_LEG_LEFT(
        "One-leg standing (Left)",
        "Stand on your left foot, keep balance",
        "Balance on LEFT foot",
        "oneleg_left"
    ),
    ONE_LEG_RIGHT(
        "One-leg standing (Right)",
        "Stand on your right foot, keep balance",
        "Balance on RIGHT foot",
        "oneleg_right"
    ),
    TOE_WALKING(
        "Toe walking",
        "Walk on your toes, maintain stability",
        "Walk on TIPTOES",
        "toewalk"
    ),
    HEEL_WALKING(
        "Heel walking",
        "Walk on your heels, careful with balance",
        "Walk on HEELS",
        "heelwalk"
    ),
    TANDEM_WALKING(
        "Tandem walking",
        "Álljon úgy, hogy az egyik lába sarka érintse a másik lába lábujjait.",
        "Kötéltáncos állás",
        "buffer"
    ),
    MARCHING_IN_PLACE(
        "Helyben járás",
        "Emelje magasra a térdeit ütemesen.",
        "Masírozás helyben",
        "buffer"
    ),
    REST(
        "Pihenő",
        "Lazítson és lélegezzen mélyeket a következő feladat előtt.",
        "Álljon egy helyben",
        "buffer"
    ),
}