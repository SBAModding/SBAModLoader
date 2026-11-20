package dev.segually.sbamodloader.ui.compose

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Compose dialogs and sheets that Java can open: they live in a [ComponentDialog], so they have a lifecycle and saved state. */
object SbaDialogs {
    private tailrec fun Context.activity(): ComponentActivity = when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.activity()
        else -> error("A dialog needs an activity context")
    }

    /**
     * Opens a dialog showing [content], centred on a dimmed screen, or at the bottom as a sheet. [content] gets the
     * function that closes it.
     */
    @JvmStatic
    @JvmOverloads
    fun show(
        context: Context,
        cancelable: Boolean = true,
        sheet: Boolean = false,
        onCancel: Runnable? = null,
        content: @Composable (dismiss: () -> Unit) -> Unit,
    ): Dialog {
        val activity = context.activity()
        val dialog = ComponentDialog(activity)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.32f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.setCancelable(cancelable)
        dialog.setOnCancelListener { onCancel?.run() }
        val view = androidx.compose.ui.platform.ComposeView(activity)
        view.setContent {
            SbaTheme {
                val outside = remember { MutableInteractionSource() }
                Box(
                    Modifier.fillMaxSize().clickable(outside, indication = null) { if (cancelable) dialog.cancel() }
                        .safeDrawingPadding().imePadding(),
                    contentAlignment = if (sheet) Alignment.BottomCenter else Alignment.Center,
                ) {
                    // Taps on the dialog itself must not reach the dimmed area behind it.
                    val inside = remember { MutableInteractionSource() }
                    Box(Modifier.clickable(inside, indication = null) { }) { content { dialog.dismiss() } }
                }
            }
        }
        dialog.setContentView(view)
        // After the content is set, or the theme's own wrap-the-content width wins.
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()
        return dialog
    }
}

/**
 * A Material 3 alert: optional icon, title, a body, and buttons along the bottom. It is as wide as the window allows
 * (up to 560dp), and when the buttons do not fit side by side they stack, the last one (the main action) on top.
 */
@Composable
fun AlertCard(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.secondary,
    title: String? = null,
    titleStyle: androidx.compose.ui.text.TextStyle? = null,
    centerHeader: Boolean = false,
    buttons: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 35.dp, vertical = 16.dp).widthIn(max = 560.dp),
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Column(Modifier.padding(24.dp).heightIn(max = 640.dp)) {
            if (icon != null) {
                Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = if (centerHeader) Alignment.Center else Alignment.CenterStart) {
                    Icon(painterResource(icon), null, Modifier.size(if (centerHeader) 56.dp else 24.dp), tint = iconTint)
                }
            }
            if (title != null) {
                SbaText(
                    title, Modifier.fillMaxWidth().padding(bottom = 16.dp), color = colors.onSurface,
                    style = (titleStyle ?: MaterialTheme.typography.headlineSmall).copy(
                        textAlign = if (centerHeader) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                    ),
                )
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { body() }
            ButtonRow(Modifier.fillMaxWidth().padding(top = 24.dp), buttons)
        }
    }
}

/** Lays the buttons out in a row at the end; when they are too wide for that, one under the other, last on top. */
@Composable
private fun ButtonRow(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, modifier) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0)
        val placeables = measurables.map { it.measure(loose) }
        val side = placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0)
        if (side <= constraints.maxWidth) {
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(constraints.maxWidth, height) {
                var x = constraints.maxWidth - side
                placeables.forEach { it.placeRelative(x, (height - it.height) / 2); x += it.width + gap }
            }
        } else {
            val height = placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
            layout(constraints.maxWidth, height) {
                var y = 0
                placeables.reversed().forEach { it.placeRelative(constraints.maxWidth - it.width, y); y += it.height + gap }
            }
        }
    }
}

/** A dialog button. */
@Composable
fun DialogButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick, enabled = enabled) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** The body text of an alert. */
@Composable
fun AlertText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    SbaText(text, modifier, color = color, style = MaterialTheme.typography.bodyMedium)
}
