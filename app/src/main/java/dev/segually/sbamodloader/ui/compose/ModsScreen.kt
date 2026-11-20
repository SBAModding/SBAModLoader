package dev.segually.sbamodloader.ui.compose

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.segually.sbamodloader.R

data class ModChip(val label: String, @DrawableRes val icon: Int)

/** Everything one row of the Mods list shows. [key] is the file's path, which identifies the row while it is moved. */
data class ModRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val avatar: String,
    val icon: Bitmap?,
    /** 0 none, 1 from the store and checked, 2 from the store and not checked by the team. */
    val badge: Int,
    val chips: List<ModChip>,
    val enabled: Boolean,
    val orderable: Boolean,
    val hasSettings: Boolean,
    val openable: Boolean,
    /** The file name and what is inside, for developer features; null otherwise. */
    val technical: String?,
)

data class UpdatesCard(val body: String, val working: Boolean, /** 0..1, or null while it is not known. */ val progress: Float?)

data class ModsState(
    val orderHint: String = "",
    val updates: UpdatesCard? = null,
    val problemsBody: String? = null,
    val rows: List<ModRow> = emptyList(),
    val canAddByHand: Boolean = false,
    val fabOpen: Boolean = false,
)

interface ModsActions {
    fun toggle(key: String, on: Boolean)
    fun remove(key: String)
    fun openSettings(key: String)
    fun open(key: String)
    /** Moves a row to another place in the load order while it is being dragged. */
    fun move(from: Int, to: Int)
    fun dragFinished()
    fun updateAll()
    fun reviewProblems()
    fun addByHand()
    fun browse()
    fun setFabOpen(open: Boolean)
}

@Composable
fun ModsScreen(state: ModsState, actions: ModsActions) {
    Column(Modifier.fillMaxWidth()) {
        SbaText(
            stringResource(R.string.mods_order_hint),
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = LocalSbaText.current.rowBody,
        )
        state.updates?.let { UpdatesCardView(it, actions, Modifier.padding(top = 16.dp)) }
        state.problemsBody?.let { ProblemsCardView(it, actions, Modifier.padding(top = 12.dp)) }
        if (state.rows.isEmpty()) {
            SbaText(
                stringResource(R.string.mods_empty),
                Modifier.fillMaxWidth().padding(top = 40.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )
        }
        ReorderableGrid(
            state.rows, maxOf(1, integerResource(R.integer.mods_columns)), actions::move, actions::dragFinished,
            Modifier.padding(top = 20.dp),
        ) { row, index, dragHandle, dragging ->
            ModCard(row, index, dragHandle, dragging, actions)
        }
    }
}

@Composable
private fun UpdatesCardView(card: UpdatesCard, actions: ModsActions, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
    ) {
        Box {
            ShapeBackdrop(Modifier.matchParentSize())
            Column(Modifier.padding(20.dp)) {
                SbaText(
                    stringResource(R.string.updates_mods_title), Modifier.fillMaxWidth().padding(end = 64.dp),
                    color = colors.onPrimaryContainer, style = MaterialTheme.typography.titleLargeEmphasized,
                )
                SbaText(
                    card.body, Modifier.fillMaxWidth().padding(top = 4.dp, end = 64.dp),
                    color = colors.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium,
                )
                if (card.working) {
                    val modifierBar = Modifier.fillMaxWidth().padding(top = 16.dp)
                    if (card.progress == null) LinearWavyProgressIndicator(modifier = modifierBar)
                    else LinearWavyProgressIndicator(progress = { card.progress }, modifier = modifierBar)
                }
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                Button(
                    onClick = actions::updateAll,
                    enabled = !card.working,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp).padding(vertical = 4.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.updates_mods_all), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ProblemsCardView(body: String, actions: ModsActions, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.errorContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(28.dp), tint = colors.onErrorContainer)
                SbaText(
                    stringResource(R.string.problems_title), Modifier.weight(1f).padding(start = 12.dp),
                    color = colors.onErrorContainer, style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
            SbaText(body, Modifier.fillMaxWidth().padding(top = 8.dp), color = colors.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            TonalAction(stringResource(R.string.problems_review), actions::reviewProblems, Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun ModCard(row: ModRow, index: Int, dragHandle: Modifier, dragging: Boolean, actions: ModsActions) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Card(
        onClick = { actions.open(row.key) },
        enabled = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).appear(index).pressable(source)
            .alpha(if (row.enabled) 1f else 0.6f),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
        interactionSource = source,
    ) {
        // A card is only clickable when the mod has a store page.
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.orderable) {
                    Box(dragHandle.size(width = 32.dp, height = 48.dp).semantics { contentDescription = "" }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_drag), stringResource(R.string.mods_drag_handle), Modifier.padding(horizontal = 4.dp), tint = colors.onSurfaceVariant)
                    }
                }
                Avatar(row)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SbaText(
                            row.title, Modifier.weight(1f, fill = false), color = colors.onSurface,
                            style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (row.badge != 0) {
                            Icon(
                                painterResource(if (row.badge == 2) R.drawable.ic_warning else R.drawable.ic_verified),
                                stringResource(R.string.mods_from_store), Modifier.padding(start = 6.dp).size(18.dp),
                                tint = if (row.badge == 2) colors.primary else colors.primary,
                            )
                        }
                    }
                    RowBody(row.subtitle)
                }
                SbaSwitch(row.enabled, { actions.toggle(row.key, it) }, Modifier.padding(start = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(start = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) { row.chips.forEach { Chip(it) } }
                if (row.hasSettings) {
                    IconButton({ actions.openSettings(row.key) }, Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.mod_settings_title), tint = colors.primary)
                    }
                }
                IconButton({ actions.remove(row.key) }, Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.mods_remove), tint = colors.onSurfaceVariant)
                }
            }
            row.technical?.let {
                SbaText(it, Modifier.padding(start = 32.dp, bottom = 8.dp), color = colors.onSurfaceVariant, style = LocalSbaText.current.caption)
            }
        }
    }
}

