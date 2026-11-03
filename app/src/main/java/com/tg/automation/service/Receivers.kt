package com.tg.automation.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tg.automation.data.MacroRepository
import com.tg.automation.engine.Engine
import com.tg.automation.engine.Scheduler

/** Wird vom AlarmManager für Uhrzeit- und Intervall-Auslöser geweckt. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Scheduler.ACTION) return
        val segments = intent.data?.pathSegments ?: return
        val id = segments.getOrNull(0) ?: return
        val index = segments.getOrNull(1)?.toIntOrNull() ?: return
        val pending = goAsync()
        // Nächsten Termin direkt wieder planen.
        val macro = MacroRepository.get(id)
        val trigger = macro?.triggers?.getOrNull(index)
        if (macro != null && macro.enabled && trigger != null) {
            Scheduler.schedule(context, id, index, trigger)
        }
        Engine.finishWhenDone(pending, listOfNotNull(Engine.fireAlarm(id, index)))
    }
}

/** Nach Neustart, App-Update oder Zeitumstellung alles neu einrichten. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                Scheduler.rescheduleAll(context)
                AutomationService.sync(context)
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                    Engine.finishWhenDone(goAsync(), Engine.dispatch("boot"))
                }
            }
        }
    }
}
