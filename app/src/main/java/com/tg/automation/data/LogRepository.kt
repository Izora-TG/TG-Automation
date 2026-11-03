package com.tg.automation.data

import android.content.Context
import com.tg.automation.model.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Kleines Protokoll der letzten Ausführungen (max. 200 Einträge). */
object LogRepository {
    private const val MAX = 200
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(LogEntry.serializer())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var file: File
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "log.json")
        _entries.value = try {
            if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(macro: String, message: String, error: Boolean = false) {
        val next = (listOf(LogEntry(System.currentTimeMillis(), macro, message, error)) + _entries.value).take(MAX)
        _entries.value = next
        persist(next)
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        persist(emptyList())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun persist(list: List<LogEntry>) {
        io.launch {
            synchronized(this@LogRepository) {
                // Immer den aktuellen Stand schreiben, damit sich Schreibvorgänge nicht überholen.
                runCatching { file.writeText(json.encodeToString(serializer, _entries.value)) }
            }
        }
    }
}
