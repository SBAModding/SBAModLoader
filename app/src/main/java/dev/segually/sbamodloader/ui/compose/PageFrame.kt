package dev.segually.sbamodloader.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val ExpandedBar = 130.dp
private val CollapsedBar = 64.dp
/** The bar's scrim shows once less than this much of it is visible (`scrimVisibleHeightTrigger`). */
private val ScrimTrigger = 120.dp
/** The room below the content for the floating navigation. */
val NavClearance = 96.dp

/**
 * A page: a large title that shrinks into a pinned bar as the page scrolls (`CollapsingToolbarLayout` with
 * `exitUntilCollapsed`), and the page's content under it. The content scrolls by itself (a [verticalScroll] column,
 * a lazy list...) and reports its scrolling to the bar through nested scrolling, which [content] receives as
 * [Modifier] to put on its scrolling container.
 *
 * The page keeps to [maxContentWidth] (plus its 20dp sides) in the middle of a wide window, and the title lines up with
 * the content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageFrame(
    title: String,
    maxContentWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (scrolling: Modifier) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    LaunchedEffect(density) {
        behavior.state.heightOffsetLimit = -with(density) { (ExpandedBar - CollapsedBar).toPx() }
        // Debug builds can open a page already scrolled (adb ... --ez sba_collapse true), for layout checks.
        if (SbaDebug.collapse) behavior.state.heightOffset = behavior.state.heightOffsetLimit
    }
    val sides = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    Box(modifier.boundedHeight().fillMaxSize().windowInsetsPadding(sides), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = maxContentWidth + 40.dp).fillMaxSize()) {
            val fraction = behavior.state.collapsedFraction
            Bar(title, fraction)
            Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(behavior.nestedScrollConnection)) {
                content(Modifier)
            }
        }
    }
}

@Composable
private fun Bar(title: String, fraction: Float) {
    val colors = MaterialTheme.colorScheme
    val height = lerp(ExpandedBar, CollapsedBar, fraction)
    val scrim by animateFloatAsState(if (height < ScrimTrigger) 1f else 0f, tween(200), label = "barScrim")
    val expandedStyle: TextStyle = LocalSbaText.current.pageTitle
    val collapsedStyle: TextStyle = MaterialTheme.typography.titleLargeEmphasized
    val scale = lerpFloat(1f, collapsedStyle.fontSize.value / expandedStyle.fontSize.value, fraction)
    // The title is drawn in the large style and scaled down (`titleCollapseMode="scale"`), but with the small style's
    // lighter weight once it is more than half way, as the Views do.
    val style = if (fraction < 0.5f) expandedStyle else expandedStyle.copy(fontWeight = FontWeight.Normal)
    // The text box is a natural line tall (1.52 em), which is what SbaText makes it.
    val box = (expandedStyle.fontSize.value * 1.52f).dp
    // Where the box's top is: low in the large bar (5dp above its bottom, measured against the Views), and centred in
    // the small one.
    val topExpanded = ExpandedBar - 5.dp - box
    val topCollapsed = CollapsedBar / 2 - 2.dp - box * collapsedStyle.fontSize.value / expandedStyle.fontSize.value / 2
    Box(Modifier.fillMaxWidth().height(height)) {
        Box(Modifier.matchParentSize().alpha(scrim).background(colors.surface))
        SbaText(
            title,
            Modifier.padding(start = 20.dp).offset(y = lerp(topExpanded, topCollapsed, fraction) - (ExpandedBar - height) * 0f)
                .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) },
            color = colors.onSurface,
            style = style,
        )
    }
}

private fun lerpFloat(a: Float, b: Float, t: Float) = a + (b - a) * t

/** A page whose content is one scrolling column (the pages that are a stack of cards). */
@Composable
fun ScrollingPage(title: String, maxContentWidth: Dp, content: @Composable () -> Unit) {
    PageFrame(title, maxContentWidth) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp)
                .padding(bottom = NavClearance)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
        ) { content() }
    }
}

/** Debug-only switches read from intent extras by `MainActivity`; always false in release builds. */
object SbaDebug {
    @JvmField
    @Volatile
    var collapse = false
}
