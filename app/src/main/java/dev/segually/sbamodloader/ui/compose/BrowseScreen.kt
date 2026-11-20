package dev.segually.sbamodloader.ui.compose

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.segually.sbamodloader.R
import dev.segually.sbamodloader.browser.ImageLoader
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

// ---- the list ---------------------------------------------------------------------------------------

data class SubChip(val label: String, val checked: Boolean)

/** Search, filters and the count: the part of the list page that sits in the app bar. */
data class BrowseHeader(
    val query: String = "",
    /** 0 all, 1 Fusion, 2 native, 3 packs. */
    val kind: Int = 0,
    /** 0 all, 1 texture packs, 2 mods. */
    val category: Int = 0,
    val subs: List<SubChip> = emptyList(),
    val count: String = "",
    val sortLabel: String = "",
    val sortOptions: List<String> = emptyList(),
    val sortSelected: Int = 0,
)

interface BrowseHeaderActions {
    fun setQuery(text: String)
    fun setKind(kind: Int)
    fun setCategory(category: Int)
    fun toggleSub(index: Int, checked: Boolean)
    fun setSort(index: Int)
}

data class BrowseItem(
    val id: String,
    val avatar: String,
    val avatarColors: Pair<Int, Int>,
    val icon: Bitmap?,
    val name: String,
    val meta: String,
    val verified: Boolean,
    val summary: String,
    val stats: String,
    val selected: Boolean,
)

data class BrowseListState(
    val items: List<BrowseItem> = emptyList(),
    /** A line shown instead of (or above) the list: no results, or the load failure. */
    val message: String? = null,
    val retry: Boolean = false,
)

interface BrowseListActions {
    fun open(id: String)
    fun retry()
}

private val FieldShape = RoundedCornerShape(28.dp)

