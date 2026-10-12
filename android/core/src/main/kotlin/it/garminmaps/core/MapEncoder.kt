package it.garminmaps.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Una linea: lat/lng dei punti. kind: strada minore, strada principale (piu' spessa) o percorso. */
data class Way(val kind: Int, val pts: List<Pair<Double, Double>>) {
    companion object {
        const val MINOR = 0
        const val MAJOR = 1
        const val ROUTE = 2
    }
}

/**
 * Formato compatto per mandare le strade all'orologio (il collegamento Bluetooth fa ~128 B/s).
 * Coordinate in metri rispetto a un punto di riferimento (x = est, y = nord), a passi di 2 m,
 * 12 bit ciascuna = 2 caratteri ('?'..'~', cioe' codice-63 = 6 bit). Una strada = classe ('1'/'0')
 * + 4 caratteri per punto; le linee sono separate da ';'.
 */
object MapEncoder {
    const val STEP_M = 2.0
    const val LIMIT_M = 700.0
    private const val M_PER_DEG = 111_320.0

    fun toLocal(lat: Double, lng: Double, refLat: Double, refLng: Double): Pair<Double, Double> =
        Pair((lng - refLng) * M_PER_DEG * cos(Math.toRadians(refLat)), (lat - refLat) * M_PER_DEG)

    fun distance(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val (x, y) = toLocal(bLat, bLng, aLat, aLng)
        return hypot(x, y)
    }

    /** Douglas-Peucker, tolleranza in metri. */
    fun simplify(pts: List<Pair<Double, Double>>, tol: Double): List<Pair<Double, Double>> {
        if (pts.size < 3) return pts
        val keep = BooleanArray(pts.size).also { it[0] = true; it[pts.lastIndex] = true }
        val stack = ArrayDeque<IntArray>().apply { add(intArrayOf(0, pts.lastIndex)) }
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var worst = -1.0
            var idx = -1
            for (i in a + 1 until b) {
                val d = segDist(pts[i], pts[a], pts[b])
                if (d > worst) { worst = d; idx = i }
            }
            if (worst > tol && idx >= 0) { keep[idx] = true; stack.add(intArrayOf(a, idx)); stack.add(intArrayOf(idx, b)) }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
    }

    /** Distanza in metri del punto p dal segmento a-b (coordinate locali in metri). */
    fun segmentDistance(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double = segDist(p, a, b)

    private fun segDist(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return hypot(p.first - a.first, p.second - a.second)
        val t = (((p.first - a.first) * dx + (p.second - a.second) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(p.first - (a.first + t * dx), p.second - (a.second + t * dy))
    }

    private fun quant(m: Double) = ((m / STEP_M).roundToInt() + 2048).coerceIn(0, 4095)
    private fun ch(v: Int) = (63 + v).toChar()

    /** Spezza la strada nei tratti dentro al riquadro +-LIMIT_M, gia' in metri locali e semplificati. */
    private fun clip(local: List<Pair<Double, Double>>): List<List<Pair<Double, Double>>> {
        val runs = mutableListOf<List<Pair<Double, Double>>>()
        var cur = mutableListOf<Pair<Double, Double>>()
        for (p in local) {
            if (abs(p.first) <= LIMIT_M && abs(p.second) <= LIMIT_M) cur.add(p)
            else { if (cur.size >= 2) runs.add(cur); cur = mutableListOf() }
        }
        if (cur.size >= 2) runs.add(cur)
        return runs
    }

    fun encode(ways: List<Way>, refLat: Double, refLng: Double, maxChars: Int = 1400): String {
        class Item(val kind: Int, val pts: List<Pair<Double, Double>>, val nearest: Double)
        val items = ways.flatMap { w ->
            val local = simplify(w.pts.map { toLocal(it.first, it.second, refLat, refLng) }, if (w.kind == Way.ROUTE) 4.0 else 6.0)
            clip(local).map { run -> Item(w.kind, run, run.minOf { sqrt(it.first * it.first + it.second * it.second) }) }
        }.sortedWith(compareBy({ -it.kind }, { it.nearest }))

        val sb = StringBuilder()
        for (item in items) {
            val s = StringBuilder().append('0' + item.kind)
            for ((x, y) in item.pts) {
                val qx = quant(x)
                val qy = quant(y)
                s.append(ch(qx shr 6)).append(ch(qx and 63)).append(ch(qy shr 6)).append(ch(qy and 63))
            }
            if (item.kind != Way.ROUTE && sb.length + s.length + 1 > maxChars) continue   // il percorso c'e' sempre
            if (sb.isNotEmpty()) sb.append(';')
            sb.append(s)
        }
        return sb.toString()
    }

    /** Inverso di [encode] (usato nei test; l'orologio ha la sua versione in Monkey C). */
    fun decode(s: String): List<Way> = s.split(';').filter { it.length >= 5 }.map { part ->
        val pts = part.substring(1).chunked(4).filter { it.length == 4 }.map { c ->
            val x = ((c[0].code - 63) * 64 + (c[1].code - 63) - 2048) * STEP_M
            val y = ((c[2].code - 63) * 64 + (c[3].code - 63) - 2048) * STEP_M
            Pair(x, y)
        }
        Way(part[0] - '0', pts)
    }
}
