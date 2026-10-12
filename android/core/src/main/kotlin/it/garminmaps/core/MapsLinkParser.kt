package it.garminmaps.core

import java.net.URLDecoder

/** Estrae le coordinate di un luogo da un link o testo condiviso da Google Maps. */
object MapsLinkParser {
    private const val NUM = """-?\d{1,3}\.\d+"""
    private val PLACE = Regex("!3d($NUM)!4d($NUM)")
    private val PARAM = Regex("""[?&](?:q|query|destination|daddr|ll|center)=($NUM),\s*($NUM)""", RegexOption.IGNORE_CASE)
    private val AT = Regex("@($NUM),($NUM)")
    private val GEO = Regex("geo:($NUM),($NUM)")
    private val URL = Regex("""https?://[^\s]+""")

    fun extractUrl(text: String): String? = URL.find(text)?.value?.trimEnd('.', ',', ')', '"')

    /** Ordine di fiducia: il segnaposto (!3d!4d), poi i parametri, poi il centro della vista (@). */
    fun parse(text: String): Pair<Double, Double>? {
        val t = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
        for (r in listOf(PLACE, PARAM, GEO, AT)) {
            val m = r.find(t) ?: continue
            val lat = m.groupValues[1].toDouble()
            val lng = m.groupValues[2].toDouble()
            if (lat in -90.0..90.0 && lng in -180.0..180.0) return lat to lng
        }
        return null
    }
}
