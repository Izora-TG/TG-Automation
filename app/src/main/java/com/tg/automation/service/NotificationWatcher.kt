package com.tg.automation.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tg.automation.engine.Apps
import com.tg.automation.engine.Engine

/** Meldet neue Benachrichtigungen anderer Apps an die Engine (nur wenn ein Makro das braucht). */
class NotificationWatcher : NotificationListenerService() {
    private var lastSignature = ""

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (!Engine.hasTrigger("notification")) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val signature = "${sbn.key}|$title|$text"
        if (signature == lastSignature) return
        lastSignature = signature
        Engine.dispatch(
            "notification",
            mapOf(
                "app" to sbn.packageName,
                "app_name" to Apps.label(this, sbn.packageName),
                "title" to title,
                "text" to text,
            ),
        )
    }
}
