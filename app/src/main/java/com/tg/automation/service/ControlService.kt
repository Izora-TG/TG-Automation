package com.tg.automation.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.tg.automation.engine.Apps
import com.tg.automation.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Bedienungshilfe: erkennt App-Wechsel (nur Paketname, keine Inhalte) und führt
 * Systemaktionen wie Home, Zurück, Sperren, Screenshot sowie Tippen/Wischen aus.
 */
class ControlService : AccessibilityService() {
    private val launchable = HashMap<String, Boolean>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == currentPackage || pkg == "com.android.systemui") return
        // Tastaturen, Overlays usw. ignorieren – nur echte Apps zählen.
        val isApp = launchable.getOrPut(pkg) { packageManager.getLaunchIntentForPackage(pkg) != null }
        if (!isApp) return
        currentPackage = pkg
        if (pkg == packageName) return
        if (Engine.hasTrigger("app_opened")) {
            Engine.dispatch("app_opened", mapOf("app" to pkg, "app_name" to Apps.label(this, pkg)))
        }
    }

    /** Führt eine Geste aus und wartet, bis sie fertig ist. */
    suspend fun dispatch(path: Path, durationMs: Long): Boolean = withContext(Dispatchers.Main) {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        suspendCancellableCoroutine { cont ->
            val started = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null,
            )
            if (!started && cont.isActive) cont.resume(false)
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

        /** Zuletzt erkannte App im Vordergrund. */
        @Volatile
        var currentPackage: String = ""
            private set
    }
}
