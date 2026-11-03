package com.tg.automation

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.tg.automation.data.LogRepository
import com.tg.automation.data.MacroRepository
import com.tg.automation.data.Prefs
import com.tg.automation.data.VarStore
import com.tg.automation.engine.Scheduler
import com.tg.automation.service.AutomationService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        VarStore.init(this)
        MacroRepository.init(this)
        LogRepository.init(this)
        createChannels()
        MacroRepository.onStructureChanged = {
            Scheduler.rescheduleAll(this)
            AutomationService.sync(this)
        }
        Prefs.onMasterChanged = {
            Scheduler.rescheduleAll(this)
            AutomationService.sync(this)
        }
        Scheduler.rescheduleAll(this)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Hintergrund-Dienst", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Zeigt an, dass Automationen auf Ereignisse warten. Kann ausgeblendet werden."
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MACROS, "Makro-Benachrichtigungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Benachrichtigungen, die von deinen Makros angezeigt werden."
            },
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "service"
        const val CHANNEL_MACROS = "macros"
        lateinit var instance: App
            private set
    }
}
