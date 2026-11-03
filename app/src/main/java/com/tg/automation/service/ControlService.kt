package com.tg.automation.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.tg.automation.engine.Apps
import com.tg.automation.engine.Engine

/**
 * Bedienungshilfe: erkennt App-Wechsel (nur Paketname, keine Inhalte) und führt
 * Systemaktionen wie Home, Zurück, Sperren oder Screenshot aus.
 */
class ControlService : AccessibilityService() {
    private var lastPackage = ""
    private val launchable = HashMap<String, Boolean>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == lastPackage || pkg == "com.android.systemui") return
        // Tastaturen, Overlays usw. ignorieren – nur echte Apps zählen.
        val isApp = launchable.getOrPut(pkg) { packageManager.getLaunchIntentForPackage(pkg) != null }
        if (!isApp) return
        lastPackage = pkg
        if (pkg == packageName) return
        if (Engine.hasTrigger("app_opened")) {
            Engine.dispatch("app_opened", mapOf("app" to pkg, "app_name" to Apps.label(this, pkg)))
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: ControlService? = null
            private set
    }
}
