package dev.segually.sbamodloader.ui.compose

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.segually.sbamodloader.R

/** Which page is chosen, and whether every button shows its label (on a desktop) or only the chosen one's. */
/** [bottomInset] is the height in pixels of the system bar under the dock, which the activity reads from its root. */
data class NavState(val selected: Int = 0, val showAllLabels: Boolean = false, val bottomInset: Int = 0)

private class NavItem(@DrawableRes val icon: Int, @StringRes val label: Int)

private val Items = listOf(
    NavItem(R.drawable.ic_nav_dashboard, R.string.nav_home),
    NavItem(R.drawable.ic_nav_browse, R.string.nav_browse),
    NavItem(R.drawable.ic_nav_mods, R.string.nav_mods),
    NavItem(R.drawable.ic_nav_settings, R.string.nav_settings),
)

/**
 * The navigation, in a floating pill over the pages: the chosen page's button grows to show its label and the others
 * shrink back to an icon. Content fades out under it instead of being cut.
 */
@Composable
fun NavDock(state: NavState, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val inset = with(androidx.compose.ui.platform.LocalDensity.current) { state.bottomInset.toDp() }
    Box(Modifier.fillMaxSize()) {
        // Scroll edge effect: the page colour, then transparent.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height(140.dp + inset)
                .background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background))),
        )
        Surface(
            // The Views dock sat the bar's height higher than its margin alone: the inset counted twice.
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp + inset * 2),
            shape = RoundedCornerShape(50),
            color = colors.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(horizontal = 9.dp, vertical = 12.5.dp), verticalAlignment = Alignment.CenterVertically) {
                Items.forEachIndexed { index, item -> NavButton(item, index == state.selected, state.showAllLabels) { onSelect(index) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NavButton(item: NavItem, selected: Boolean, showLabel: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = selected || showLabel
    val container by animateColorAsState(if (selected) colors.secondaryContainer else Color.Transparent, tween(280), label = "navContainer")
    val content = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
    val source = remember { MutableInteractionSource() }
    val text = stringResource(item.label)
    Surface(
        onClick = onClick,
        modifier = Modifier.height(56.dp).widthIn(min = 56.dp).pressable(source).animateContentSize(tween(280)).semantics { contentDescription = text },
        shape = RoundedCornerShape(28.dp),
        color = container,
        interactionSource = source,
    ) {
        Row(Modifier.padding(horizontal = if (label) 20.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(item.icon), null, Modifier.size(18.dp), tint = content)
            if (label) {
                Spacer(Modifier.width(8.dp))
                SbaText(text, color = content, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
            }
        }
    }
}

/** The Java activity's way in. */
object NavDockHost {
    @JvmStatic
    fun setContent(view: androidx.compose.ui.platform.ComposeView, state: androidx.compose.runtime.State<NavState>, onSelect: java.util.function.IntConsumer) {
        view.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent { SbaTheme { NavDock(state.value) { onSelect.accept(it) } } }
    }
}
