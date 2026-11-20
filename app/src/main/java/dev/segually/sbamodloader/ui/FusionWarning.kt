package dev.segually.sbamodloader.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.segually.sbamodloader.R
import dev.segually.sbamodloader.ui.compose.AlertCard
import dev.segually.sbamodloader.ui.compose.DialogButton
import dev.segually.sbamodloader.ui.compose.SbaDialogs
import dev.segually.sbamodloader.ui.compose.SbaText

/** The yellow "Fusion may not work on this device" dialog, shown at first launch and when Fusion is turned on. */
object FusionWarning {
    private val Yellow = Color(0xFFFFC107)

    /** Shown once, before the first launch on a device that failed the check. */
    @JvmStatic
    fun showFirstLaunch(context: Context, reasons: List<String>?, onOk: Runnable) {
        SbaDialogs.show(context, cancelable = false) { dismiss ->
            Warning(reasons, R.string.fusion_warn_title, R.string.fusion_warn_body) {
                DialogButton(stringResource(R.string.fusion_warn_ok), { dismiss(); onOk.run() })
            }
        }
    }

    /** Shown when the user turns Fusion on, on a device that failed the check. */
    @JvmStatic
    fun showTurnOn(context: Context, reasons: List<String>?, onConfirm: Runnable, onCancel: Runnable) {
        SbaDialogs.show(context, onCancel = onCancel) { dismiss ->
            Warning(reasons, R.string.fusion_enable_title, R.string.fusion_enable_body) {
                DialogButton(stringResource(android.R.string.cancel), { dismiss(); onCancel.run() })
                DialogButton(stringResource(R.string.fusion_enable_confirm), { dismiss(); onConfirm.run() })
            }
        }
    }

    /** The size and weight a plain TextView of that size had: a line is the font's natural height (about 1.17 em). */
    private fun viewText(sp: Float, bold: Boolean = false) = androidx.compose.ui.text.TextStyle(
        fontFamily = dev.segually.sbamodloader.ui.compose.SbaFontFamily,
        fontSize = sp.sp,
        fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
        lineHeight = (sp * 1.17f).sp,
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None),
    )

    @Composable
    private fun Warning(reasons: List<String>?, title: Int, body: Int, buttons: @Composable () -> Unit) {
        val colors = MaterialTheme.colorScheme
        AlertCard(
            icon = R.drawable.ic_warning, iconTint = Yellow, title = stringResource(title), titleStyle = viewText(20f, bold = true),
            centerHeader = true, buttons = buttons,
        ) {
            Column {
                SbaText(stringResource(body), color = colors.onSurface, style = viewText(15f))
                if (!reasons.isNullOrEmpty()) {
                    SbaText(reasons.joinToString("\n") { "\u2022 $it" }, Modifier.padding(top = 8.dp), color = Yellow, style = viewText(14f))
                }
                SbaText(stringResource(R.string.fusion_warn_what), Modifier.padding(top = 16.dp, bottom = 4.dp), color = colors.onSurface, style = viewText(16f, bold = true))
                SbaText(stringResource(R.string.fusion_warn_bullets), color = colors.onSurface, style = viewText(15f))
            }
        }
    }
}
