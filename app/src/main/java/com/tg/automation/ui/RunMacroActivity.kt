package com.tg.automation.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.tg.automation.data.MacroRepository
import com.tg.automation.engine.Engine

/** Unsichtbare Activity für Homescreen-Verknüpfungen: führt ein Makro aus und schließt sich. */
class RunMacroActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val macro = intent.getStringExtra(EXTRA_ID)?.let { MacroRepository.get(it) }
        if (macro == null) {
            Toast.makeText(this, "Makro nicht gefunden", Toast.LENGTH_SHORT).show()
        } else {
            Engine.runManually(macro, "Verknüpfung")
            Toast.makeText(this, "▶ ${macro.name}", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_ID = "macro_id"
    }
}
