package com.tg.automation.data

import android.content.Context
import android.util.Log
import com.tg.automation.model.Macro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Speichert die Makros als JSON-Datei im privaten App-Speicher. */
object MacroRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val serializer = ListSerializer(Macro.serializer())
    private lateinit var file: File
    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros

    /** Wird aufgerufen, wenn sich Makros so ändern, dass Alarme/Dienst angepasst werden müssen. */
    var onStructureChanged: (() -> Unit)? = null

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "macros.json")
        val loaded = try {
            if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
        } catch (e: Exception) {
            Log.e("MacroRepository", "Konnte Makros nicht laden", e)
            emptyList()
        }
        _macros.value = loaded
        // Alte Listen-Makros in Blöcke umwandeln.
        if (loaded.any { it.needsMigration() }) {
            update { list -> list.map { if (it.needsMigration()) it.migrated() else it } }
        }
    }

    fun get(id: String): Macro? = _macros.value.firstOrNull { it.id == id }

    fun upsert(macro: Macro) {
        update { list ->
            val i = list.indexOfFirst { it.id == macro.id }
            if (i >= 0) list.toMutableList().also { it[i] = macro } else list + macro
        }
        onStructureChanged?.invoke()
    }

    fun delete(id: String) {
        update { list -> list.filterNot { it.id == id } }
        onStructureChanged?.invoke()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
        onStructureChanged?.invoke()
    }

    fun markRun(id: String, time: Long = System.currentTimeMillis()) {
        update { list -> list.map { if (it.id == id) it.copy(lastRun = time) else it } }
    }

    @Synchronized
    private fun update(block: (List<Macro>) -> List<Macro>) {
        val next = block(_macros.value)
        _macros.value = next
        try {
            val tmp = File(file.parentFile, "macros.json.tmp")
            tmp.writeText(json.encodeToString(serializer, next))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e("MacroRepository", "Konnte Makros nicht speichern", e)
        }
    }
}
