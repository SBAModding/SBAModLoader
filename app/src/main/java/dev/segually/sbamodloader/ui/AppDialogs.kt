package dev.segually.sbamodloader.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.segually.sbamodloader.R
import dev.segually.sbamodloader.ui.compose.AlertCard
import dev.segually.sbamodloader.ui.compose.AlertText
import dev.segually.sbamodloader.ui.compose.DialogButton
import dev.segually.sbamodloader.ui.compose.SbaDialogs
import dev.segually.sbamodloader.ui.compose.SbaText

/** The app's own dialogs and sheets, in Compose, for Java callers. */
object AppDialogs {
    /** A question with two buttons. [negative] null means there is only the positive button. */
    @JvmStatic
    fun confirm(context: Context, title: String, message: String?, positive: String, negative: String?, onConfirm: Runnable) {
        SbaDialogs.show(context) { dismiss ->
            AlertCard(
                title = title,
                buttons = {
                    if (negative != null) DialogButton(negative, dismiss)
                    DialogButton(positive, { dismiss(); onConfirm.run() })
                },
            ) { if (message != null) AlertText(message) }
        }
    }

    /**
     * A message with an OK button, and optionally a third button on the left ([neutral]). [cancelable] false means the
     * dialog can only be closed with OK.
     */
    @JvmStatic
    @JvmOverloads
    fun info(
        context: Context, title: String, message: String, onOk: Runnable? = null,
        neutral: String? = null, onNeutral: Runnable? = null, cancelable: Boolean = true,
    ) {
        SbaDialogs.show(context, cancelable = cancelable) { dismiss ->
            AlertCard(
                title = title,
                buttons = {
                    if (neutral != null) DialogButton(neutral, { dismiss(); onNeutral?.run() })
                    DialogButton(stringResource(android.R.string.ok), { dismiss(); onOk?.run() })
                },
            ) { AlertText(message) }
        }
    }

    /** What an install will download and what to watch out for, before anything is. A blocked plan can only be closed. */
    @JvmStatic
    fun plan(context: Context, title: String, body: String, blocked: Boolean, positive: String, onConfirm: Runnable) {
        SbaDialogs.show(context) { dismiss ->
            AlertCard(
                title = title,
                buttons = {
                    if (!blocked) DialogButton(androidx.compose.ui.res.stringResource(android.R.string.cancel), dismiss)
                    DialogButton(positive, { dismiss(); if (!blocked) onConfirm.run() })
                },
            ) { AlertText(body) }
        }
    }

    /** The list of problems, with a button that fixes them when the store has everything that is needed. */
    @JvmStatic
    fun problems(context: Context, issues: List<String>, hint: String, canFix: Boolean, onFix: Runnable) {
        SbaDialogs.show(context, sheet = true) { dismiss ->
            val colors = MaterialTheme.colorScheme
            Surface(
                Modifier.fillMaxWidth().heightIn(max = 720.dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = colors.surfaceContainerLow,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                    SbaText(stringResource(R.string.problems_title), color = colors.onSurface, style = MaterialTheme.typography.headlineSmallEmphasized)
                    issues.forEach {
                        Surface(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh) {
                            SbaText(it, Modifier.padding(16.dp), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    SbaText(hint, Modifier.padding(top = 16.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    var fixing by remember { mutableStateOf(false) }
                    Button(
                        onClick = { fixing = true; onFix.run(); dismiss() },
                        enabled = canFix && !fixing,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(64.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.problems_fix), style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                }
            }
        }
    }

    /** Where the Add repository dialog sends the address to be checked. */
    interface RepositoryHandler {
        /** Returns a message to show when the address is no good (checked at once), or null when it may be tried. */
        fun invalid(url: String): String?

        /** Checks the address in the background and calls [done] with an error message, or null when it was added. */
        fun submit(url: String, done: java.util.function.Consumer<String?>)
    }

    @JvmStatic
    fun addRepository(context: Context, handler: RepositoryHandler) {
        SbaDialogs.show(context) { dismiss ->
            var url by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            var checking by remember { mutableStateOf(false) }
            AlertCard(
                title = stringResource(R.string.sources_add_title),
                buttons = {
                    DialogButton(stringResource(android.R.string.cancel), dismiss)
                    DialogButton(stringResource(R.string.sources_add_confirm), {
                        val trimmed = url.trim()
                        val bad = handler.invalid(trimmed)
                        if (bad != null) { error = bad; return@DialogButton }
                        error = null
                        checking = true
                        handler.submit(trimmed) { result ->
                            checking = false
                            if (result == null) dismiss() else error = result
                        }
                    }, enabled = !checking)
                },
            ) {
                Column {
                    AlertText(stringResource(R.string.sources_add_warning))
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        label = { Text(stringResource(R.string.sources_add_hint)) },
                        supportingText = { Text(error ?: if (checking) stringResource(R.string.sources_checking) else "") },
                        isError = error != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
            }
        }
    }

    /** The accent colour presets (the first is the app's own green, saved as 0). */
    @JvmField
    val ACCENT_PRESETS = intArrayOf(
        0xFF7FD1A7.toInt(), 0xFF8AB4F8.toInt(), 0xFFC6A8FF.toInt(), 0xFFFFA6C9.toInt(),
        0xFFFF8A7A.toInt(), 0xFFFFB05C.toInt(), 0xFFE9C349.toInt(), 0xFF5CD6D6.toInt(),
    )

    /** The dialog for a colour of your own; calls [onChosen] with the ARGB colour when the user confirms. */
    @JvmStatic
    fun pickAccent(context: Context, current: Int, onChosen: java.util.function.IntConsumer) {
        SbaDialogs.show(context) { dismiss ->
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(if (current != 0) current else ACCENT_PRESETS[1], hsv)
            var hue by remember { mutableStateOf(hsv[0]) }
            var strength by remember { mutableStateOf(maxOf(0.2f, hsv[1]) * 100) }
            val chosen = android.graphics.Color.HSVToColor(floatArrayOf(hue, strength / 100f, 0.9f))
            AlertCard(
                title = stringResource(R.string.appearance_accent_custom),
                buttons = {
                    DialogButton(stringResource(android.R.string.cancel), dismiss)
                    DialogButton(stringResource(R.string.appearance_accent_use), { dismiss(); onChosen.accept(chosen or 0xFF000000.toInt()) })
                },
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().height(72.dp).background(Color(chosen), RoundedCornerShape(24.dp)))
                    AlertText(stringResource(R.string.appearance_accent_hue), Modifier.padding(top = 12.dp))
                    Slider(hue, { hue = it }, valueRange = 0f..359f)
                    AlertText(stringResource(R.string.appearance_accent_strength), Modifier.padding(top = 12.dp))
                    Slider(strength, { strength = it }, valueRange = 20f..100f)
                }
            }
        }
    }
}
