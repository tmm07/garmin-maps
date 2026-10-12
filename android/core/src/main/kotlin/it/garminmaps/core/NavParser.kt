package it.garminmaps.core

/** Stato di navigazione letto dalla notifica di Google Maps. maneuver: vedi [NavParser.classify]. */
data class NavState(val distance: String, val instruction: String, val maneuver: Int, val eta: String)

object NavParser {
    const val STRAIGHT = 0
    const val LEFT = 1
    const val RIGHT = 2
    const val SLIGHT_LEFT = 3
    const val SLIGHT_RIGHT = 4
    const val SHARP_LEFT = 5
    const val SHARP_RIGHT = 6
    const val UTURN = 7
    const val ROUNDABOUT = 8
    const val ARRIVE = 9
    const val UNKNOWN = 10

    private val DIST = Regex("""\d+(?:[.,]\d+)?\s?(?:km|mi|ft|yd|m)\b""", RegexOption.IGNORE_CASE)
    private val LEADING = Regex("""^(?:tra|fra|in|after)\s+""", RegexOption.IGNORE_CASE)

    /**
     * La notifica di navigazione di Google Maps ha titolo/testo/sottotesto: la distanza puo' stare
     * nel titolo o nel testo, il sottotesto contiene l'orario di arrivo. Se non trova una distanza
     * restituisce null, a meno che [isNavigation] (categoria "navigation") sia true.
     */
    fun parse(title: String?, text: String?, subText: String?, isNavigation: Boolean = false): NavState? {
        val t = title.orEmpty().trim()
        val x = text.orEmpty().trim()
        if (t.isEmpty() && x.isEmpty()) return null
        val m = DIST.find(t) ?: DIST.find(x)
        if (m == null && !isNavigation) return null
        val distance = m?.value?.replace(Regex("\\s+"), " ").orEmpty()
        val rest = listOf(t, x)
            .map { if (m != null) it.replace(m.value, "") else it }
            .map { it.replace(Regex("""^[\s·•,\-–:]+|[\s·•,\-–:]+$"""), "").replace(LEADING, "").trim() }
            .filter { it.isNotEmpty() }
        val instruction = rest.joinToString(" · ")
        return NavState(distance, instruction, classify("$t $x"), subText.orEmpty().trim())
    }

    fun classify(s: String): Int {
        val l = s.lowercase()
        fun has(vararg w: String) = w.any { l.contains(it) }
        return when {
            has("arriv", "destinazione", "destination") -> ARRIVE
            has("inversione", "u-turn", "u turn") -> UTURN
            has("rotatoria", "rotonda", "roundabout") -> ROUNDABOUT
            has("sinistra", "left") -> when {
                has("secca", "sharp") -> SHARP_LEFT
                has("legger", "slight", "mantieni", "keep") -> SLIGHT_LEFT
                else -> LEFT
            }
            has("destra", "right") -> when {
                has("secca", "sharp") -> SHARP_RIGHT
                has("legger", "slight", "mantieni", "keep") -> SLIGHT_RIGHT
                else -> RIGHT
            }
            has("dritto", "straight", "prosegui", "continu", "head", "dirigiti") -> STRAIGHT
            else -> UNKNOWN
        }
    }
}
