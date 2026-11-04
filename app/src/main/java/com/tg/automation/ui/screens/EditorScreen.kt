package com.tg.automation.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tg.automation.data.MacroRepository
import com.tg.automation.engine.Permissions
import com.tg.automation.model.ComponentDef
import com.tg.automation.model.Kind
import com.tg.automation.model.Link
import com.tg.automation.model.Macro
import com.tg.automation.model.Node
import com.tg.automation.model.PORT_NO
import com.tg.automation.model.PORT_OUT
import com.tg.automation.model.PORT_YES
import com.tg.automation.model.Registry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Maße auf dem Board (in dp)
private const val NODE_W = 176f
private const val NODE_H = 68f
private const val PORT = 24f            // Berührfläche eines Anschlusses
private const val TOP = PORT / 2        // Abstand Block-Oberkante zur Box-Oberkante
private const val WORLD = 3000f
private const val MIN_Y = 56f           // Platz für die Werkzeugleiste über dem Block
private const val ROW = 120f

private val GREEN = Color(0xFF2E9E4F)

private fun portX(port: String): Float = when (port) {
    PORT_YES -> NODE_W * 0.27f
    PORT_NO -> NODE_W * 0.73f
    else -> NODE_W / 2
}

/** Ausgangs-Punkt eines Blocks in dp-Weltkoordinaten. */
private fun outPoint(n: Node, port: String) = Offset(n.x + portX(port), n.y + TOP + NODE_H)
private fun inPoint(n: Node) = Offset(n.x + NODE_W / 2, n.y + TOP)

private fun outPorts(kind: Kind?): List<String> = when (kind) {
    Kind.CONSTRAINT, Kind.QUESTION -> listOf(PORT_YES, PORT_NO)
    null -> emptyList()
    else -> listOf(PORT_OUT)
}

