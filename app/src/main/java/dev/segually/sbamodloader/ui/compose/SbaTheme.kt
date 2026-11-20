package dev.segually.sbamodloader.ui.compose

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.appcompat.R as AC
import com.google.android.material.R as M
import dev.segually.sbamodloader.R

/** The app's one typeface: Roboto Flex, one variable file used at several weights. */
val SbaFontFamily = FontFamily(
    listOf(300, 400, 500, 600, 700, 800, 900).map { weight ->
        Font(
            R.font.roboto_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    }
)

/**
 * The colours the Views already resolved from the activity's theme, as a Compose colour scheme.
 *
 * Material You, the chosen accent colour and light/dark are all applied to the Views theme by `Theming.apply`, and
 * Compose has no equivalent of the accent seed. Reading the resolved attributes keeps both UIs identical by
 * construction, and the Compose screens follow every appearance setting with no second implementation of it.
 */
private fun Context.resolvedColorScheme(): ColorScheme {
    fun c(@AttrRes attr: Int): Color {
        val tv = TypedValue()
        check(theme.resolveAttribute(attr, tv, true)) { "Theme does not define attribute $attr" }
        val argb = if (tv.resourceId != 0) getColor(tv.resourceId) else tv.data
        return Color(argb)
    }
    return ColorScheme(
        primary = c(AC.attr.colorPrimary),
        onPrimary = c(M.attr.colorOnPrimary),
        primaryContainer = c(M.attr.colorPrimaryContainer),
        onPrimaryContainer = c(M.attr.colorOnPrimaryContainer),
        inversePrimary = c(M.attr.colorPrimaryInverse),
        secondary = c(M.attr.colorSecondary),
        onSecondary = c(M.attr.colorOnSecondary),
        secondaryContainer = c(M.attr.colorSecondaryContainer),
        onSecondaryContainer = c(M.attr.colorOnSecondaryContainer),
        tertiary = c(M.attr.colorTertiary),
        onTertiary = c(M.attr.colorOnTertiary),
        tertiaryContainer = c(M.attr.colorTertiaryContainer),
        onTertiaryContainer = c(M.attr.colorOnTertiaryContainer),
        background = c(android.R.attr.colorBackground),
        onBackground = c(M.attr.colorOnBackground),
        surface = c(M.attr.colorSurface),
        onSurface = c(M.attr.colorOnSurface),
        surfaceVariant = c(M.attr.colorSurfaceVariant),
        onSurfaceVariant = c(M.attr.colorOnSurfaceVariant),
        surfaceTint = c(AC.attr.colorPrimary),
        inverseSurface = c(M.attr.colorSurfaceInverse),
        inverseOnSurface = c(M.attr.colorOnSurfaceInverse),
        error = c(AC.attr.colorError),
        onError = c(M.attr.colorOnError),
        errorContainer = c(M.attr.colorErrorContainer),
        onErrorContainer = c(M.attr.colorOnErrorContainer),
        outline = c(M.attr.colorOutline),
        outlineVariant = c(M.attr.colorOutlineVariant),
        scrim = Color.Black,
        surfaceBright = c(M.attr.colorSurfaceBright),
        surfaceDim = c(M.attr.colorSurfaceDim),
        surfaceContainer = c(M.attr.colorSurfaceContainer),
        surfaceContainerHigh = c(M.attr.colorSurfaceContainerHigh),
        surfaceContainerHighest = c(M.attr.colorSurfaceContainerHighest),
        surfaceContainerLow = c(M.attr.colorSurfaceContainerLow),
        surfaceContainerLowest = c(M.attr.colorSurfaceContainerLowest),
        primaryFixed = c(M.attr.colorPrimaryFixed),
        primaryFixedDim = c(M.attr.colorPrimaryFixedDim),
        onPrimaryFixed = c(M.attr.colorOnPrimaryFixed),
        onPrimaryFixedVariant = c(M.attr.colorOnPrimaryFixedVariant),
        secondaryFixed = c(M.attr.colorSecondaryFixed),
        secondaryFixedDim = c(M.attr.colorSecondaryFixedDim),
        onSecondaryFixed = c(M.attr.colorOnSecondaryFixed),
        onSecondaryFixedVariant = c(M.attr.colorOnSecondaryFixedVariant),
        tertiaryFixed = c(M.attr.colorTertiaryFixed),
        tertiaryFixedDim = c(M.attr.colorTertiaryFixedDim),
        onTertiaryFixed = c(M.attr.colorOnTertiaryFixed),
        onTertiaryFixedVariant = c(M.attr.colorOnTertiaryFixedVariant),
    )
}

/** Material 3 Expressive, themed from the activity's own theme so it always matches the Views. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SbaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Recreated with the activity when an appearance setting changes, so the configuration is the only key needed.
    val configuration: Configuration = LocalConfiguration.current
    val scheme = remember(context, configuration.uiMode) { context.resolvedColorScheme() }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = remember(context) { context.sbaTypography() },
    ) {
        val sbaText = remember(context) { context.sbaTextStyles() }
        // A ComposeView inside a scrolling View parent is first measured without a width limit, which a horizontally
        // scrolling row refuses. The first pass is thrown away, so any finite width will do.
        val fallback = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
        Box(Modifier.layout { measurable, constraints ->
            val bounded = if (constraints.hasBoundedWidth) constraints else constraints.copy(maxWidth = fallback)
            val placeable = measurable.measure(bounded)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }) { androidx.compose.runtime.CompositionLocalProvider(LocalSbaText provides sbaText) { content() } }
    }
}