/** The store's icon when the mod came from the store, otherwise the first letter of its name. */
@Composable
private fun Avatar(row: ModRow) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    if (row.icon != null) {
        Image(
            row.icon.asImageBitmap(), null,
            Modifier.size(56.dp).border(1.5.dp, colors.outlineVariant, RoundedCornerShape(22)).padding(1.dp).clip(RoundedCornerShape(22)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(56.dp).background(colors.primaryContainer, shape).border(1.5.dp, colors.outlineVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            SbaText(row.avatar, color = colors.onPrimaryContainer, style = MaterialTheme.typography.titleLargeEmphasized)
        }
    }
}

/** A small tonal label, e.g. "Native" or "Assets". */
@Composable
private fun Chip(chip: ModChip) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.padding(end = 6.dp).background(colors.secondaryContainer, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(chip.icon), null, Modifier.size(14.dp), tint = colors.onSecondaryContainer)
        Spacer(Modifier.width(4.dp))
        SbaText(chip.label, color = colors.onSecondaryContainer, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * A grid of rows that can be dragged into a new order by their handle, in any direction, like `ItemTouchHelper` did.
 * Only the rows with `orderable` take part.
 */
@Composable
private fun ReorderableGrid(
    rows: List<ModRow>,
    columns: Int,
    onMove: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier,
    content: @Composable (row: ModRow, index: Int, dragHandle: Modifier, dragging: Boolean) -> Unit,
) {
    val origins = remember { mutableStateMapOf<String, Offset>() }
    val sizes = remember { mutableStateMapOf<String, IntSize>() }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    val view = LocalView.current
    val currentRows by androidx.compose.runtime.rememberUpdatedState(rows)
    val gap = if (columns > 1) 6.dp else 0.dp

    Column(modifier.fillMaxWidth()) {
        rows.chunked(columns).forEachIndexed { chunkIndex, chunk ->
            Row(Modifier.fillMaxWidth()) {
                chunk.forEachIndexed { column, row ->
                    val index = chunkIndex * columns + column
                    val dragging = draggedKey == row.key
                    val handle = Modifier.pointerInput(row.key) {
                        detectDragGestures(
                            onDragStart = { local ->
                                val origin = origins[row.key] ?: return@detectDragGestures
                                draggedKey = row.key
                                // Where in the row the finger holds it; the row follows the finger from there.
                                grab = Offset(local.x, local.y)
                                pointer = origin + grab
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                pointer += amount
                                val key = draggedKey ?: return@detectDragGestures
                                val from = currentRows.indexOfFirst { it.key == key }
                                val size = sizes[key] ?: return@detectDragGestures
                                val origin = origins[key] ?: return@detectDragGestures
                                // The row's own place is the one the dragged row's middle is over.
                                val middle = pointer - grab + Offset(size.width / 2f, size.height / 2f)
                                val to = currentRows.indexOfFirst { other ->
                                    other.orderable && other.key != key && Rect(origins[other.key] ?: Offset.Zero,
                                        (sizes[other.key] ?: IntSize.Zero).let { Size(it.width.toFloat(), it.height.toFloat()) }).contains(middle)
                                }
                                if (to >= 0 && from >= 0 && to != from) onMove(from, to)
                            },
                            onDragEnd = { draggedKey = null; onDragEnd() },
                            onDragCancel = { draggedKey = null; onDragEnd() },
                        )
                    }
                    Box(
                        Modifier.weight(1f).padding(horizontal = gap)
                            .onGloballyPositioned { origins[row.key] = it.positionInRoot(); sizes[row.key] = it.size }
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                if (dragging) {
                                    val origin = origins[row.key] ?: Offset.Zero
                                    translationX = pointer.x - grab.x - origin.x
                                    translationY = pointer.y - grab.y - origin.y
                                    scaleX = 1.02f; scaleY = 1.02f
                                    shadowElevation = 12.dp.toPx()
                                }
                            },
                    ) { content(row, index, handle, dragging) }
                }
                // An odd last row keeps its single card at the width of one column.
                if (chunk.size < columns) Spacer(Modifier.weight((columns - chunk.size).toFloat()))
            }
        }
    }
}

/** The floating action menu (add a mod by hand, browse the store), laid over the whole page. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModsFab(state: ModsState, actions: ModsActions) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(state.fabOpen, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { actions.setFabOpen(false) })
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 200.dp),
            horizontalAlignment = Alignment.End,
        ) {
            AnimatedVisibility(state.fabOpen, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 10.dp)) {
                    if (state.canAddByHand) FabItem(stringResource(R.string.mods_add), R.drawable.ic_add) { actions.setFabOpen(false); actions.addByHand() }
                    FabItem(stringResource(R.string.mods_browse), R.drawable.ic_nav_browse) { actions.setFabOpen(false); actions.browse() }
                }
            }
            val source = remember { MutableInteractionSource() }
            LargeFloatingActionButton(
                onClick = { actions.setFabOpen(!state.fabOpen) },
                modifier = Modifier.pressable(source),
                containerColor = if (state.fabOpen) colors.primary else colors.primaryContainer,
                contentColor = if (state.fabOpen) colors.onPrimary else colors.onPrimaryContainer,
                interactionSource = source,
            ) {
                Icon(painterResource(if (state.fabOpen) R.drawable.ic_close else R.drawable.ic_add), stringResource(R.string.mods_actions), Modifier.size(androidx.compose.material3.FloatingActionButtonDefaults.LargeIconSize))
            }
        }
    }
}

@Composable
private fun FabItem(label: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(top = 10.dp).height(64.dp).pressable(source),
        shape = RoundedCornerShape(32.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        interactionSource = source,
    ) {
        Row(Modifier.padding(start = 24.dp, end = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}

/** The Java fragment's way in. */
object ModsScreenHost {
    @JvmStatic
    fun setContent(view: androidx.compose.ui.platform.ComposeView, state: androidx.compose.runtime.State<ModsState>, actions: ModsActions) {
        view.setContent { SbaTheme { ModsScreen(state.value, actions) } }
    }

    @JvmStatic
    fun setFab(view: androidx.compose.ui.platform.ComposeView, state: androidx.compose.runtime.State<ModsState>, actions: ModsActions) {
        view.setContent { SbaTheme { ModsFab(state.value, actions) } }
    }
}
