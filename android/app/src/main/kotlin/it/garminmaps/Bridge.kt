package it.garminmaps

import android.os.SystemClock
import it.garminmaps.core.NavState

/** Stato condiviso tra il listener delle notifiche, il servizio di tracciamento e la schermata. */
object Bridge {
    @Volatile var link: WatchLink? = null
    @Volatile var running = false
    @Volatile var navKey: String? = null
    private var lastSent: NavState? = null
    private var lastSentAt = 0L
    private val lines = ArrayDeque<String>()

    @Synchronized fun log(s: String) {
        lines.addLast(s.take(160))
        while (lines.size > 14) lines.removeFirst()
    }

    @Synchronized fun logText() = lines.joinToString("\n")

    /** Chiamato dal listener a ogni aggiornamento della notifica di navigazione di Google Maps. */
    @Synchronized fun onNav(state: NavState, key: String) {
        val firstOfTrip = navKey == null
        navKey = key
        val now = SystemClock.elapsedRealtime()
        if (state == lastSent || now - lastSentAt < 700) return
        lastSent = state
        lastSentAt = now
        val l = link ?: return
        if (firstOfTrip) l.openApp()
        l.send("n", mapOf("k" to "n", "d" to state.distance, "i" to state.instruction, "m" to state.maneuver, "e" to state.eta))
    }

    @Synchronized fun onNavEnded() {
        if (navKey == null) return
        navKey = null
        lastSent = null
        link?.send("n", mapOf("k" to "x"))
    }
}
