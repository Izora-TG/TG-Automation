package com.tg.automation.engine

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tg.automation.model.Macro
import com.tg.automation.model.Registry
import com.tg.automation.model.Requirement
import com.tg.automation.service.ControlService

object Permissions {

    private fun has(ctx: Context, perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    fun isGranted(ctx: Context, r: Requirement): Boolean = when (r) {
        Requirement.NOTIFICATIONS ->
            Build.VERSION.SDK_INT < 33 || has(ctx, Manifest.permission.POST_NOTIFICATIONS)
        Requirement.EXACT_ALARM ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        Requirement.NOTIFICATION_LISTENER ->
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        Requirement.ACCESSIBILITY -> isAccessibilityEnabled(ctx)
        Requirement.WRITE_SETTINGS -> Settings.System.canWrite(ctx)
        Requirement.DND_ACCESS ->
            ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        Requirement.BLUETOOTH ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || has(ctx, Manifest.permission.BLUETOOTH_CONNECT)
        Requirement.PHONE -> has(ctx, Manifest.permission.READ_PHONE_STATE)
        Requirement.LOCATION ->
            has(ctx, Manifest.permission.ACCESS_FINE_LOCATION) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || has(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        Requirement.BATTERY_OPTIMIZATION ->
            ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val cn = ComponentName(ctx, ControlService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), ignoreCase = true) || it.equals(cn.flattenToShortString(), ignoreCase = true)
        }
    }

    /** Laufzeit-Berechtigungen, die per Dialog angefragt werden (oder null → Einstellungsseite). */
    fun runtimePermissions(ctx: Context, r: Requirement): Array<String>? = when (r) {
        Requirement.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else null
        Requirement.BLUETOOTH ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else null
        Requirement.PHONE -> arrayOf(Manifest.permission.READ_PHONE_STATE)
        Requirement.LOCATION ->
            if (!has(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                null
            }
        else -> null
    }

    fun settingsIntent(ctx: Context, r: Requirement): Intent {
        val pkg = Uri.parse("package:${ctx.packageName}")
        return when (r) {
            Requirement.EXACT_ALARM ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
                } else {
                    appDetails(ctx)
                }
            Requirement.NOTIFICATION_LISTENER -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Requirement.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            Requirement.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)
            Requirement.DND_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            Requirement.BATTERY_OPTIMIZATION -> requestIgnoreBatteryOptimizations(pkg)
            else -> appDetails(ctx)
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations(pkg: Uri) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)

    fun appDetails(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /** Welche Berechtigungen die aktiven Makros brauchen. */
    fun usedBy(macros: List<Macro>): Set<Requirement> =
        macros.filter { it.enabled }
            .flatMap { it.all }
            .flatMap { c -> Registry.def(c.type)?.requirements?.invoke(c).orEmpty() }
            .toSet()

    fun missingFor(ctx: Context, macros: List<Macro>): List<Requirement> =
        usedBy(macros).filterNot { isGranted(ctx, it) }
}
