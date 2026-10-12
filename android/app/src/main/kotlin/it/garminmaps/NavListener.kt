package it.garminmaps

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import it.garminmaps.core.NavParser

/** Legge la notifica di navigazione di Google Maps e la passa al ponte verso l'orologio. */
class NavListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS_PACKAGE) return
        val n = sbn.notification
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (e.getCharSequence(Notification.EXTRA_TEXT) ?: e.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()
        val sub = e.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val isNav = n.category == Notification.CATEGORY_NAVIGATION
        // Il formato esatto cambia tra versioni di Google Maps: tieni traccia di cio' che arriva.
        Bridge.log("maps cat=${n.category} ongoing=${sbn.isOngoing} t=$title | x=$text | s=$sub")
        val state = NavParser.parse(title, text, sub, isNav) ?: return
        if (!sbn.isOngoing && !isNav) return
        Bridge.onNav(state, sbn.key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS_PACKAGE && sbn.key == Bridge.navKey) Bridge.onNavEnded()
    }

    companion object { const val MAPS_PACKAGE = "com.google.android.apps.maps" }
}
