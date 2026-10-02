package org.example.project.data

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
        "Kétlábas állás",
        "Álljon nyugodtan, mindkét lábával a talajon.",
        "Álljon vállszélességű terpeszben.",
        "buffer"
    ),
    ONE_LEG_LEFT(
        "Egylábas állás (Bal)",
        "Álljon a bal lábán, tartsa meg az egyensúlyát.",
        "Egyensúlyozzon a BAL lábán.",
        "oneleg_left"
    ),
    ONE_LEG_RIGHT(
        "Egylábas állás (Jobb)",
        "Álljon a jobb lábán, tartsa meg az egyensúlyát.",
        "Egyensúlyozzon a JOBB lábán.",
        "oneleg_right"
    ),
    TOE_WALKING(
        "Lábujjhegyen járás",
        "Sétáljon lábujjhegyen, ügyelve az egyensúlyára.",
        "Sétáljon LÁBUJJHEGYEN.",
        "toewalk"
    ),
    HEEL_WALKING(
        "Sarkon járás",
        "Sétáljon a sarkán, óvatosan egyensúlyozva.",
        "Sétáljon SARKON.",
        "heelwalk"
    ),
    TANDEM_WALKING(
        "Tandem állás",
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