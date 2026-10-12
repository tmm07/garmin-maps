package it.garminmaps

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Schermata unica: permessi, avvio/stop e un registro per capire cosa arriva da Google Maps. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var toggle: Button
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refresh(); h.postDelayed(this, 1000) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        fun button(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }.also { col.addView(it) }
        status = TextView(this).apply { textSize = 16f }.also { col.addView(it) }
        button("1. Consenti accesso alle notifiche") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        button("2. Consenti posizione e notifiche") {
            val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(p.toTypedArray(), 1)
        }
        toggle = button("3. Avvia") {
            val i = Intent(this, TrackerService::class.java)
            if (Bridge.running) stopService(i) else startForegroundService(i)
        }
        log = TextView(this).apply { textSize = 11f; typeface = android.graphics.Typeface.MONOSPACE }
        col.addView(TextView(this).apply { text = "\nRegistro (utile se la navigazione non compare sull'orologio):" })
        col.addView(log)
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() { super.onResume(); h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }

    private fun refresh() {
        val listener = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(ComponentName(this, NavListener::class.java).flattenToString()) == true
        val loc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        status.text = "Notifiche di Google Maps: ${if (listener) "ok" else "DA CONSENTIRE"}\n" +
            "Posizione: ${if (loc) "ok" else "DA CONSENTIRE"}\n" +
            "Servizio: ${if (Bridge.running) "attivo" else "fermo"}\n" +
            "Orologio: ${Bridge.link?.status ?: "-"}\n" +
            "Navigazione Maps: ${if (Bridge.navKey != null) "in corso" else "nessuna"}"
        toggle.text = if (Bridge.running) "3. Ferma" else "3. Avvia"
        log.text = Bridge.logText()
    }
}
