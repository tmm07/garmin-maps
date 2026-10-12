package it.garminmaps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import it.garminmaps.core.MapEncoder
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Servizio in primo piano: segue il GPS del telefono e manda all'orologio la posizione (ogni ~2 s)
 * e le strade intorno a te (ogni ~150 m). Il calcolo del percorso resta tutto a Google Maps.
 */
class TrackerService : Service(), LocationListener {
    private var link: WatchLink? = null
    private var refLat = 0.0
    private var refLng = 0.0
    private var activeRef = 0          // riferimento che l'orologio ha gia' ricevuto
    private var nextRefId = 0
    private var haveRef = false
    private var fetching = false
    private var heading = 0
    private var lastPosAt = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("track", "Maps per Garmin", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "track")
            .setContentTitle("Maps per Garmin attivo")
            .setContentText("Invio posizione e mappa all'orologio")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(1, n)

        if (link == null) {
            link = WatchLink(applicationContext).also { it.start() }
            Bridge.link = link
        }
        Bridge.running = true
        try {
            getSystemService(LocationManager::class.java)
                .requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this)
        } catch (e: SecurityException) {
            Bridge.log("manca il permesso di posizione")
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        getSystemService(LocationManager::class.java).removeUpdates(this)
        link?.stop()
        link = null
        Bridge.link = null
        Bridge.running = false
        super.onDestroy()
    }

    override fun onLocationChanged(loc: Location) {
        if (loc.hasBearing() && loc.speed > 1.0f) heading = loc.bearing.roundToInt() % 360
        if (!haveRef || (!fetching && MapEncoder.distance(refLat, refLng, loc.latitude, loc.longitude) > 150)) refreshMap(loc)
        if (haveRef) sendPosition(loc)
    }

    private fun sendPosition(loc: Location) {
        val now = System.currentTimeMillis()
        if (now - lastPosAt < 1800) return
        lastPosAt = now
        val (x, y) = MapEncoder.toLocal(loc.latitude, loc.longitude, refLat, refLng)
        link?.send("p", mapOf("k" to "p", "r" to activeRef, "x" to (x * 10).roundToInt(), "y" to (y * 10).roundToInt(), "h" to heading))
    }

    private fun refreshMap(loc: Location) {
        fetching = true
        val lat = loc.latitude
        val lng = loc.longitude
        thread {
            try {
                val enc = MapEncoder.encode(Roads.fetch(lat, lng), lat, lng)
                val id = ++nextRefId
                Bridge.log("mappa #$id: ${enc.length} caratteri")
                // la posizione usa il nuovo riferimento solo dopo che l'orologio ha ricevuto la mappa
                link?.send("m", mapOf("k" to "m", "r" to id, "w" to enc)) {
                    refLat = lat; refLng = lng; activeRef = id; haveRef = true
                }
            } catch (e: Exception) {
                Bridge.log("mappa non scaricata: ${e.message}")
            } finally { fetching = false }
        }
    }

    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}
}
