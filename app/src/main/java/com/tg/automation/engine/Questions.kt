package com.tg.automation.engine

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.tg.automation.App
import com.tg.automation.R
import com.tg.automation.service.ControlService
import com.tg.automation.service.QuestionReceiver
import com.tg.automation.ui.QuestionActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Eine offene Frage an die nutzende Person. */
data class Question(
    val id: Int,
    val type: String,
    val title: String,
    val text: String,
    val params: Map<String, String>,
) {
    val options: List<String>
        get() = params["options"].orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Zeigt Abfragen als Dialog (wenn Android das Öffnen erlaubt) und immer zusätzlich als
 * Benachrichtigung mit Antwort-Knöpfen. Die Makro-Ausführung wartet, bis eine Antwort kommt.
 */
object Questions {
    const val NOTIFICATION_TAG = "question"
    const val KEY_REPLY = "reply"
    private val counter = AtomicInteger(1000)
    private val pending = ConcurrentHashMap<Int, Pair<Question, CompletableDeferred<String?>>>()
    private val _open = MutableStateFlow<Set<Int>>(emptySet())

    /** IDs aller noch offenen Fragen – der Dialog schließt sich, wenn seine ID verschwindet. */
    val open: StateFlow<Set<Int>> = _open

    fun get(id: Int): Question? = pending[id]?.first

    /** Antwort setzen; null = abgebrochen. Gibt false zurück, wenn die Frage nicht mehr offen ist. */
    fun answer(ctx: Context, id: Int, value: String?): Boolean {
        val entry = pending.remove(id) ?: return false
        _open.update { it - id }
        NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_TAG, id)
        entry.second.complete(value)
        return true
    }

    /** Stellt die Frage und wartet auf die Antwort (null bei Abbruch oder Zeitüberschreitung). */
    suspend fun ask(ctx: Context, type: String, title: String, text: String, params: Map<String, String>, timeoutSec: Int): String? {
        val q = Question(counter.incrementAndGet(), type, title, text, params)
        val deferred = CompletableDeferred<String?>()
        pending[q.id] = q to deferred
        _open.update { it + q.id }
        try {
            notify(ctx, q)
            withContext(Dispatchers.Main) { openDialog(ctx, q) }
            return if (timeoutSec > 0) {
                withTimeoutOrNull(timeoutSec * 1000L) { deferred.await() }
            } else {
                deferred.await()
            }
        } finally {
            if (pending.remove(q.id) != null) {
                _open.update { it - q.id }
                NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_TAG, q.id)
            }
        }
    }

    private fun openDialog(ctx: Context, q: Question) {
        val intent = QuestionActivity.intent(ctx, q.id)
        try {
            // Mit aktiver Bedienungshilfe darf Android den Dialog auch aus dem Hintergrund öffnen.
            (ControlService.instance ?: ctx).startActivity(intent)
        } catch (e: Exception) {
            // Dann bleibt die Benachrichtigung als Weg zum Antworten.
        }
    }

    fun notify(ctx: Context, q: Question, hint: String? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, q.id, QuestionActivity.intent(ctx, q.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(ctx, App.CHANNEL_QUESTIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(q.title)
            .setContentText(hint ?: q.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (hint != null) "$hint\n${q.text}" else q.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setDeleteIntent(answerIntent(ctx, q.id, null, 1))
            .setOnlyAlertOnce(hint != null)
        when (q.type) {
            "ask_yes_no" -> {
                b.addAction(0, q.params["yes"].orEmpty().ifBlank { "Ja" }, answerIntent(ctx, q.id, "yes", 2))
                b.addAction(0, q.params["no"].orEmpty().ifBlank { "Nein" }, answerIntent(ctx, q.id, "no", 3))
            }
            "ask_choice" -> q.options.take(3).forEachIndexed { i, o -> b.addAction(0, o, answerIntent(ctx, q.id, o, 10 + i)) }
            "ask_text", "ask_number" -> {
                val input = RemoteInput.Builder(KEY_REPLY)
                    .setLabel(if (q.type == "ask_number") "Zahl eingeben" else "Antwort eingeben")
                    .build()
                val pi = PendingIntent.getBroadcast(
                    ctx, q.id * 100 + 4,
                    Intent(ctx, QuestionReceiver::class.java).setAction(QuestionReceiver.ACTION).putExtra(QuestionReceiver.EXTRA_ID, q.id),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                b.addAction(NotificationCompat.Action.Builder(0, "Antworten", pi).addRemoteInput(input).build())
            }
        }
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_TAG, q.id, b.build())
    }

    private fun answerIntent(ctx: Context, id: Int, value: String?, code: Int): PendingIntent {
        val intent = Intent(ctx, QuestionReceiver::class.java)
            .setAction(QuestionReceiver.ACTION)
            .putExtra(QuestionReceiver.EXTRA_ID, id)
            .putExtra(QuestionReceiver.EXTRA_CANCEL, value == null)
        if (value != null) intent.putExtra(QuestionReceiver.EXTRA_VALUE, value)
        return PendingIntent.getBroadcast(ctx, id * 100 + code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Zahl aus Texteingabe lesen (Komma oder Punkt). */
    fun parseNumber(s: String): String? {
        val d = s.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return if (d == Math.floor(d) && !d.isInfinite() && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()
    }
}
