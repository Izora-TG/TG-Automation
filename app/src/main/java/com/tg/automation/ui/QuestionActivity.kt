package com.tg.automation.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.tg.automation.engine.Question
import com.tg.automation.engine.Questions
import com.tg.automation.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Durchsichtige Activity, die eine Frage als Dialog zeigt. */
class QuestionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val question = Questions.get(id)
        if (question == null) {
            finish()
            return
        }
        setContent {
            AppTheme {
                val open by Questions.open.collectAsState()
                LaunchedEffect(open) { if (id !in open) finish() }
                QuestionDialog(question) { answer ->
                    Questions.answer(this, id, answer)
                    finish()
                }
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "question_id"

        fun intent(ctx: Context, id: Int): Intent = Intent(ctx, QuestionActivity::class.java)
            .putExtra(EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionDialog(q: Question, onAnswer: (String?) -> Unit) {
    val props = DialogProperties(dismissOnClickOutside = false)
    val cancel = { onAnswer(null) }
    val title: @Composable () -> Unit = { Text(q.title) }
    val icon: @Composable () -> Unit = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) }

    when (q.type) {
        "ask_yes_no" -> AlertDialog(
            onDismissRequest = cancel, properties = props, icon = icon, title = title,
            text = { Text(q.text, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { onAnswer("yes") }) { Text(q.params["yes"].orEmpty().ifBlank { "Ja" }) } },
            dismissButton = { TextButton(onClick = { onAnswer("no") }) { Text(q.params["no"].orEmpty().ifBlank { "Nein" }) } },
        )

        "ask_text", "ask_number" -> {
            val isNumber = q.type == "ask_number"
            var value by remember { mutableStateOf(q.params["default"].orEmpty()) }
            var error by remember { mutableStateOf(false) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            fun submit() {
                if (isNumber) {
                    val n = Questions.parseNumber(value)
                    if (n == null) error = true else onAnswer(n)
                } else {
                    onAnswer(value)
                }
            }
            AlertDialog(
                onDismissRequest = cancel, properties = props, icon = icon, title = title,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(q.text)
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it; error = false },
                            singleLine = isNumber,
                            isError = error,
                            supportingText = if (error) ({ Text("Bitte eine Zahl eingeben") }) else null,
                            keyboardOptions = KeyboardOptions(keyboardType = if (isNumber) KeyboardType.Decimal else KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { submit() }) { Text("OK") } },
                dismissButton = { TextButton(onClick = cancel) { Text("Abbrechen") } },
            )
        }

        "ask_time" -> {
            val now = java.time.LocalTime.now()
            val state = rememberTimePickerState(now.hour, now.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = cancel, properties = props, title = title,
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(q.text)
                        TimePicker(state = state)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onAnswer(String.format(Locale.ROOT, "%02d:%02d", state.hour, state.minute)) }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = cancel) { Text("Abbrechen") } },
            )
        }

        "ask_date" -> {
            val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
            DatePickerDialog(
                onDismissRequest = cancel,
                properties = props,
                confirmButton = {
                    TextButton(onClick = {
                        val ms = state.selectedDateMillis ?: return@TextButton
                        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).apply { timeZone = TimeZone.getTimeZone("UTC") }
                        onAnswer(fmt.format(Date(ms)))
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = cancel) { Text("Abbrechen") } },
            ) {
                DatePicker(
                    state = state,
                    title = { Text(q.text, Modifier.fillMaxWidth().padding(16.dp)) },
                )
            }
        }

        "ask_choice" -> {
            var selected by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = cancel, properties = props, icon = icon, title = title,
                text = {
                    Column {
                        Text(q.text)
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(q.options) { o ->
                                ListItem(
                                    headlineContent = { Text(o) },
                                    leadingContent = { RadioButton(selected = selected == o, onClick = { selected = o }) },
                                    modifier = Modifier.clickable { selected = o },
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selected?.let(onAnswer) }, enabled = selected != null) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = cancel) { Text("Abbrechen") } },
            )
        }

        "ask_slider" -> {
            val min = q.params["min"]?.toIntOrNull() ?: 0
            val max = (q.params["max"]?.toIntOrNull() ?: 100).let { if (it <= min) min + 1 else it }
            var value by remember { mutableFloatStateOf((min + max) / 2f) }
            AlertDialog(
                onDismissRequest = cancel, properties = props, icon = icon, title = title,
                text = {
                    Column {
                        Text(q.text)
                        Text(
                            value.roundToInt().toString(),
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                        Slider(value = value, onValueChange = { value = it }, valueRange = min.toFloat()..max.toFloat())
                    }
                },
                confirmButton = { TextButton(onClick = { onAnswer(value.roundToInt().toString()) }) { Text("OK") } },
                dismissButton = { TextButton(onClick = cancel) { Text("Abbrechen") } },
            )
        }

        else -> LaunchedEffect(Unit) { cancel() }
    }
}
