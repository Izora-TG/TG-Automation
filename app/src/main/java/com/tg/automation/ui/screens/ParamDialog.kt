package com.tg.automation.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tg.automation.engine.Permissions
import com.tg.automation.engine.ScreenPicker
import com.tg.automation.model.Requirement
import com.tg.automation.service.ControlService
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tg.automation.data.MacroRepository
import com.tg.automation.engine.AppInfo
import com.tg.automation.engine.Apps
import com.tg.automation.engine.Conditions
import com.tg.automation.model.Component
import com.tg.automation.model.ComponentDef
import com.tg.automation.model.Kind
import com.tg.automation.model.Param
import com.tg.automation.model.VARIABLE_HINT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ParamDialog(def: ComponentDef, initial: Component, onDismiss: () -> Unit, onConfirm: (Component) -> Unit) {
    val values = remember {
        mutableStateMapOf<String, String>().apply {
            def.params.forEach { put(it.key, initial.params[it.key] ?: it.default) }
        }
    }
    val hasText = def.params.any { it is Param.Text }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(def.icon, null) },
        title = { Text(def.title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(def.description, style = MaterialTheme.typography.bodySmall)
                def.params.forEach { p ->
                    ParamEditor(p, values[p.key] ?: p.default, onSet = { k, v -> values[k] = v }) { values[p.key] = it }
                }
                if (hasText && (def.kind == Kind.ACTION || def.kind == Kind.QUESTION)) {
                    Text(
                        VARIABLE_HINT,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleaned = def.params.associate { p ->
                    val v = values[p.key] ?: p.default
                    p.key to when (p) {
                        is Param.Number -> (v.toIntOrNull() ?: p.default.toInt()).coerceIn(p.min, p.max).toString()
                        else -> v
                    }
                }
                onConfirm(Component(def.type, cleaned))
            }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParamEditor(p: Param, value: String, onSet: (String, String) -> Unit, onChange: (String) -> Unit) {
    when (p) {
        is Param.Text -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(p.label) },
            placeholder = { Text(p.hint) },
            singleLine = !p.multiline,
            minLines = if (p.multiline) 2 else 1,
            modifier = Modifier.fillMaxWidth(),
        )

        is Param.Number -> OutlinedTextField(
            value = value,
            onValueChange = { s -> onChange(s.filter { it.isDigit() }.take(6)) },
            label = { Text(p.label) },
            suffix = { Text(p.unit) },
            supportingText = { Text("${p.min} – ${p.max}") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        is Param.Slider -> Column {
            Text("${p.label}: $value ${p.unit}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = (value.toFloatOrNull() ?: p.min.toFloat()).coerceIn(p.min.toFloat(), p.max.toFloat()),
                onValueChange = { onChange(it.roundToInt().toString()) },
                valueRange = p.min.toFloat()..p.max.toFloat(),
            )
        }

        is Param.Choice -> Column {
            Text(p.label, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.options.forEach { (key, label) ->
                    FilterChip(selected = value == key, onClick = { onChange(key) }, label = { Text(label) })
                }
            }
        }

        is Param.Time -> TimeField(p.label, value, onChange)

        is Param.Days -> Column {
            Text(p.label, style = MaterialTheme.typography.labelLarge)
            val selected = Conditions.parseDays(value)
            val names = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                names.forEachIndexed { i, name ->
                    val day = i + 1
                    FilterChip(
                        selected = day in selected,
                        onClick = {
                            val next = if (day in selected) selected - day else selected + day
                            if (next.isNotEmpty()) onChange(next.sorted().joinToString(","))
                        },
                        label = { Text(name) },
                    )
                }
            }
        }

        is Param.App -> AppField(p, value, onChange)

        is Param.ScreenPoint -> ScreenField(swipe = false, label = p.label, value = value, onChange = onChange)

        is Param.ScreenSwipe -> ScreenField(swipe = true, label = p.label, value = value) {
            onChange(it)
            onSet("dir", "custom")
        }

        is Param.MacroRef -> Column {
            Text(p.label, style = MaterialTheme.typography.labelLarge)
            val macros = MacroRepository.macros.collectAsState().value
            if (macros.isEmpty()) Text("Noch keine anderen Makros", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                macros.forEach { m ->
                    FilterChip(selected = value == m.id, onClick = { onChange(m.id) }, label = { Text(m.name) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Schedule, null)
        Spacer(Modifier.width(8.dp))
        Text("$label: $value Uhr")
    }
    if (show) {
        val t = Conditions.parseTime(value)
        val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(String.format(Locale.ROOT, "%02d:%02d", state.hour, state.minute))
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun AppField(p: Param.App, value: String, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    val label = if (value.isBlank()) (if (p.optional) "Beliebige App" else "App auswählen …") else Apps.label(ctx, value)
    Column {
        Text(p.label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Apps, null)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
    if (show) {
        AppPickerDialog(
            optional = p.optional,
            onDismiss = { show = false },
            onPick = {
                onChange(it)
                show = false
            },
        )
    }
}

@Composable
private fun AppPickerDialog(optional: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { Apps.launchable(ctx) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App wählen") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Suchen …") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val list = apps
                if (list == null) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                } else {
                    val filtered = list.filter { query.isBlank() || it.label.contains(query, true) }
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        if (optional && query.isBlank()) {
                            item {
                                ListItem(
                                    headlineContent = { Text("Beliebige App") },
                                    modifier = Modifier.clickable { onPick("") },
                                )
                            }
                        }
                        items(filtered, key = { it.pkg }) { app ->
                            ListItem(
                                headlineContent = { Text(app.label) },
                                leadingContent = { AppIcon(app.pkg) },
                                modifier = Modifier.clickable { onPick(app.pkg) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun AppIcon(pkg: String) {
    val ctx = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    val b = bitmap
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = Modifier.size(36.dp))
    } else {
        Row(Modifier.size(36.dp)) {}
    }
}


/** Vorschau + „Auf dem Bildschirm wählen“ für Tipp-Position bzw. Wisch-Strecke. */
@Composable
private fun ScreenField(swipe: Boolean, label: String, value: String, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    var waitingId by remember { mutableStateOf<Int?>(null) }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val serviceOn = remember(resumeTick) { ControlService.instance != null }
    val result by ScreenPicker.result.collectAsState()
    LaunchedEffect(result) {
        val r = result
        if (r != null && r.first == waitingId) {
            onChange(r.second)
            waitingId = null
        }
    }
    val screen = remember { ScreenPicker.realSize(ctx) }
    val pts = ScreenPicker.parse(value)
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Mini-Handy mit Markierung
            Canvas(
                Modifier
                    .height(132.dp)
                    .aspectRatio(screen.x.toFloat() / screen.y.toFloat())
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.5.dp, outline, RoundedCornerShape(10.dp)),
            ) {
                val step = 1f / 10
                for (i in 1..9) {
                    drawLine(outline.copy(alpha = 0.25f), Offset(size.width * i * step, 0f), Offset(size.width * i * step, size.height))
                    drawLine(outline.copy(alpha = 0.25f), Offset(0f, size.height * i * step), Offset(size.width, size.height * i * step))
                }
                if (pts != null) {
                    val a = Offset(pts[0] * size.width, pts[1] * size.height)
                    if (pts.size >= 4) {
                        val b = Offset(pts[2] * size.width, pts[3] * size.height)
                        drawLine(accent, a, b, strokeWidth = 3.dp.toPx())
                        drawCircle(accent, 4.dp.toPx(), a)
                        drawCircle(accent, 6.dp.toPx(), b, style = Stroke(2.dp.toPx()))
                    } else {
                        drawCircle(accent, 7.dp.toPx(), a, style = Stroke(2.dp.toPx()))
                        drawCircle(accent, 2.5.dp.toPx(), a)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ScreenPicker.describe(value), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(
                    enabled = serviceOn,
                    onClick = {
                        val id = ScreenPicker.start(if (swipe) "swipe" else "tap", value)
                        if (id != null) {
                            waitingId = id
                            Toast.makeText(
                                ctx,
                                "Öffne die Ziel-App und tippe oben auf „📍 ${if (swipe) "Wisch-Strecke" else "Position"} wählen“",
                                Toast.LENGTH_LONG,
                            ).show()
                            activity?.moveTaskToBack(true)
                        }
                    },
                ) {
                    Icon(Icons.Default.AdsClick, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Auf dem Bildschirm wählen")
                }
            }
        }
        if (!serviceOn) {
            Text(
                "Zum Auswählen auf dem Bildschirm muss die Bedienungshilfe „TG Automation – Steuerung“ aktiv sein.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = {
                runCatching { ctx.startActivity(Permissions.settingsIntent(ctx, Requirement.ACCESSIBILITY)) }
            }) { Text("Bedienungshilfe öffnen") }
        }
        if (!swipe) {
            // Feinjustierung ohne Overlay
            val x = pts?.getOrNull(0) ?: 0.5f
            val y = pts?.getOrNull(1) ?: 0.5f
            Text("Feinjustieren", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("↔", Modifier.width(24.dp))
                Slider(value = x, onValueChange = { onChange(ScreenPicker.format(listOf(it, y))) }, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("↕", Modifier.width(24.dp))
                Slider(value = y, onValueChange = { onChange(ScreenPicker.format(listOf(x, it))) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
