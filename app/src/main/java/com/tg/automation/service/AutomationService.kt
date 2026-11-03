package com.tg.automation.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.tg.automation.App
import com.tg.automation.R
import com.tg.automation.data.MacroRepository
import com.tg.automation.data.Prefs
import com.tg.automation.engine.DeviceState
import com.tg.automation.engine.Engine
import com.tg.automation.model.Registry
import com.tg.automation.ui.MainActivity

/**
 * Leichter Vordergrund-Dienst, der NUR läuft, wenn ein aktives Makro Systemereignisse braucht
 * (Bildschirm, Ladegerät, WLAN …). Er registriert nur die tatsächlich benötigten Empfänger
 * und wartet passiv – es wird nichts abgefragt, es läuft kein Timer.
 */
class AutomationService : Service() {

    private var receiver: BroadcastReceiver? = null
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null
    private var audioCallback: AudioDeviceCallback? = null
    private var lastBattery: Int? = null
    private var lastCallState: String? = null
    private var lastHeadsetEvent = 0L
    private var cellCallback: ConnectivityManager.NetworkCallback? = null
    private var motion: MotionDetector? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val types = neededTypes()
        goForeground(types)
        if (types.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        register(types)
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterAll()
        super.onDestroy()
    }

    private fun goForeground(types: Set<String>) {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val watching = types.mapNotNull { Registry.def(it)?.title }.sorted().joinToString(", ")
        val n: Notification = NotificationCompat.Builder(this, App.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Automationen aktiv")
            .setContentText(if (watching.isBlank()) "Wartet auf Ereignisse" else "Wartet auf: $watching")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun register(types: Set<String>) {
        unregisterAll()
        val filter = IntentFilter()
        if ("battery_level" in types) filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        if ("power" in types) {
            filter.addAction(Intent.ACTION_POWER_CONNECTED)
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if ("screen" in types) {
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            filter.addAction(Intent.ACTION_USER_PRESENT)
        }
        if ("bluetooth" in types) {
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if ("airplane" in types) filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        if ("call" in types) filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        if ("ringer_changed" in types) filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        if ("dnd_changed" in types) filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        if ("power_save" in types) filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        val needsMotion = "shake" in types || "face_down" in types
        if (needsMotion) {
            // Sensoren nur bei eingeschaltetem Bildschirm – dafür auf an/aus hören.
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
        }

        if (filter.countActions() > 0) {
            val r = EventReceiver()
            ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_EXPORTED)
            receiver = r
        }
        if ("wifi" in types) registerWifi()
        if ("headset" in types) registerAudio()
        if ("mobile_data" in types) registerCellular()
        if (needsMotion) {
            motion = MotionDetector(this).also { if (DeviceState.isScreenOn(this)) it.start() }
        }
    }

    private fun unregisterAll() {
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        wifiCallback?.let { cb ->
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) }
        }
        wifiCallback = null
        audioCallback?.let { cb ->
            runCatching { getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(cb) }
        }
        audioCallback = null
        cellCallback?.let { cb ->
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) }
        }
        cellCallback = null
        motion?.stop()
        motion = null
    }

    private inner class EventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
                    if (level < 0) return
                    val pct = level * 100 / scale
                    val prev = lastBattery
                    lastBattery = pct
                    if (prev != null && prev != pct) {
                        Engine.dispatch("battery_level", mapOf("level" to "$pct", "prev" to "$prev"))
                    }
                }
                Intent.ACTION_POWER_CONNECTED -> Engine.dispatch("power", mapOf("state" to "connected"))
                Intent.ACTION_POWER_DISCONNECTED -> Engine.dispatch("power", mapOf("state" to "disconnected"))
                Intent.ACTION_SCREEN_ON -> {
                    motion?.start()
                    Engine.dispatch("screen", mapOf("state" to "on"))
                }
                Intent.ACTION_SCREEN_OFF -> {
                    motion?.stop()
                    Engine.dispatch("screen", mapOf("state" to "off"))
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION ->
                    Engine.dispatch("ringer_changed", mapOf("mode" to DeviceState.ringerMode(context)))
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED ->
                    Engine.dispatch("dnd_changed", mapOf("state" to if (DeviceState.isDndOn(context)) "on" else "off"))
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                    Engine.dispatch("power_save", mapOf("state" to if (DeviceState.isPowerSave(context)) "on" else "off"))
                Intent.ACTION_USER_PRESENT -> Engine.dispatch("screen", mapOf("state" to "unlocked"))
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    val name = try {
                        device?.name.orEmpty()
                    } catch (e: SecurityException) {
                        ""
                    }
                    val state = if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) "connected" else "disconnected"
                    Engine.dispatch("bluetooth", mapOf("state" to state, "device" to name))
                }
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                    val on = intent.getBooleanExtra("state", false)
                    Engine.dispatch("airplane", mapOf("state" to if (on) "on" else "off"))
                }
                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                    val state = when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                        TelephonyManager.EXTRA_STATE_RINGING -> "ringing"
                        TelephonyManager.EXTRA_STATE_OFFHOOK -> "offhook"
                        TelephonyManager.EXTRA_STATE_IDLE -> "idle"
                        else -> return
                    }
                    if (state == lastCallState) return
                    lastCallState = state
                    Engine.dispatch("call", mapOf("state" to state))
                }
            }
        }
    }

    // ───────────── WLAN ─────────────

    private fun registerWifi() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val cb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WifiCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
        } else {
            WifiCallback()
        }
        try {
            cm.registerNetworkCallback(request, cb)
            wifiCallback = cb
        } catch (e: Exception) {
            Log.w(TAG, "WLAN-Überwachung nicht möglich", e)
        }
    }

    private inner class WifiCallback : ConnectivityManager.NetworkCallback {
        constructor() : super()

        @RequiresApi(Build.VERSION_CODES.S)
        constructor(flags: Int) : super(flags)

        private val connected = HashMap<Network, String>()
        private val registeredAt = SystemClock.elapsedRealtime()

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val ssid = ssidOf(caps)
            val known = connected[network]
            if (known != null) {
                if (known.isEmpty() && ssid.isNotEmpty()) connected[network] = ssid
                return
            }
            connected[network] = ssid
            // Beim Registrieren meldet Android bestehende Verbindungen – die sind kein neues Ereignis.
            if (SystemClock.elapsedRealtime() - registeredAt < 2_000) return
            Engine.dispatch("wifi", mapOf("state" to "connected", "ssid" to ssid))
        }

        override fun onLost(network: Network) {
            val ssid = connected.remove(network) ?: return
            Engine.dispatch("wifi", mapOf("state" to "disconnected", "ssid" to ssid))
        }
    }

    private fun ssidOf(caps: NetworkCapabilities): String {
        var ssid: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ssid = (caps.transportInfo as? WifiInfo)?.ssid
        }
        if ((ssid == null || ssid == UNKNOWN_SSID) && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            ssid = applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
        }
        return ssid?.takeIf { it != UNKNOWN_SSID }?.removeSurrounding("\"").orEmpty()
    }

    // ───────────── Mobile Daten ─────────────

    private fun registerCellular() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val registeredAt = SystemClock.elapsedRealtime()
        val active = HashSet<Network>()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasEmpty = active.isEmpty()
                active.add(network)
                if (wasEmpty && SystemClock.elapsedRealtime() - registeredAt > 2_000) {
                    Engine.dispatch("mobile_data", mapOf("state" to "connected"))
                }
            }

            override fun onLost(network: Network) {
                if (active.remove(network) && active.isEmpty()) {
                    Engine.dispatch("mobile_data", mapOf("state" to "disconnected"))
                }
            }
        }
        try {
            cm.registerNetworkCallback(request, cb)
            cellCallback = cb
        } catch (e: Exception) {
            Log.w(TAG, "Mobilfunk-Überwachung nicht möglich", e)
        }
    }

    // ───────────── Kopfhörer ─────────────

    private fun registerAudio() {
        val am = getSystemService(AudioManager::class.java)
        val known = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { DeviceState.isHeadphone(it) }
            .map { it.id }
            .toMutableSet()
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                val added = addedDevices.orEmpty().filter { DeviceState.isHeadphone(it) && known.add(it.id) }
                // Nur melden, wenn vorher keine Kopfhörer verbunden waren.
                if (added.isNotEmpty() && known.size == added.size) {
                    headsetEvent("connected", added.first().productName?.toString().orEmpty())
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                val removed = removedDevices.orEmpty().filter { known.remove(it.id) }
                if (removed.isNotEmpty() && known.isEmpty()) {
                    headsetEvent("disconnected", removed.first().productName?.toString().orEmpty())
                }
            }
        }
        am.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        audioCallback = cb
    }

    private fun headsetEvent(state: String, device: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHeadsetEvent < 1_500) return
        lastHeadsetEvent = now
        Engine.dispatch("headset", mapOf("state" to state, "device" to device))
    }

    companion object {
        private const val TAG = "AutomationService"
        private const val NOTIFICATION_ID = 1
        private const val UNKNOWN_SSID = "<unknown ssid>"

        fun neededTypes(): Set<String> {
            if (!Prefs.masterEnabled) return emptySet()
            return MacroRepository.macros.value
                .filter { it.enabled }
                .flatMap { it.triggerNodes }
                .map { it.type }
                .filter { Registry.def(it)?.needsService == true }
                .toSet()
        }

        /** Startet oder stoppt den Dienst passend zu den aktiven Makros. */
        fun sync(ctx: Context) {
            val intent = Intent(ctx, AutomationService::class.java)
            if (neededTypes().isEmpty()) {
                ctx.stopService(intent)
                return
            }
            try {
                ContextCompat.startForegroundService(ctx, intent)
            } catch (e: Exception) {
                // Android 12+ erlaubt den Start aus dem Hintergrund nicht immer – beim nächsten Öffnen erneut.
                Log.w(TAG, "Dienst konnte nicht gestartet werden", e)
            }
        }
    }
}
