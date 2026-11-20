package dev.segually.sbamodloader.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.segually.sbamodloader.R

enum class StepState { OK, ERROR, PENDING }

/** One line of the setup checklist. [onClick] is null when the step is not actionable. */
data class SetupStep(val state: StepState, val text: String, val onClick: (() -> Unit)? = null)

data class DashboardState(
    /** The installed game's version, or null when the game is not installed. */
    val gameVersion: String? = null,
    val steps: List<SetupStep> = emptyList(),
    val done: Int = 0,
    val canLaunch: Boolean = false,
    val showRetry: Boolean = false,
    val modUpdates: Int = 0,
)

/** The Home page. Two columns from 840dp wide, as the Views layout had (`layout-w840dp/fragment_dashboard.xml`). */
@Composable
fun DashboardScreen(
    state: DashboardState,
    onLaunch: () -> Unit,
    onRetry: () -> Unit,
    onModUpdates: () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    if (wide) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(Modifier.weight(1.2f).padding(end = 8.dp)) {
                HeroCard(state, onLaunch, Modifier.padding(top = 8.dp).appear(0))
            }
            Column(Modifier.weight(1f).padding(start = 8.dp).appear(1)) {
                SetupCard(state, Modifier.padding(top = 12.dp))
                ModUpdatesCard(state, onModUpdates)
                RetryButton(state, onRetry)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            HeroCard(state, onLaunch, Modifier.padding(top = 8.dp).appear(0))
            SetupCard(state, Modifier.padding(top = 12.dp).appear(1))
            ModUpdatesCard(state, onModUpdates, Modifier.appear(2))
            RetryButton(state, onRetry, Modifier.appear(3))
        }
    }
}

private val CardShape = RoundedCornerShape(28.dp)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(state: DashboardState, onLaunch: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
    ) {
        Box {
            ShapeBackdrop(Modifier.matchParentSize())
            Column(Modifier.padding(24.dp)) {
                SbaText(
                    stringResource(R.string.hero_title),
                    Modifier.padding(end = 96.dp),
                    color = colors.onPrimaryContainer,
                    style = LocalSbaText.current.hero,
                )
                SbaText(
                    if (state.gameVersion == null) stringResource(R.string.step_game_missing)
                    else stringResource(R.string.hero_game_version, state.gameVersion),
                    Modifier.padding(top = 4.dp),
                    color = colors.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val source = remember { MutableInteractionSource() }
                Button(
                    onClick = onLaunch,
                    enabled = state.canLaunch,
                    // 88dp tall with 4dp of inset above and below, as MaterialButton draws it: the button itself is 80dp.
                    modifier = Modifier.padding(top = 40.dp).fillMaxWidth().height(88.dp).padding(vertical = 4.dp).pressable(source),
                    interactionSource = source,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Icon(painterResource(R.drawable.ic_play), null, Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.dashboard_launch),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupCard(state: DashboardState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SbaText(
                    stringResource(R.string.setup_progress, state.done, 3),
                    Modifier.weight(1f),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                val progress by animateFloatAsState(state.done / 3f, label = "setupProgress")
                val thickness = with(LocalDensity.current) { 8.dp.toPx() }
                Box(contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(52.dp),
                        color = colors.tertiary,
                        trackColor = colors.tertiaryContainer,
                        stroke = Stroke(thickness, cap = StrokeCap.Round),
                        trackStroke = Stroke(thickness, cap = StrokeCap.Round),
                    )
                    if (state.done == 3) {
                        Icon(painterResource(R.drawable.ic_check), null, Modifier.size(28.dp), tint = colors.tertiary)
                    }
                }
            }
            state.steps.forEach { SetupRow(it) }
        }
    }
}

@Composable
private fun SetupRow(step: SetupStep) {
    val colors = MaterialTheme.colorScheme
    val tint = when (step.state) {
        StepState.ERROR -> colors.error
        StepState.OK -> colors.primary
        StepState.PENDING -> colors.onSurfaceVariant
    }
    val icon = when (step.state) {
        StepState.OK -> R.drawable.ic_check_circle
        StepState.ERROR -> R.drawable.ic_warning
        StepState.PENDING -> R.drawable.ic_pending
    }
    val click = step.onClick
    Row(
        (if (click != null) Modifier.clickable(onClick = click) else Modifier)
            .fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, Modifier.size(28.dp), tint = tint)
        SbaText(
            step.text,
            Modifier.weight(1f).padding(start = 16.dp),
            color = if (step.state == StepState.ERROR) tint else colors.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ModUpdatesCard(state: DashboardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (state.modUpdates == 0) return
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = colors.tertiaryContainer),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_download), null, Modifier.size(28.dp), tint = colors.onTertiaryContainer)
            SbaText(
                if (state.modUpdates == 1) stringResource(R.string.dashboard_mod_updates_one)
                else stringResource(R.string.dashboard_mod_updates, state.modUpdates),
                Modifier.weight(1f).padding(start = 16.dp),
                color = colors.onTertiaryContainer,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RetryButton(state: DashboardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (!state.showRetry) return
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp).padding(vertical = 4.dp),
        shapes = ButtonDefaults.shapes(),
    ) {
        Text(stringResource(R.string.dashboard_retry_patches))
    }
}

/** The Java fragment's way in: puts [DashboardScreen] into a [ComposeView], inside the app theme. */
object DashboardScreenHost {
    @JvmStatic
    fun setContent(
        view: androidx.compose.ui.platform.ComposeView,
        state: androidx.compose.runtime.State<DashboardState>,
        onLaunch: Runnable,
        onRetry: Runnable,
        onModUpdates: Runnable,
    ) {
        view.setContent {
            SbaTheme {
                DashboardScreen(state.value, { onLaunch.run() }, { onRetry.run() }, { onModUpdates.run() })
            }
        }
    }
}
