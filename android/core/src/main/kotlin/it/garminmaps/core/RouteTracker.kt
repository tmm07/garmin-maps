package it.garminmaps.core

/** Segue l'avanzamento lungo un percorso (lista di lat/lng) e dice di quanto ci si e' allontanati. */
class RouteTracker(private val pts: List<Pair<Double, Double>>) {
    var idx = 0
        private set

    /** Metri dalla linea. Cerca solo in avanti (finestra di 80 segmenti), cosi' i tratti gia' fatti non contano. */
    fun update(lat: Double, lng: Double): Double {
        if (pts.size < 2) return 0.0
        var best = Double.MAX_VALUE
        var bestIdx = idx
        val origin = 0.0 to 0.0
        for (i in idx..minOf(pts.size - 2, idx + 80)) {
            val a = MapEncoder.toLocal(pts[i].first, pts[i].second, lat, lng)
            val b = MapEncoder.toLocal(pts[i + 1].first, pts[i + 1].second, lat, lng)
            val d = MapEncoder.segmentDistance(origin, a, b)
            if (d < best) { best = d; bestIdx = i }
        }
        idx = bestIdx
        return best
    }

    /** Il percorso ancora da fare, a partire dalla posizione attuale. */
    fun remaining(lat: Double, lng: Double): List<Pair<Double, Double>> =
        listOf(lat to lng) + pts.subList(minOf(idx + 1, pts.size), pts.size)
}