@Composable
fun BrowseHeaderView(state: BrowseHeader, actions: BrowseHeaderActions) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        TextField(
            value = state.query,
            onValueChange = actions::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = null,
            label = { Text(stringResource(R.string.browse_search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton({ actions.setQuery("") }) { Icon(painterResource(R.drawable.ic_close), null) }
            },
            singleLine = true,
            shape = FieldShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerHigh,
                unfocusedContainerColor = colors.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            ToggleRow(
                listOf(R.string.browse_all, R.string.kind_fusion_plural, R.string.kind_native_plural, R.string.kind_pack_plural).map { stringResource(it) },
                state.kind, actions::setKind,
            )
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            ToggleRow(
                listOf(R.string.browse_all_categories, R.string.category_texture_pack, R.string.category_mod).map { stringResource(it) },
                state.category, actions::setCategory,
            )
        }
        if (state.subs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.subs.forEachIndexed { i, sub ->
                    FilterChip(sub.checked, { actions.toggleSub(i, !sub.checked) }, label = { Text(sub.label) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SbaText(state.count, Modifier.weight(1f), color = colors.onSurfaceVariant, style = LocalSbaText.current.rowBody)
            var open by remember { mutableStateOf(false) }
            Box {
                TextButton({ open = true }) {
                    Icon(painterResource(R.drawable.ic_sort), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(state.sortLabel, style = MaterialTheme.typography.labelLarge)
                }
                DropdownMenu(open, { open = false }) {
                    state.sortOptions.forEachIndexed { i, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { open = false; actions.setSort(i) },
                            trailingIcon = { if (i == state.sortSelected) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
        }
    }
}

/** A row of outlined, connected toggle buttons where one is chosen (`MaterialButtonToggleGroup`, 48dp tall). */
@Composable
private fun ToggleRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { index, label ->
            val checked = index == selected
            val outer = CornerSize(50)
            val inner = CornerSize(8.dp)
            val shape = if (checked) RoundedCornerShape(50) else RoundedCornerShape(
                topStart = if (index == 0) outer else inner, bottomStart = if (index == 0) outer else inner,
                topEnd = if (index == labels.lastIndex) outer else inner, bottomEnd = if (index == labels.lastIndex) outer else inner,
            )
            androidx.compose.material3.Surface(
                onClick = { onSelect(index) },
                // 48dp tall with 4dp of inset above and below, as MaterialButton draws it.
                modifier = Modifier.height(48.dp).padding(vertical = 4.dp),
                shape = shape,
                color = if (checked) colors.secondaryContainer else Color.Transparent,
                contentColor = if (checked) colors.onSecondaryContainer else colors.onSurface,
                border = if (checked) null else androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Box(Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
fun BrowseList(state: BrowseListState, actions: BrowseListActions) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.boundedHeight().fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
            itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                BrowseCard(item, index) { actions.open(item.id) }
            }
        }
        state.message?.let {
            SbaText(
                it, Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 48.dp), color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
            )
        }
        if (state.retry) {
            TonalAction(stringResource(R.string.browse_retry), actions::retry, Modifier.align(Alignment.TopCenter).padding(top = 120.dp))
        }
    }
}

@Composable
private fun BrowseCard(item: BrowseItem, index: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).appear(index).pressable(source),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = if (item.selected) colors.secondaryContainer else colors.surfaceContainerHigh),
        interactionSource = source,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ModIcon(item.avatar, item.avatarColors, item.icon, 64.dp, 14.dp, 16.dp, MaterialTheme.typography.headlineSmallEmphasized)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                SbaText(item.name, color = colors.onSurface, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                SbaText(item.meta, color = colors.onSurfaceVariant, style = LocalSbaText.current.rowBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SbaText(item.summary, Modifier.padding(top = 6.dp), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                SbaText(item.stats, Modifier.padding(top = 8.dp), color = colors.onSurfaceVariant, style = LocalSbaText.current.caption)
            }
            if (item.verified) {
                Icon(painterResource(R.drawable.ic_verified), stringResource(R.string.browse_audited), Modifier.padding(start = 8.dp).size(24.dp), tint = colors.primary)
            }
        }
    }
}

/** A mod's icon in a soft rounded square, or its first letter on a coloured gradient while there is no icon. */
@Composable
fun ModIcon(letter: String, colors: Pair<Int, Int>, icon: Bitmap?, size: androidx.compose.ui.unit.Dp, avatarRadius: androidx.compose.ui.unit.Dp,
            iconRadius: androidx.compose.ui.unit.Dp, style: androidx.compose.ui.text.TextStyle) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    if (icon != null) {
        val shape = RoundedCornerShape(iconRadius)
        Image(icon.asImageBitmap(), null, Modifier.size(size).border(1.5.dp, outline, shape).padding(1.dp).clip(shape), contentScale = ContentScale.Crop)
    } else {
        val shape = RoundedCornerShape(avatarRadius)
        Box(
            Modifier.size(size).background(Brush.linearGradient(listOf(Color(colors.first), Color(colors.second))), shape).border(1.5.dp, outline, shape),
            contentAlignment = Alignment.Center,
        ) { SbaText(letter, color = Color.White, style = style) }
    }
}

// ---- the store page --------------------------------------------------------------------------------

data class Badge(val label: String, @DrawableRes val icon: Int, val accent: Boolean)
data class StatTile(val value: String, val label: String)
data class Reason(@DrawableRes val icon: Int, val title: String, val body: String?)
data class InfoLine(val label: String, val value: String)

/** What the install area shows. */
sealed interface InstallUi {
    /** A running install: a line and a bar. [percent] 0 means unknown. */
    data class Working(val status: String, val percent: Int) : InstallUi

    /**
     * Idle: the main button, with a trailing versions button when [split], and a secondary Remove.
     * [remove] is true when the main action is Remove.
     */
    data class Idle(val label: String, val remove: Boolean, val split: Boolean, val secondaryRemove: Boolean, val versionOptions: List<String>, val versionSelected: Int) : InstallUi
}

data class DetailState(
    val id: String,
    val avatar: String,
    val avatarColors: Pair<Int, Int>,
    val icon: Bitmap?,
    val name: String,
    val author: String,
    val meta: String,
    val badges: List<Badge>,
    val install: InstallUi,
    val stats: List<StatTile>,
    val imageBase: String?,
    val screenshots: List<String>,
    val about: String,
    val reasons: List<Reason>,
    val hash: String?,
    val categories: List<String>,
    val info: List<InfoLine>,
    val changelog: String?,
)

interface DetailActions {
    fun primary()
    fun remove()
    fun pickVersion(index: Int)
    fun openViewer(index: Int)
}

@Composable
fun BrowseDetail(state: DetailState?, actions: DetailActions, prompt: String) {
    val colors = MaterialTheme.colorScheme
    if (state == null) {
        Box(Modifier.boundedHeight().fillMaxSize(), contentAlignment = Alignment.Center) {
            SbaText(prompt, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    Column(Modifier.boundedHeight().fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 112.dp)) {
        DetailHeader(state)
        if (state.badges.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.badges.forEach { BadgeChip(it) }
            }
        }
        InstallArea(state.install, actions, Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.stats.forEachIndexed { i, tile -> StatTileView(tile, i, Modifier.weight(1f)) }
        }
        if (state.screenshots.isNotEmpty()) {
            Section(stringResource(R.string.detail_screenshots))
            ScreenshotStrip(state, actions)
        }
        Section(stringResource(R.string.detail_about))
        SbaText(state.about, color = colors.onSurface, style = MaterialTheme.typography.bodyMedium)
        Section(stringResource(R.string.detail_verification))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
            Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)) {
                state.reasons.forEach { ReasonRow(it) }
                state.hash?.let {
                    SbaText(stringResource(R.string.detail_hash_title), Modifier.padding(top = 12.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    SelectionContainer { SbaText(it, color = colors.onSurface, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Section(stringResource(R.string.detail_categories))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { state.categories.forEach { BadgeChip(Badge(it, 0, false)) } }
        Section(stringResource(R.string.detail_info))
        state.info.forEach { InfoRow(it) }
        state.changelog?.let {
            Section(stringResource(R.string.detail_whats_new))
            SbaText(it, color = colors.onSurface, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Section(title: String) {
    SbaText(title, Modifier.padding(top = 28.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLargeEmphasized)
}

@Composable
private fun DetailHeader(state: DetailState) {
    val colors = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = colors.primaryContainer)) {
        Box {
            ShapeBackdrop(Modifier.matchParentSize())
            Column(Modifier.padding(24.dp)) {
                ModIcon(state.avatar, state.avatarColors, state.icon, 104.dp, 23.dp, 23.dp, MaterialTheme.typography.displaySmallEmphasized)
                SbaText(state.name, Modifier.fillMaxWidth().padding(top = 20.dp), color = colors.onPrimaryContainer, style = LocalSbaText.current.hero)
                SbaText(state.author, Modifier.fillMaxWidth().padding(top = 2.dp), color = colors.onPrimaryContainer, style = MaterialTheme.typography.bodyLarge)
                SbaText(state.meta, Modifier.fillMaxWidth().padding(top = 2.dp), color = colors.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A read-only label: "Checked by the team", "Open source"... */
@Composable
private fun BadgeChip(badge: Badge) {
    SuggestionChip(
        onClick = {},
        label = { Text(badge.label) },
        icon = if (badge.icon != 0) {
            { Icon(painterResource(badge.icon), null, Modifier.size(18.dp), tint = if (badge.accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
    )
}

/** A tile in the row of facts; each has its own corner shape, which gives the row some rhythm. */
@Composable
private fun StatTileView(tile: StatTile, index: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val tone = index % 3
    val even = index % 2 == 0
    val container = when (tone) { 0 -> colors.tertiaryContainer; 1 -> colors.secondaryContainer; else -> colors.primaryContainer }
    val on = when (tone) { 0 -> colors.onTertiaryContainer; 1 -> colors.onSecondaryContainer; else -> colors.onPrimaryContainer }
    val big = 28.dp
    val small = 8.dp
    Card(
        modifier,
        shape = RoundedCornerShape(
            topStart = if (even) big else small, bottomEnd = if (even) big else small,
            topEnd = if (even) small else big, bottomStart = if (even) small else big,
        ),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp)) {
            SbaText(tile.value, color = on, style = MaterialTheme.typography.titleLargeEmphasized)
            SbaText(tile.label, color = on, style = LocalSbaText.current.rowBody)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstallArea(state: InstallUi, actions: DetailActions, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        is InstallUi.Working -> Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            SbaText(state.status, Modifier.padding(bottom = 12.dp), color = colors.onSurface, style = MaterialTheme.typography.titleMediumEmphasized)
            if (state.percent > 0) LinearWavyProgressIndicator(progress = { state.percent / 100f }, Modifier.fillMaxWidth())
            else LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        }
        is InstallUi.Idle -> Column(modifier.fillMaxWidth()) {
            val label: @Composable () -> Unit = {
                Icon(painterResource(if (state.remove) R.drawable.ic_delete else R.drawable.ic_download), null, Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(state.label, style = MaterialTheme.typography.titleMediumEmphasized)
            }
            if (state.split) {
                var open by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Button(
                        onClick = actions::primary,
                        modifier = Modifier.weight(1f).height(72.dp).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                    ) { label() }
                    Box {
                        Button(
                            onClick = { open = true },
                            modifier = Modifier.size(72.dp).padding(vertical = 4.dp),
                            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 36.dp, bottomEnd = 36.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Icon(painterResource(R.drawable.ic_expand_more), stringResource(R.string.detail_version_menu)) }
                        DropdownMenu(open, { open = false }) {
                            state.versionOptions.forEachIndexed { i, text ->
                                DropdownMenuItem(
                                    text = { Text(text) },
                                    onClick = { open = false; actions.pickVersion(i) },
                                    trailingIcon = { if (i == state.versionSelected) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)) },
                                )
                            }
                        }
                    }
                }
            } else {
                // 72dp tall with 4dp of inset above and below, as MaterialButton draws it.
                Button(onClick = actions::primary, modifier = Modifier.fillMaxWidth().height(72.dp).padding(vertical = 4.dp), shapes = ButtonDefaults.shapes()) { label() }
            }
            if (state.secondaryRemove) {
                OutlinedButton(actions::remove, Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp)) {
                    Text(stringResource(R.string.detail_remove), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** A picture from the store, loaded off the main thread; null until it is there. */
@Composable
private fun rememberStoreImage(base: String?, path: String, maxPixels: Int): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, base, path) {
        value = suspendCancellableCoroutine { c -> ImageLoader.load(context, base, path, maxPixels) { if (c.isActive) c.resume(it) } }
    }
    return bitmap
}

/**
 * Screenshots as a row of pictures at one height, each as wide as the picture is, running to the edges of the screen
 * (like the store apps do). Tapping one opens the viewer.
 */
@Composable
private fun ScreenshotStrip(state: DetailState, actions: DetailActions) {
    val colors = MaterialTheme.colorScheme
    // The page has 24dp of side padding; the row ignores it so the pictures slide out under the screen edge.
    LazyRow(
        Modifier.layout { measurable, constraints ->
            val extra = 48.dp.roundToPx()
            val p = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra, minWidth = constraints.minWidth + extra))
            layout(p.width - extra, p.height) { p.place(-extra / 2, 0) }
        }.height(230.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 14.dp),
    ) {
        itemsIndexed(state.screenshots) { index, path ->
            val bitmap = rememberStoreImage(state.imageBase, path, 1024)
            val width = if (bitmap == null) 340f else (220f * bitmap.width / bitmap.height).coerceIn(120f, 480f)
            val shape = RoundedCornerShape(16.dp)
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier.padding(end = 10.dp).width(width.dp).height(220.dp).pressable(source)
                    .background(colors.surfaceContainerHigh, shape).border(1.dp, colors.outlineVariant, shape).padding(1.dp).clip(shape)
                    .clickable(interactionSource = source, indication = androidx.compose.material3.ripple()) { actions.openViewer(index) },
            ) {
                if (bitmap != null) Image(bitmap.asImageBitmap(), stringResource(R.string.detail_screenshots), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
private fun ReasonRow(reason: Reason) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(top = 12.dp)) {
        // A warning is red; everything else that backs the mod up is the accent colour.
        Icon(painterResource(reason.icon), null, Modifier.size(24.dp), tint = if (reason.icon == R.drawable.ic_warning) colors.error else colors.primary)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            SbaText(reason.title, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
            reason.body?.let { SbaText(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun InfoRow(line: InfoLine) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SbaText(line.label, Modifier.width(140.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        SbaText(line.value, Modifier.weight(1f), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A full screen viewer for the screenshots: swipe between them, with a counter and a close button. */
@Composable
fun ScreenshotViewer(base: String?, paths: List<String>, start: Int, onClose: () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val pager = rememberPagerState(start) { paths.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val bitmap = rememberStoreImage(base, paths[page], 2048)
                Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }, contentAlignment = Alignment.Center) {
                    if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            SbaText(
                "${pager.currentPage + 1} / ${paths.size}",
                Modifier.align(Alignment.TopCenter).padding(top = 36.dp).background(Color(0x80000000), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color.White, style = MaterialTheme.typography.titleMediumEmphasized,
            )
            IconButton(onClose, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 32.dp).size(48.dp).background(Color(0x80000000), CircleShape)) {
                Icon(painterResource(R.drawable.ic_close), stringResource(android.R.string.cancel), tint = Color.White)
            }
        }
    }
}

/** The Java fragment's way in. */
object BrowseScreenHost {
    @JvmStatic
    fun header(view: androidx.compose.ui.platform.ComposeView, state: androidx.compose.runtime.State<BrowseHeader>, actions: BrowseHeaderActions) {
        view.setContent { SbaTheme { BrowseHeaderView(state.value, actions) } }
    }

    @JvmStatic
    fun list(view: androidx.compose.ui.platform.ComposeView, state: androidx.compose.runtime.State<BrowseListState>, actions: BrowseListActions) {
        view.setContent { SbaTheme { BrowseList(state.value, actions) } }
    }

    @JvmStatic
    fun detail(view: androidx.compose.ui.platform.ComposeView, state: androidx.compose.runtime.State<DetailState?>, actions: DetailActions, prompt: String,
               viewer: androidx.compose.runtime.State<ViewerRequest?>, closeViewer: Runnable) {
        view.setContent {
            SbaTheme {
                BrowseDetail(state.value, actions, prompt)
                viewer.value?.let { ScreenshotViewer(it.base, it.paths, it.start) { closeViewer.run() } }
            }
        }
    }
}

data class ViewerRequest(val base: String?, val paths: List<String>, val start: Int)
