package it.garminmaps

import it.garminmaps.core.Way
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Scarica da OpenStreetMap (Overpass, gratuito) le strade intorno a un punto. */
object Roads {
    private const val URL = "https://overpass-api.de/api/interpreter"
    private val MAJOR = setOf("motorway", "trunk", "primary", "secondary", "tertiary", "motorway_link", "trunk_link", "primary_link", "secondary_link")
    private const val ALL = "motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|pedestrian|footway|path|cycleway|track|" +
        "motorway_link|trunk_link|primary_link|secondary_link"

    fun fetch(lat: Double, lng: Double, radius: Int = 500): List<Way> {
        val q = "[out:json][timeout:15];way(around:$radius,$lat,$lng)[highway~\"^($ALL)$\"];out geom 400;"
        val conn = (java.net.URL(URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "garmin-maps-watch/1.0")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            conn.outputStream.use { it.write(("data=" + java.net.URLEncoder.encode(q, "UTF-8")).toByteArray()) }
            if (conn.responseCode != 200) error("Overpass ${conn.responseCode}")
            val els = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("elements")
            return (0 until els.length()).mapNotNull { i ->
                val el = els.getJSONObject(i)
                val g = el.optJSONArray("geometry") ?: return@mapNotNull null
                val pts = (0 until g.length()).map { k -> g.getJSONObject(k).let { it.getDouble("lat") to it.getDouble("lon") } }
                if (pts.size < 2) null else Way(el.optJSONObject("tags")?.optString("highway") in MAJOR, pts)
            }
        } finally { conn.disconnect() }
    }
}
