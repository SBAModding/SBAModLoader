package dev.segually.sbamodloader.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Spring motion as the Views version had it (`ui/Motion.java`): a pressed thing squashes to 96% and springs back with a
 * small overshoot. Pass the same [interactionSource] to the clickable it wraps.
 */
@Composable
fun Modifier.pressable(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        // STIFFNESS_MEDIUM and DAMPING_RATIO_MEDIUM_BOUNCY, as in Motion.pressable.
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * New things rise 36dp into place with a spring and fade in, a little later for each later item (`Motion.appear`).
 * Plays once per composition entry, not again when the item recomposes.
 */
fun Modifier.appear(index: Int): Modifier = composed {
    if (index > 10) return@composed this
    val rise = with(LocalDensity.current) { 36.dp.toPx() }
    val alpha = remember { Animatable(0f) }
    val offset = remember { Animatable(rise) }
    LaunchedEffect(Unit) {
        delay(40L * index)
        // Both run on this coroutine's children, so they start together.
        coroutineScope {
            launch { alpha.animateTo(1f, tween(220)) }
            launch { offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) }
        }
    }
    graphicsLayer { this.alpha = alpha.value; translationY = offset.value }
}