private data class Pending(val from: String, val port: String, val end: Offset) // end in dp
private data class NewFrom(val from: String, val port: String, val at: Offset)    // at in dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(initial: Macro, isNew: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    var macro by remember(initial.id) { mutableStateOf(initial) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var newFrom by remember { mutableStateOf<NewFrom?>(null) }
    var editNodeId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    // Ansicht: Verschiebung in px, Zoomfaktor
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    fun fit() {
        if (viewSize.width == 0 || macro.nodes.isEmpty()) return
        val minX = macro.nodes.minOf { it.x } - 24f
        val minY = macro.nodes.minOf { it.y } - MIN_Y
        val maxX = macro.nodes.maxOf { it.x } + NODE_W + 24f
        val maxY = macro.nodes.maxOf { it.y } + NODE_H + PORT + 24f
        val w = (maxX - minX) * density
        val h = (maxY - minY) * density
        val s = min(viewSize.width / w, (viewSize.height - 100 * density) / h).coerceIn(0.35f, 1.15f)
        scale = s
        offset = Offset(
            (viewSize.width - w * s) / 2 - minX * density * s,
            24 * density - minY * density * s,
        )
    }

    LaunchedEffect(viewSize) { if (viewSize.width > 0) fit() }

    fun update(block: (Macro) -> Macro) {
        macro = block(macro)
    }

    fun moveNode(id: String, dxDp: Float, dyDp: Float) = update { m ->
        m.copy(nodes = m.nodes.map {
            if (it.id == id) {
                it.copy(
                    x = (it.x + dxDp).coerceIn(0f, WORLD - NODE_W),
                    y = (it.y + dyDp).coerceIn(MIN_Y, WORLD - NODE_H - PORT),
                )
            } else it
        })
    }

    fun connect(from: String, port: String, to: String) {
        if (from == to) return
        val target = macro.node(to) ?: return
        if (target.kind == Kind.TRIGGER) {
            Toast.makeText(ctx, "Auslöser haben keinen Eingang", Toast.LENGTH_SHORT).show()
            return
        }
        if (macro.links.any { it.from == from && it.port == port && it.to == to }) return
        update { it.copy(links = it.links + Link(from, port, to)) }
    }

    fun deleteNode(id: String) = update { m ->
        m.copy(nodes = m.nodes.filterNot { it.id == id }, links = m.links.filterNot { it.from == id || it.to == id })
    }

    /** Freien Platz in der Nähe suchen, damit Blöcke nicht übereinander liegen. */
    fun freeSpot(x: Float, y: Float): Offset {
        var px = x
        var tries = 0
        while (tries < 12 && macro.nodes.any { abs(it.x - px) < NODE_W - 8 && abs(it.y - y) < NODE_H }) {
            px += NODE_W + 24f
            tries++
        }
        return Offset(px.coerceIn(0f, WORLD - NODE_W), y.coerceIn(MIN_Y, WORLD - NODE_H - PORT))
    }

    fun viewCenterDp(): Offset = Offset(
        ((viewSize.width / 2f - offset.x) / scale) / density - NODE_W / 2,
        ((viewSize.height / 3f - offset.y) / scale) / density,
    )

    fun addNode(def: ComponentDef) {
        val c = def.create()
        val nf = newFrom
        val sel = selectedId?.let { macro.node(it) }
        val (pos, link) = when {
            nf != null -> freeSpot(nf.at.x - NODE_W / 2, nf.at.y) to (nf.from to nf.port)
            sel != null && def.kind != Kind.TRIGGER -> {
                val port = if (sel.kind?.branches == true) PORT_YES else PORT_OUT
                freeSpot(sel.x, sel.y + ROW) to (sel.id to port)
            }
            else -> {
                val center = viewCenterDp()
                freeSpot(center.x, center.y) to null
            }
        }
        val node = Node(type = def.type, params = c.params, x = pos.x, y = pos.y)
        update { it.copy(nodes = it.nodes + node) }
        if (link != null) connect(link.first, link.second, node.id)
        selectedId = node.id
        newFrom = null
        if (def.params.isNotEmpty()) editNodeId = node.id
    }

    fun autoLayout() {
        val m = macro
        if (m.nodes.isEmpty()) return
        val level = HashMap<String, Int>()
        m.nodes.forEach { level[it.id] = 0 }
        repeat(m.nodes.size) {
            var changed = false
            for (l in m.links) {
                val want = (level[l.from] ?: 0) + 1
                if (want > (level[l.to] ?: 0) && want < m.nodes.size + 1) {
                    level[l.to] = want
                    changed = true
                }
            }
            if (!changed) return@repeat
        }
        val rows = m.nodes.groupBy { level[it.id] ?: 0 }.toSortedMap()
        val widest = rows.values.maxOf { it.size }
        val gap = NODE_W + 28f
        val placed = HashMap<String, Offset>()
        rows.forEach { (lvl, list) ->
            val sorted = list.sortedBy { it.x }
            val startX = 32f + (widest - sorted.size) * gap / 2
            sorted.forEachIndexed { i, n -> placed[n.id] = Offset(startX + i * gap, MIN_Y + 8f + lvl * ROW) }
        }
        update { it.copy(nodes = it.nodes.map { n -> placed[n.id]?.let { p -> n.copy(x = p.x, y = p.y) } ?: n }) }
        fit()
    }

    fun save() {
        val triggers = macro.nodes.count { it.kind == Kind.TRIGGER }
        val actions = macro.nodes.count { it.kind == Kind.ACTION }
        when {
            triggers == 0 -> Toast.makeText(ctx, "Füge mindestens einen Auslöser hinzu", Toast.LENGTH_SHORT).show()
            actions == 0 -> Toast.makeText(ctx, "Füge mindestens eine Aktion hinzu", Toast.LENGTH_SHORT).show()
            else -> {
                val connected = macro.nodes.filter { n ->
                    n.kind == Kind.TRIGGER || macro.links.any { it.to == n.id }
                }.size
                val loose = macro.nodes.size - connected
                if (loose > 0) {
                    Toast.makeText(ctx, "Hinweis: $loose Block/Blöcke ohne Verbindung werden nie ausgeführt", Toast.LENGTH_LONG).show()
                }
                val name = macro.name.trim().ifBlank {
                    macro.triggerNodes.firstNotNullOfOrNull { Registry.def(it.type)?.title } ?: "Makro"
                }
                MacroRepository.upsert(macro.copy(name = name))
                onClose()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        Modifier.clickable { renameOpen = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            macro.name.ifBlank { if (isNew) "Neues Makro" else "Makro" },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Edit, contentDescription = "Umbenennen", Modifier.size(18.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Abbrechen") }
                },
                actions = {
                    IconButton(onClick = { autoLayout() }) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Automatisch anordnen")
                    }
                    IconButton(onClick = { fit() }) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "Alles zeigen")
                    }
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Löschen")
                        }
                    }
                    TextButton(onClick = { save() }) { Text("Speichern", fontWeight = FontWeight.SemiBold) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { newFrom = null; pickerOpen = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(if (selectedId != null) "Block anhängen" else "Block") },
            )
        },
    ) { pad ->
        val gridColor = MaterialTheme.colorScheme.outlineVariant
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .onSizeChanged { viewSize = it }
                .drawBehind {
                    // Punktraster
                    val step = 24f * density * scale
                    if (step >= 10f) {
                        val pts = ArrayList<Offset>()
                        var x = offset.x % step
                        if (x < 0) x += step
                        while (x < size.width) {
                            var y = offset.y % step
                            if (y < 0) y += step
                            while (y < size.height) {
                                pts += Offset(x, y)
                                y += step
                            }
                            x += step
                        }
                        drawPoints(pts, PointMode.Points, gridColor, strokeWidth = 2.5f * density * scale.coerceAtMost(1f), cap = StrokeCap.Round)
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.35f, 2f)
                        offset = centroid - (centroid - offset) * (newScale / scale) + pan
                        scale = newScale
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { selectedId = null }) },
        ) {
            Board(
                macro = macro,
                selectedId = selectedId,
                pending = pending,
                resumeTick = resumeTick,
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(WORLD.dp)
                    .graphicsLayer {
                        translationX = offset.x
                        translationY = offset.y
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
                onSelect = { selectedId = it },
                onEdit = { id ->
                    val n = macro.node(id)
                    if (n != null && Registry.def(n.type)?.params?.isNotEmpty() == true) editNodeId = id
                },
                onMove = { id, dx, dy -> moveNode(id, dx, dy) },
                onPortStart = { id, port ->
                    val n = macro.node(id) ?: return@Board
                    selectedId = id
                    pending = Pending(id, port, outPoint(n, port))
                },
                onPortDrag = { dx, dy -> pending = pending?.let { it.copy(end = it.end + Offset(dx, dy)) } },
                onPortEnd = {
                    val p = pending
                    pending = null
                    if (p != null) {
                        val target = macro.nodes.firstOrNull { n ->
                            n.id != p.from &&
                                Rect(n.x - 12f, n.y, n.x + NODE_W + 12f, n.y + NODE_H + PORT + 12f).contains(p.end)
                        }
                        if (target != null) {
                            connect(p.from, p.port, target.id)
                        } else {
                            val start = macro.node(p.from)?.let { outPoint(it, p.port) }
                            // In freie Fläche gezogen → neuen Block an dieser Stelle anhängen.
                            if (start != null && (p.end - start).getDistance() > 40f) {
                                newFrom = NewFrom(p.from, p.port, p.end)
                                pickerOpen = true
                            }
                        }
                    }
                },
                onRemoveLink = { link -> update { it.copy(links = it.links - link) } },
                onDuplicate = { id ->
                    val n = macro.node(id) ?: return@Board
                    val spot = freeSpot(n.x + 24f, n.y + 24f)
                    val copy = n.copy(id = java.util.UUID.randomUUID().toString(), x = spot.x, y = spot.y)
                    update { it.copy(nodes = it.nodes + copy) }
                    selectedId = copy.id
                },
                onDisconnect = { id -> update { m -> m.copy(links = m.links.filterNot { it.from == id || it.to == id }) } },
                onDelete = { id ->
                    deleteNode(id)
                    selectedId = null
                },
                density = density,
            )

            if (macro.nodes.isEmpty()) {
                EmptyBoardHint(Modifier.align(Alignment.Center))
            } else if (macro.links.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).padding(end = 150.dp),
                ) {
                    Text(
                        "Tipp: Ziehe vom ● unter einem Block auf einen anderen Block, um sie zu verbinden.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }

    if (pickerOpen) {
        val sel = selectedId?.let { macro.node(it) }
        BlockPicker(
            initialKind = when {
                newFrom != null -> Kind.ACTION
                macro.nodes.none { it.kind == Kind.TRIGGER } -> Kind.TRIGGER
                sel != null -> Kind.ACTION
                else -> Kind.TRIGGER
            },
            allowTriggers = newFrom == null,
            onDismiss = { pickerOpen = false; newFrom = null },
            onPick = { def ->
                pickerOpen = false
                addNode(def)
            },
        )
    }

    editNodeId?.let { id ->
        val node = macro.node(id)
        val def = node?.let { Registry.def(it.type) }
        if (node == null || def == null) {
            editNodeId = null
        } else {
            ParamDialog(
                def = def,
                initial = node.component,
                onDismiss = { editNodeId = null },
                onConfirm = { updated ->
                    update { m -> m.copy(nodes = m.nodes.map { if (it.id == id) it.copy(params = updated.params) else it }) }
                    editNodeId = null
                },
            )
        }
    }

    if (renameOpen) {
        var text by remember { mutableStateOf(macro.name) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Name") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    singleLine = true, placeholder = { Text("z. B. Nachtruhe") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    update { it.copy(name = text) }
                    renameOpen = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Abbrechen") } },
        )
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
private fun EmptyBoardHint(modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Leeres Board", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(8.dp))
        Text(
            "1. Tippe auf „+ Block“ und wähle einen Auslöser.\n" +
                "2. Tippe den Block an und dann wieder auf „+“ – der neue Block wird automatisch verbunden.\n" +
                "3. Oder ziehe vom ● unter einem Block auf einen anderen.\n" +
                "Bedingungen und Abfragen haben zwei Ausgänge: Ja und Nein.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Die „Welt“ mit Verbindungslinien und Blöcken. Koordinaten hier sind lokal (vor Zoom). */
@Composable
private fun Board(
    macro: Macro,
    selectedId: String?,
    pending: Pending?,
    resumeTick: Int,
    modifier: Modifier,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onPortStart: (String, String) -> Unit,
    onPortDrag: (Float, Float) -> Unit,
    onPortEnd: () -> Unit,
    onRemoveLink: (Link) -> Unit,
    onDuplicate: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onDelete: (String) -> Unit,
    density: Float,
) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val noColor = MaterialTheme.colorScheme.error
    val pendingColor = MaterialTheme.colorScheme.primary
    Box(modifier) {
        // Linien
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    fun px(o: Offset) = Offset(o.x * density, o.y * density)
                    fun curve(a: Offset, b: Offset): Path {
                        val d = max(50f * density, abs(b.y - a.y) / 2) + if (b.y < a.y) 60f * density else 0f
                        return Path().apply {
                            moveTo(a.x, a.y)
                            cubicTo(a.x, a.y + d, b.x, b.y - d, b.x, b.y)
                        }
                    }
                    val stroke = Stroke(width = 2.5f * density, cap = StrokeCap.Round)
                    for (l in macro.links) {
                        val from = macro.node(l.from) ?: continue
                        val to = macro.node(l.to) ?: continue
                        val a = px(outPoint(from, l.port))
                        val b = px(inPoint(to))
                        val color = when (l.port) {
                            PORT_YES -> GREEN
                            PORT_NO -> noColor
                            else -> lineColor
                        }
                        val highlight = selectedId != null && (l.from == selectedId || l.to == selectedId)
                        drawPath(
                            curve(a, b), color,
                            style = if (highlight) Stroke(width = 4f * density, cap = StrokeCap.Round) else stroke,
                        )
                        // Pfeilspitze
                        val s = 6f * density
                        drawPath(
                            Path().apply {
                                moveTo(b.x - s, b.y - s * 1.4f)
                                lineTo(b.x + s, b.y - s * 1.4f)
                                lineTo(b.x, b.y)
                                close()
                            },
                            color,
                        )
                    }
                    pending?.let { p ->
                        val from = macro.node(p.from) ?: return@let
                        drawPath(
                            curve(px(outPoint(from, p.port)), px(p.end)),
                            pendingColor,
                            style = Stroke(
                                width = 3f * density,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * density, 8f * density)),
                            ),
                        )
                        drawCircle(pendingColor, 7f * density, px(p.end))
                    }
                },
        )

        macro.nodes.forEach { node ->
            NodeBlock(
                node = node,
                selected = node.id == selectedId,
                resumeTick = resumeTick,
                onSelect = { onSelect(node.id) },
                onEdit = { onEdit(node.id) },
                onMove = { dx, dy -> onMove(node.id, dx / density, dy / density) },
                onPortStart = { port -> onPortStart(node.id, port) },
                onPortDrag = { dx, dy -> onPortDrag(dx / density, dy / density) },
                onPortEnd = onPortEnd,
            )
        }

        // Werkzeugleiste + Trenn-Knöpfe am ausgewählten Block
        val sel = selectedId?.let { macro.node(it) }
        if (sel != null) {
            for (l in macro.links.filter { it.from == sel.id || it.to == sel.id }) {
                val from = macro.node(l.from) ?: continue
                val to = macro.node(l.to) ?: continue
                val a = outPoint(from, l.port)
                val b = inPoint(to)
                val d = max(50f, abs(b.y - a.y) / 2) + if (b.y < a.y) 60f else 0f
                // Mittelpunkt der Bézierkurve (t = 0.5)
                val mid = Offset(
                    (a.x + 3 * a.x + 3 * b.x + b.x) / 8f,
                    (a.y + 3 * (a.y + d) + 3 * (b.y - d) + b.y) / 8f,
                )
                Box(
                    Modifier
                        .offset((mid.x - 13f).dp, (mid.y - 13f).dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable { onRemoveLink(l) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, "Verbindung lösen", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.offset(sel.x.dp, (sel.y - 48f).dp),
            ) {
                Row {
                    if (Registry.def(sel.type)?.params?.isNotEmpty() == true) {
                        IconButton(onClick = { onEdit(sel.id) }) { Icon(Icons.Default.Edit, "Bearbeiten") }
                    }
                    IconButton(onClick = { onDuplicate(sel.id) }) { Icon(Icons.Default.ContentCopy, "Duplizieren") }
                    IconButton(onClick = { onDisconnect(sel.id) }) { Icon(Icons.Default.LinkOff, "Alle Verbindungen lösen") }
                    IconButton(onClick = { onDelete(sel.id) }) { Icon(Icons.Default.DeleteOutline, "Block löschen") }
                }
            }
        }
    }
}

@Composable
private fun NodeBlock(
    node: Node,
    selected: Boolean,
    resumeTick: Int,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onPortStart: (String) -> Unit,
    onPortDrag: (Float, Float) -> Unit,
    onPortEnd: () -> Unit,
) {
    val ctx = LocalContext.current
    val def = Registry.def(node.type)
    val kind = def?.kind
    val missing = remember(node.type, node.params, resumeTick) {
        def?.requirements?.invoke(node.component).orEmpty().any { !Permissions.isGranted(ctx, it) }
    }
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val (bg, fg) = when (kind) {
        Kind.TRIGGER -> cs.primaryContainer to cs.onPrimaryContainer
        Kind.CONSTRAINT -> cs.tertiaryContainer to cs.onTertiaryContainer
        Kind.QUESTION -> if (dark) Color(0xFF4A3A00) to Color(0xFFFFE08A) else Color(0xFFFFE9A8) to Color(0xFF3A2D00)
        else -> cs.secondaryContainer to cs.onSecondaryContainer
    }
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val edit by rememberUpdatedState(onEdit)
    val move by rememberUpdatedState(onMove)

    Box(Modifier.offset(node.x.dp, node.y.dp).size(NODE_W.dp, (NODE_H + PORT).dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
            elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 2.dp),
            border = when {
                selected -> BorderStroke(2.5.dp, cs.primary)
                missing -> BorderStroke(2.dp, cs.error)
                else -> null
            },
            modifier = Modifier
                .offset(0.dp, TOP.dp)
                .size(NODE_W.dp, NODE_H.dp)
                .pointerInput(node.id) {
                    detectTapGestures(onTap = { if (currentSelected) edit() else select() })
                }
                .pointerInput(node.id) {
                    detectDragGestures(onDragStart = { select() }) { change, drag ->
                        change.consume()
                        move(drag.x, drag.y)
                    }
                },
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (def != null) Icon(def.icon, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        def?.title ?: node.type,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (missing) Icon(Icons.Default.Warning, "Berechtigung fehlt", Modifier.size(16.dp), tint = cs.error)
                }
                Text(
                    def?.summary?.invoke(node.component).orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    maxLines = if (kind?.branches == true) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (kind?.branches == true) {
                    val labels = def?.portLabels ?: ("Ja" to "Nein")
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Text(labels.first, style = MaterialTheme.typography.labelSmall, color = GREEN, fontWeight = FontWeight.Bold)
                        Text(labels.second, style = MaterialTheme.typography.labelSmall, color = cs.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Eingang (oben)
        if (kind != Kind.TRIGGER) {
            Box(
                Modifier
                    .offset((NODE_W / 2 - 6f).dp, (TOP - 6f).dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(cs.surface)
                    .border(2.dp, cs.outline, CircleShape),
            )
        }

        // Ausgänge (unten) – ziehen zum Verbinden
        for (port in outPorts(kind)) {
            val color = when (port) {
                PORT_YES -> GREEN
                PORT_NO -> cs.error
                else -> cs.primary
            }
            val start by rememberUpdatedState(onPortStart)
            val dragFn by rememberUpdatedState(onPortDrag)
            val end by rememberUpdatedState(onPortEnd)
            Box(
                Modifier
                    .offset((portX(port) - PORT / 2).dp, NODE_H.dp)
                    .size(PORT.dp)
                    .pointerInput(node.id + port) {
                        detectDragGestures(
                            onDragStart = { start(port) },
                            onDragEnd = { end() },
                            onDragCancel = { end() },
                        ) { change, drag ->
                            change.consume()
                            dragFn(drag.x, drag.y)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(color).border(2.dp, cs.surface, CircleShape))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockPicker(
    initialKind: Kind,
    allowTriggers: Boolean,
    onDismiss: () -> Unit,
    onPick: (ComponentDef) -> Unit,
) {
    var kind by remember { mutableStateOf(if (!allowTriggers && initialKind == Kind.TRIGGER) Kind.ACTION else initialKind) }
    var query by remember { mutableStateOf("") }
    val defs = remember(kind, query) {
        val all = if (query.isBlank()) Registry.of(kind) else Registry.all.filter {
            (allowTriggers || it.kind != Kind.TRIGGER) &&
                (it.title.contains(query, true) || it.description.contains(query, true))
        }
        all.groupBy { if (query.isBlank()) it.category else it.kind.plural }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            "Block hinzufügen",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Alle Blöcke durchsuchen …") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (query.isBlank()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Kind.entries.filter { allowTriggers || it != Kind.TRIGGER }.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = k },
                        label = { Text("${k.plural} (${Registry.of(k).size})") },
                    )
                }
            }
        }
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
