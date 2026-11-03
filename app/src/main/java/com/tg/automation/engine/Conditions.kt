package com.tg.automation.engine

import android.content.Context
import com.tg.automation.data.VarStore
import com.tg.automation.model.Component
import com.tg.automation.service.ControlService
import com.tg.automation.model.int
import com.tg.automation.model.p
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

object Conditions {
    fun check(ctx: Context, c: Component): Boolean = when (c.type) {
        "time_range" -> inRange(LocalTime.now(), parseTime(c.p("from")), parseTime(c.p("to")))
        "days" -> LocalDate.now().dayOfWeek.value in parseDays(c.p("days"))
        "battery_range" -> DeviceState.battery(ctx) in c.int("min", 0)..c.int("max", 100)
        "charging" -> DeviceState.isCharging(ctx) == (c.p("state") == "yes")
        "screen_state" -> DeviceState.isScreenOn(ctx) == (c.p("state") == "on")
        "wifi_connected" -> DeviceState.isWifiConnected(ctx) == (c.p("state") == "yes")
        "headphones" -> DeviceState.headphonesConnected(ctx) == (c.p("state") == "yes")
        "ringer_mode" -> DeviceState.ringerMode(ctx) == c.p("mode")
        "chance" -> Random.nextInt(100) < c.int("percent", 50)
        "var_compare" -> compareVar(c)
        "current_app" -> ControlService.currentPackage == c.p("app")
        "dnd_active" -> DeviceState.isDndOn(ctx) == (c.p("state") == "yes")
        "power_save_active" -> DeviceState.isPowerSave(ctx) == (c.p("state") == "yes")
        "airplane_active" -> DeviceState.isAirplane(ctx) == (c.p("state") == "yes")
        else -> true
    }

    private fun compareVar(c: Component): Boolean {
        val current = VarStore.get(c.p("name")).orEmpty()
        val expected = Vars.apply(c.p("value"), emptyMap())
        val a = current.replace(',', '.').toDoubleOrNull()
        val b = expected.replace(',', '.').toDoubleOrNull()
        return when (c.p("op")) {
            "ne" -> if (a != null && b != null) a != b else !current.equals(expected, ignoreCase = true)
            "gt" -> a != null && b != null && a > b
            "lt" -> a != null && b != null && a < b
            "contains" -> current.contains(expected, ignoreCase = true)
            else -> if (a != null && b != null) a == b else current.equals(expected, ignoreCase = true)
        }
    }

    fun inRange(now: LocalTime, from: LocalTime, to: LocalTime): Boolean =
        if (from <= to) now >= from && now < to else now >= from || now < to

    fun parseTime(s: String): LocalTime {
        val parts = s.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return LocalTime.of(h, m)
    }

    fun parseDays(s: String): Set<Int> =
        s.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet().ifEmpty { (1..7).toSet() }
}
