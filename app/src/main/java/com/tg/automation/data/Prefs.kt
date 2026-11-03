package com.tg.automation.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _master = MutableStateFlow(true)
    val master: StateFlow<Boolean> = _master
    var onMasterChanged: (() -> Unit)? = null

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _master.value = sp.getBoolean("master", true)
    }

    val masterEnabled: Boolean get() = _master.value

    fun setMaster(enabled: Boolean) {
        if (_master.value == enabled) return
        _master.value = enabled
        sp.edit().putBoolean("master", enabled).apply()
        onMasterChanged?.invoke()
    }
}
