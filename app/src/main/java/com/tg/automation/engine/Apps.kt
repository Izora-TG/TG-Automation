package com.tg.automation.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppInfo(val pkg: String, val label: String)

object Apps {
    private val labels = HashMap<String, String>()

    @Suppress("DEPRECATION")
    fun launchable(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION")
    fun label(ctx: Context, pkg: String): String {
        if (pkg.isBlank()) return ""
        synchronized(labels) { labels[pkg]?.let { return it } }
        val pm = ctx.packageManager
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
        synchronized(labels) { labels[pkg] = label }
        return label
    }
}
