package com.tg.automation.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tg.automation.data.MacroRepository
import com.tg.automation.engine.Permissions
import com.tg.automation.model.Component
import com.tg.automation.model.ComponentDef
import com.tg.automation.model.Kind
import com.tg.automation.model.Macro
import com.tg.automation.model.Registry

private data class EditTarget(val kind: Kind, val index: Int, val component: Component)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(initial: Macro, isNew: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var macro by remember(initial.id) { mutableStateOf(initial) }
    var pickerKind by remember { mutableStateOf<Kind?>(null) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    fun save() {
        when {
            macro.triggers.isEmpty() -> Toast.makeText(ctx, "Füge mindestens einen Auslöser hinzu", Toast.LENGTH_SHORT).show()
            macro.actions.isEmpty() -> Toast.makeText(ctx, "Füge mindestens eine Aktion hinzu", Toast.LENGTH_SHORT).show()
            else -> {
                val name = macro.name.trim().ifBlank {
                    macro.triggers.firstNotNullOfOrNull { Registry.def(it.type)?.title } ?: "Makro"
                }
                MacroRepository.upsert(macro.copy(name = name))
                onClose()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Neues Makro" else "Makro bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Abbrechen") }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Löschen")
                        }
                    }
                    TextButton(onClick = { save() }) { Text("Speichern", fontWeight = FontWeight.SemiBold) }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = macro.name,
                    onValueChange = { macro = macro.copy(name = it) },
                    label = { Text("Name") },
                    placeholder = { Text("z. B. Nachtruhe") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            for (kind in Kind.entries) {
                val list = macro.list(kind)
                item(key = "header_$kind") {
                    Column(Modifier.padding(top = 14.dp)) {
                        Text(kind.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            kind.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(list) { i, c ->
                    ComponentRow(
                        component = c,
                        kind = kind,
                        index = i,
                        count = list.size,
                        resumeTick = resumeTick,
                        onClick = {
                            val def = Registry.def(c.type)
                            if (def != null && def.params.isNotEmpty()) editTarget = EditTarget(kind, i, c)
                        },
                        onDelete = { macro = macro.withList(kind, list.filterIndexed { j, _ -> j != i }) },
                        onMove = { delta ->
                            val j = i + delta
                            if (j in list.indices) {
                                val m = list.toMutableList()
                                val tmp = m[i]; m[i] = m[j]; m[j] = tmp
                                macro = macro.withList(kind, m)
                            }
                        },
                    )
                }
                item(key = "add_$kind") {
                    OutlinedButton(onClick = { pickerKind = kind }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(kind.addLabel)
                    }
                }
            }
        }
    }

    pickerKind?.let { kind ->
        ComponentPicker(
            kind = kind,
            onDismiss = { pickerKind = null },
            onPick = { def ->
                pickerKind = null
                val c = def.create()
                if (def.params.isEmpty()) {
                    macro = macro.withList(kind, macro.list(kind) + c)
                } else {
                    editTarget = EditTarget(kind, -1, c)
                }
            },
        )
    }

    editTarget?.let { target ->
        val def = Registry.def(target.component.type)
        if (def != null) {
            ParamDialog(
                def = def,
                initial = target.component,
                onDismiss = { editTarget = null },
                onConfirm = { updated ->
                    val list = macro.list(target.kind)
                    macro = macro.withList(
                        target.kind,
                        if (target.index < 0) list + updated else list.toMutableList().also { it[target.index] = updated },
                    )
                    editTarget = null
                },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Makro löschen?") },
            text = { Text("„${macro.name}“ wird endgültig entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    MacroRepository.delete(macro.id)
                    confirmDelete = false
                    onClose()
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun ComponentRow(
    component: Component,
    kind: Kind,
    index: Int,
    count: Int,
    resumeTick: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val def = Registry.def(component.type)
    val missing = remember(component, resumeTick) {
        def?.requirements?.invoke(component).orEmpty().filterNot { Permissions.isGranted(ctx, it) }
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (def != null) Icon(def.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (if (kind == Kind.ACTION) "${index + 1}. " else "") + (def?.title ?: component.type),
                    style = MaterialTheme.typography.titleSmall,
                )
                val summary = def?.summary?.invoke(component).orEmpty()
                if (summary.isNotBlank()) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (missing.isNotEmpty()) {
                    Text(
                        "⚠ Braucht: ${missing.joinToString { it.title }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (kind == Kind.ACTION && count > 1) {
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Nach oben")
                }
                IconButton(onClick = { onMove(1) }, enabled = index < count - 1) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Nach unten")
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = "Entfernen") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentPicker(kind: Kind, onDismiss: () -> Unit, onPick: (ComponentDef) -> Unit) {
    var query by remember { mutableStateOf("") }
    val defs = remember(kind, query) {
        Registry.of(kind).filter {
            query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true)
        }.groupBy { it.category }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            kind.addLabel,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Suchen …") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            defs.forEach { (category, list) ->
                item(key = "cat_$category") {
                    Text(
                        category,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { it.type }) { def ->
                    ListItem(
                        headlineContent = { Text(def.title) },
                        supportingContent = { Text(def.description) },
                        leadingContent = { Icon(def.icon, null) },
                        modifier = Modifier.clickable { onPick(def) },
                    )
                }
            }
        }
    }
}
