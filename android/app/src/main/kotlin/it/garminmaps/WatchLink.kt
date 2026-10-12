package it.garminmaps

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice

/**
 * Collegamento all'app sull'orologio tramite Garmin Connect (Connect IQ Mobile SDK).
 * Il collegamento e' lento, quindi i messaggi vanno uno alla volta, e un messaggio nuovo con la
 * stessa chiave ("n" navigazione, "p" posizione, "m" mappa) sostituisce quello ancora in coda.
 */
class WatchLink(private val ctx: Context) {
    private val app = IQApp(APP_ID)
    private val ui = Handler(Looper.getMainLooper())
    private var ciq: ConnectIQ? = null
    private var device: IQDevice? = null
    @Volatile var status = "avvio..."
        private set

    private class Item(val key: String, val msg: Map<String, Any>, val onSent: (() -> Unit)?)
    private val queue = ArrayDeque<Item>()
    private var inFlight = false
    private val timeout = Runnable { inFlight = false; pump() }

    fun start() {
        val c = ConnectIQ.getInstance(ctx, ConnectIQ.IQConnectType.WIRELESS)
        ciq = c
        c.initialize(ctx, false, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() { ui.post { pickDevice() } }
            override fun onInitializeError(s: ConnectIQ.IQSdkErrorStatus) { status = "errore SDK: $s" }
            override fun onSdkShutDown() { status = "SDK spento" }
        })
    }

    fun stop() {
        try { ciq?.shutdown(ctx) } catch (_: Exception) { }
        ciq = null
    }

    private fun pickDevice() {
        val c = ciq ?: return
        val all = try { c.knownDevices.orEmpty() } catch (e: Exception) { status = "Garmin Connect non risponde"; return }
        val d = all.firstOrNull { it.friendlyName?.contains("255") == true } ?: all.firstOrNull()
        if (d == null) { status = "nessun orologio in Garmin Connect"; return }
        device = d
        status = "${d.friendlyName}: ${d.status}"
        c.registerForDeviceEvents(d) { dev, st -> status = "${dev.friendlyName}: $st"; ui.post { pump() } }
    }

    fun send(key: String, msg: Map<String, Any>, onSent: (() -> Unit)? = null) {
        ui.post {
            queue.removeAll { it.key == key }
            queue.addLast(Item(key, msg, onSent))
            pump()
        }
    }

    private fun pump() {
        val c = ciq ?: return
        val d = device ?: return
        if (inFlight || queue.isEmpty() || d.status != IQDevice.IQDeviceStatus.CONNECTED) return
        // priorita: navigazione, poi posizione, poi mappa
        val next = listOf("n", "p", "m").firstNotNullOfOrNull { k -> queue.firstOrNull { it.key == k } } ?: queue.first()
        queue.remove(next)
        inFlight = true
        ui.postDelayed(timeout, 12_000)
        try {
            c.sendMessage(d, app, next.msg) { _, _, st ->
                ui.post {
                    ui.removeCallbacks(timeout)
                    inFlight = false
                    if (st == ConnectIQ.IQMessageStatus.SUCCESS) next.onSent?.invoke() else Bridge.log("invio ${next.key}: $st")
                    pump()
                }
            }
        } catch (e: Exception) {
            ui.removeCallbacks(timeout)
            inFlight = false
            Bridge.log("invio ${next.key} fallito: ${e.message}")
        }
    }

    /** Apre l'app sull'orologio se non e' gia' in primo piano. */
    fun openApp() {
        val c = ciq ?: return
        val d = device ?: return
        try { c.openApplication(d, app) { _, _, st -> Bridge.log("apri app: $st") } } catch (_: Exception) { }
    }

    companion object {
        /** Deve coincidere con l'id in watch/manifest.xml */
        const val APP_ID = "8845fc29-fff0-4998-acda-f595d298093d"
    }
}
