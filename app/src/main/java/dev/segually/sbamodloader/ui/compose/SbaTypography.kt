package dev.segually.sbamodloader.ui.compose

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.annotation.StyleRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.segually.sbamodloader.R

/**
 * A Compose [TextStyle] built from an Android `TextAppearance` style, so the Compose screens use exactly the numbers the
 * Views did: size, line height, letter spacing and weight. (Compose's own type scale differs from Material
 * Components' in letter spacing and in what "emphasized" means, and the app adds bold on top of the latter.)
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Context.textStyleOf(@StyleRes appearance: Int, family: FontFamily = SbaFontFamily): TextStyle {
    val attrs = intArrayOf(
        android.R.attr.textSize,
        android.R.attr.textStyle,
        android.R.attr.letterSpacing,
        android.R.attr.textFontWeight,
        android.R.attr.lineHeight,
        androidx.appcompat.R.attr.lineHeight,
    ).sortedArray()
    val a = obtainStyledAttributes(appearance, attrs)
    try {
        fun index(attr: Int) = attrs.indexOf(attr)
        fun sp(attr: Int): TextUnit {
            val v = TypedValue()
            val i = index(attr)
            return if (a.getValue(i, v) && v.type == TypedValue.TYPE_DIMENSION) TypedValue.complexToFloat(v.data).sp else TextUnit.Unspecified
        }
        val size = sp(android.R.attr.textSize)
        val lineHeight = sp(android.R.attr.lineHeight).let { if (it != TextUnit.Unspecified) it else sp(androidx.appcompat.R.attr.lineHeight) }
        val spacing = a.getFloat(index(android.R.attr.letterSpacing), 0f)
        val style = a.getInt(index(android.R.attr.textStyle), Typeface.NORMAL)
        // A weight given explicitly wins over bold, as in TextView.
        val weight = if (a.hasValue(index(android.R.attr.textFontWeight))) a.getInt(index(android.R.attr.textFontWeight), 400)
        else if (style and Typeface.BOLD != 0) 700 else 400
        return TextStyle(
            fontFamily = family,
            fontSize = size,
            fontWeight = FontWeight(weight),
            letterSpacing = spacing.em,
            lineHeight = lineHeight,
            // Lines are exactly lineHeight tall, which is how the Views' lineHeight behaves except for the last line.
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    } finally {
        a.recycle()
    }
}

/** The app's own text styles that are not part of the Material type scale. */
class SbaTextStyles(
    val pageTitle: TextStyle,
    val hero: TextStyle,
    val section: TextStyle,
    val rowTitle: TextStyle,
    val rowBody: TextStyle,
    val caption: TextStyle,
)

val LocalSbaText = staticCompositionLocalOf<SbaTextStyles> { error("SbaTheme is missing") }

internal fun Context.sbaTextStyles() = SbaTextStyles(
    pageTitle = textStyleOf(R.style.TextAppearance_SBA_PageTitle).copy(fontWeight = FontWeight.Black),
    hero = textStyleOf(R.style.TextAppearance_SBA_Hero),
    section = textStyleOf(R.style.TextAppearance_SBA_Section),
    rowTitle = textStyleOf(R.style.TextAppearance_SBA_RowTitle),
    rowBody = textStyleOf(R.style.TextAppearance_SBA_RowBody),
    caption = textStyleOf(R.style.TextAppearance_SBA_Caption),
)

/** The Material type scale, every slot taken from the app's `TextAppearance.SBA.*` styles. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun Context.sbaTypography(): Typography {
    fun t(res: Int) = textStyleOf(res)
    return Typography(
        displayLarge = t(R.style.TextAppearance_SBA_DisplayLarge),
        displayMedium = t(R.style.TextAppearance_SBA_DisplayMedium),
        displaySmall = t(R.style.TextAppearance_SBA_DisplaySmall),
        headlineLarge = t(R.style.TextAppearance_SBA_HeadlineLarge),
        headlineMedium = t(R.style.TextAppearance_SBA_HeadlineMedium),
        headlineSmall = t(R.style.TextAppearance_SBA_HeadlineSmall),
        titleLarge = t(R.style.TextAppearance_SBA_TitleLarge),
        titleMedium = t(R.style.TextAppearance_SBA_TitleMedium),
        titleSmall = t(R.style.TextAppearance_SBA_TitleSmall),
        bodyLarge = t(R.style.TextAppearance_SBA_BodyLarge),
        bodyMedium = t(R.style.TextAppearance_SBA_BodyMedium),
        bodySmall = t(R.style.TextAppearance_SBA_BodySmall),
        labelLarge = t(R.style.TextAppearance_SBA_LabelLarge),
        labelMedium = t(R.style.TextAppearance_SBA_LabelMedium),
        labelSmall = t(R.style.TextAppearance_SBA_LabelSmall),
        displayLargeEmphasized = t(R.style.TextAppearance_SBA_DisplayLargeEmphasized),
        displayMediumEmphasized = t(R.style.TextAppearance_SBA_DisplayMediumEmphasized),
        displaySmallEmphasized = t(R.style.TextAppearance_SBA_DisplaySmallEmphasized),
        headlineLargeEmphasized = t(R.style.TextAppearance_SBA_HeadlineLargeEmphasized),
        headlineMediumEmphasized = t(R.style.TextAppearance_SBA_HeadlineMediumEmphasized),
        headlineSmallEmphasized = t(R.style.TextAppearance_SBA_HeadlineSmallEmphasized),
        titleLargeEmphasized = t(R.style.TextAppearance_SBA_TitleLargeEmphasized),
        titleMediumEmphasized = t(R.style.TextAppearance_SBA_TitleMediumEmphasized),
        titleSmallEmphasized = t(R.style.TextAppearance_SBA_TitleSmallEmphasized),
        bodyLargeEmphasized = t(R.style.TextAppearance_SBA_BodyLargeEmphasized),
        bodyMediumEmphasized = t(R.style.TextAppearance_SBA_BodyMediumEmphasized),
        bodySmallEmphasized = t(R.style.TextAppearance_SBA_BodySmallEmphasized),
        labelLargeEmphasized = t(R.style.TextAppearance_SBA_LabelLargeEmphasized),
        labelMediumEmphasized = t(R.style.TextAppearance_SBA_LabelMediumEmphasized),
        labelSmallEmphasized = t(R.style.TextAppearance_SBA_LabelSmallEmphasized),
    )
}
