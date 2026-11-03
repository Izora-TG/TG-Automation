package com.tg.automation.data

import android.content.Context
import android.content.SharedPreferences

/** Dauerhaft gespeicherte Variablen für Makros (z. B. Zähler). */
object VarStore {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("variables", Context.MODE_PRIVATE)
    }

    fun get(name: String): String? = sp.getString(key(name), null)

    @Synchronized
    fun set(name: String, value: String) {
        sp.edit().putString(key(name), value).apply()
    }

    private fun key(name: String) = name.trim().lowercase()
}
