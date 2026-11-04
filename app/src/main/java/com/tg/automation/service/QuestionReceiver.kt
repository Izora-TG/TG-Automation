package com.tg.automation.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.tg.automation.engine.Questions

/** Antworten aus der Benachrichtigung (Knöpfe, Direktantwort, Wegwischen). */
class QuestionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val question = Questions.get(id)
        if (question == null) {
            if (!intent.getBooleanExtra(EXTRA_CANCEL, false)) {
                Toast.makeText(context, "Diese Frage ist nicht mehr aktiv", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (intent.getBooleanExtra(EXTRA_CANCEL, false)) {
            Questions.answer(context, id, null)
            return
        }
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Questions.KEY_REPLY)?.toString()
        val value = reply ?: intent.getStringExtra(EXTRA_VALUE)
        if (value == null) return
        if (question.type == "ask_number") {
            val number = Questions.parseNumber(value)
            if (number == null) {
                Questions.notify(context, question, "„$value“ ist keine Zahl – bitte nochmal.")
                return
            }
            Questions.answer(context, id, number)
        } else {
            Questions.answer(context, id, value)
        }
    }

    companion object {
        const val ACTION = "com.tg.automation.ANSWER"
        const val EXTRA_ID = "id"
        const val EXTRA_VALUE = "value"
        const val EXTRA_CANCEL = "cancel"
    }
}
