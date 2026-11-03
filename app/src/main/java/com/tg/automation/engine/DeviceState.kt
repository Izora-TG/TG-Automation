package com.tg.automation.engine

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** Liest den aktuellen Gerätezustand – ohne Dauerabfrage, nur bei Bedarf. */
object DeviceState {
    fun battery(ctx: Context): Int =
        ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    fun isCharging(ctx: Context): Boolean = ctx.getSystemService(BatteryManager::class.java).isCharging

    fun isScreenOn(ctx: Context): Boolean = ctx.getSystemService(PowerManager::class.java).isInteractive

    fun isWifiConnected(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@any false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    fun isHeadphone(d: AudioDeviceInfo): Boolean {
        val t = d.type
        if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_USB_HEADSET
        ) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && t == AudioDeviceInfo.TYPE_BLE_HEADSET
    }

    fun headphonesConnected(ctx: Context): Boolean =
        ctx.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { isHeadphone(it) }

    fun ringerMode(ctx: Context): String = when (ctx.getSystemService(AudioManager::class.java).ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> "silent"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        else -> "normal"
    }
}
