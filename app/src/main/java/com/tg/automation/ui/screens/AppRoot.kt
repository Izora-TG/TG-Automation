package com.tg.automation.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tg.automation.R
import com.tg.automation.data.LogRepository
import com.tg.automation.data.MacroRepository
import com.tg.automation.data.Prefs
import com.tg.automation.engine.Engine
import com.tg.automation.engine.Permissions
import com.tg.automation.model.Kind
import com.tg.automation.model.Macro
import com.tg.automation.model.Registry
import com.tg.automation.ui.RunMacroActivity
import com.tg.automation.ui.Template
import com.tg.automation.ui.Templates
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class Editing(val macro: Macro, val isNew: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var editing by remember { mutableStateOf<Editing?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showNewSheet by remember { mutableStateOf(false) }

    val current = editing
    if (current != null) {
        BackHandler { editing = null }
        EditorScreen(initial = current.macro, isNew = current.isNew, onClose = { editing = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            0 -> "Makros"
                            1 -> "Protokoll"
                            else -> "Einrichtung"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (tab == 1) {
                        IconButton(onClick = { LogRepository.clear() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Protokoll leeren")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Makros") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.History, null) }, label = { Text("Protokoll") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Security, null) }, label = { Text("Einrichtung") },
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showNewSheet = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Neues Makro") },
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> MacroList(
                    onEdit = { editing = Editing(it, isNew = false) },
                    onNew = { showNewSheet = true },
                    onOpenSetup = { tab = 2 },
                )
                1 -> LogScreen()
                else -> SetupScreen()
            }
        }
    }

    if (showNewSheet) {
        NewMacroSheet(
            onDismiss = { showNewSheet = false },
            onPick = { macro ->
                showNewSheet = false
                editing = Editing(macro, isNew = true)
            },
        )
    }
}

@Composable
private fun MacroList(onEdit: (Macro) -> Unit, onNew: () -> Unit, onOpenSetup: () -> Unit) {
    val ctx = LocalContext.current
    val macros by MacroRepository.macros.collectAsStateWithLifecycle()
    val master by Prefs.master.collectAsStateWithLifecycle()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val missing = remember(macros, resumeTick) { Permissions.missingFor(ctx, macros) }
    var toDelete by remember { mutableStateOf<Macro?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!master) {
            item {
                Banner(
                    icon = Icons.Default.PauseCircle,
                    text = "Alle Automationen sind pausiert.",
                    button = "Fortsetzen",
                    onClick = { Prefs.setMaster(true) },
                )
            }
        }
        if (missing.isNotEmpty()) {
            item {
                Banner(
                    icon = Icons.Default.Warning,
                    text = "${missing.size} Berechtigung(en) fehlen: ${missing.joinToString { it.title }}",
                    button = "Einrichten",
                    onClick = onOpenSetup,
                    error = true,
                )
            }
        }
        if (macros.isEmpty()) {
            item { EmptyState(onNew) }
        }
        items(macros, key = { it.id }) { m ->
            MacroCard(
                macro = m,
                onEdit = { onEdit(m) },
                onToggle = { MacroRepository.setEnabled(m.id, it) },
                onRun = {
                    Engine.runManually(m, "Manuell")
                    Toast.makeText(ctx, "▶ ${m.name}", Toast.LENGTH_SHORT).show()
                },
                onShortcut = { addShortcut(ctx, m) },
                onDuplicate = {
                    MacroRepository.upsert(m.copy(id = UUID.randomUUID().toString(), name = "${m.name} (Kopie)", lastRun = 0).withNewIds())
                },
                onDelete = { toDelete = m },
            )
        }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("„${m.name}“ löschen?") },
            text = { Text("Das Makro wird endgültig entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    MacroRepository.delete(m.id)
                    toDelete = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String, button: String, onClick: () -> Unit, error: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(12.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun EmptyState(onNew: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Bolt, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text("Noch keine Makros", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ein Makro besteht aus „Wenn“ (Auslöser), optional „Nur wenn“ (Bedingungen) und „Dann“ (Aktionen). " +
                "Starte am einfachsten mit einer Vorlage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onNew) {
            Icon(Icons.Default.AutoAwesome, null)
            Spacer(Modifier.width(8.dp))
            Text("Vorlage wählen")
        }
    }
}

@Composable
private fun MacroCard(
    macro: Macro,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onShortcut: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val icon = macro.triggerNodes.firstOrNull()?.let { Registry.def(it.type)?.icon } ?: Icons.Default.Bolt
    ElevatedCard(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(
                        if (macro.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon, null,
                        tint = if (macro.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        macro.name.ifBlank { "Makro" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        summary(macro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = macro.enabled, onCheckedChange = onToggle, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (macro.lastRun > 0) "Zuletzt: ${formatTime(macro.lastRun)}" else "Noch nie ausgeführt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 56.dp),
                )
                IconButton(onClick = onRun) { Icon(Icons.Default.PlayArrow, contentDescription = "Jetzt ausführen") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Mehr") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Auf Homescreen legen") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.AddToHomeScreen, null) },
                            onClick = { menu = false; onShortcut() },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplizieren") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = { menu = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text("Löschen") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

private fun summary(m: Macro): String {
    val triggers = m.triggerNodes.mapNotNull { n -> Registry.def(n.type)?.let { it.summary(n.component).ifBlank { it.title } } }
    val conditions = m.nodes.count { it.kind == Kind.CONSTRAINT || it.kind == Kind.QUESTION }
    val actions = m.nodes.filter { it.kind == Kind.ACTION }.mapNotNull { Registry.def(it.type)?.title }.distinct()
    val cond = if (conditions > 0) " (+$conditions Bed.)" else ""
    return "${triggers.joinToString(" oder ")}$cond → ${actions.joinToString(", ")}"
}

fun formatTime(ms: Long): String = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(ms))

private fun addShortcut(ctx: Context, m: Macro) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
        Toast.makeText(ctx, "Dein Launcher unterstützt keine Verknüpfungen", Toast.LENGTH_LONG).show()
        return
    }
    val intent = Intent(ctx, RunMacroActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .putExtra(RunMacroActivity.EXTRA_ID, m.id)
    val info = ShortcutInfoCompat.Builder(ctx, "macro_${m.id}")
        .setShortLabel(m.name.ifBlank { "Makro" }.take(24))
        .setLongLabel(m.name.ifBlank { "Makro" })
        .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
        .setIntent(intent)
        .build()
    ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewMacroSheet(onDismiss: () -> Unit, onPick: (Macro) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Neues Makro",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Leeres Makro") },
                    supportingContent = { Text("Blöcke selbst auf dem Board verbinden") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                    modifier = Modifier.clickable { onPick(Macro(name = "")) },
                )
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "Vorlagen",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            items(Templates.all) { t: Template ->
                ListItem(
                    headlineContent = { Text(t.title) },
                    supportingContent = { Text(t.description) },
                    leadingContent = { Icon(t.icon, null) },
                    modifier = Modifier.clickable { onPick(t.build()) },
                )
            }
        }
    }
}

