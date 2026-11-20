package dev.segually.sbamodloader.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.segually.sbamodloader.R
import dev.segually.sbamodloader.tools.ModSettings
import dev.segually.sbamodloader.tools.ModSettingsStore
import dev.segually.sbamodloader.ui.compose.ChoiceGroup
import dev.segually.sbamodloader.ui.compose.DialogButton
import dev.segually.sbamodloader.ui.compose.LocalSbaText
import dev.segually.sbamodloader.ui.compose.RowBody
import dev.segually.sbamodloader.ui.compose.RowTitle
import dev.segually.sbamodloader.ui.compose.SbaDialogs
import dev.segually.sbamodloader.ui.compose.SbaSwitch
import dev.segually.sbamodloader.ui.compose.SbaText
import java.io.IOException
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The screen that is made from a mod's declared settings: a switch for a bool, a slider for a number, buttons or a menu
 * for a choice, a text field for text. Every change is saved at once; the game picks them up the next time it starts.
 */
object ModSettingsSheet {
    @JvmStatic
    fun show(context: Context, modName: String, settings: ModSettings) {
        SbaDialogs.show(context, sheet = true) { dismiss ->
            Sheet(context, modName, settings, reopen = { dismiss(); show(context, modName, settings) })
        }
    }

    @Composable
    private fun Sheet(context: Context, modName: String, settings: ModSettings, reopen: () -> Unit) {
        val colors = MaterialTheme.colorScheme
        // What is saved, kept in state so the rows follow it.
        val values = remember { mutableStateMapOf<String, String>().also { it.putAll(ModSettingsStore.load(settings)) } }
        fun current(f: ModSettings.Field) = values[f.key] ?: f.defaultValue
        fun save(f: ModSettings.Field, value: String) {
            try {
                values[f.key] = ModSettingsStore.set(settings, f.key, value)
            } catch (e: IOException) {
                Toast.makeText(context, e.message ?: e.toString(), Toast.LENGTH_LONG).show()
            }
        }
        Surface(
            Modifier.fillMaxWidth().heightIn(max = 720.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = colors.surfaceContainerLow,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                SbaText(modName, color = colors.onSurface, style = MaterialTheme.typography.headlineSmallEmphasized)
                SbaText(stringResource(R.string.mod_settings_note), Modifier.padding(top = 4.dp, bottom = 8.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                settings.fields.forEach { f ->
                    Surface(Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh) {
                        Column(Modifier.padding(16.dp)) {
                            when (f.type) {
                                ModSettings.Type.BOOL -> BoolRow(f, current(f) == "true") { save(f, if (it) "true" else "false") }
                                ModSettings.Type.INT, ModSettings.Type.FLOAT -> NumberRow(f, current(f)) { save(f, it) }
                                ModSettings.Type.CHOICE -> ChoiceRow(f, current(f)) { save(f, it) }
                                else -> StringRow(f, current(f)) { save(f, it) }
                            }
                        }
                    }
                }
                Row(Modifier.padding(top = 16.dp)) {
                    DialogButton(stringResource(R.string.mod_settings_reset), {
                        try {
                            ModSettingsStore.reset(settings)
                        } catch (e: IOException) {
                            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                        }
                        reopen()
                    })
                }
            }
        }
    }

    @Composable
    private fun Titles(f: ModSettings.Field, modifier: Modifier = Modifier) {
        Column(modifier) {
            RowTitle(f.label)
            if (f.summary.isNotEmpty()) RowBody(f.summary)
        }
    }

    @Composable
    private fun BoolRow(f: ModSettings.Field, on: Boolean, onChange: (Boolean) -> Unit) {
        // The state is kept here so the switch moves at once.
        var checked by remember(f.key) { mutableStateOf(on) }
        Row(Modifier.fillMaxWidth().clickable { checked = !checked; onChange(checked) }, verticalAlignment = Alignment.CenterVertically) {
            Titles(f, Modifier.weight(1f))
            SbaSwitch(checked, null)
        }
    }

    @Composable
    private fun NumberRow(f: ModSettings.Field, saved: String, onChange: (String) -> Unit) {
        val isInt = f.type == ModSettings.Type.INT
        // A slider needs a whole number of steps between its ends.
        val steps = max(1, min(1000, ((f.max - f.min) / f.step).roundToInt()))
        val stepSize = (f.max - f.min) / steps
        fun snap(value: Double): Float = (f.min + max(0, min(steps, ((value - f.min) / stepSize).roundToInt())) * stepSize).toFloat()
        var value by remember(f.key) { mutableStateOf(snap(saved.toDoubleOrNull() ?: f.min)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Titles(f, Modifier.weight(1f))
            SbaText(format(value, isInt), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMediumEmphasized)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(if (isInt) value.roundToLong().toString() else value.toDouble().toString()) },
            valueRange = f.min.toFloat()..f.max.toFloat(),
            steps = steps - 1,
        )
    }

    private fun format(v: Float, isInt: Boolean) =
        if (isInt) v.roundToLong().toString()
        else String.format(Locale.ROOT, "%.2f", v).replace(Regex("0+$"), "").replace(Regex("\\.$"), "")

    @Composable
    private fun ChoiceRow(f: ModSettings.Field, saved: String, onChange: (String) -> Unit) {
        var selected by remember(f.key) { mutableStateOf(saved) }
        Titles(f)
        if (f.choices.size <= 4) {
            ChoiceGroup(f.choices.map { it.label }, f.choices.indexOfFirst { it.value == selected }.coerceAtLeast(0), {
                selected = f.choices[it].value
                onChange(selected)
            }, Modifier.padding(top = 12.dp))
        } else {
            var open by remember { mutableStateOf(false) }
            val label = f.choices.firstOrNull { it.value == selected }?.label ?: selected
            OutlinedButton({ open = true }, Modifier.padding(top = 12.dp)) { Text("$label ▾") }
            DropdownMenu(open, { open = false }) {
                f.choices.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice.label) }, onClick = { open = false; selected = choice.value; onChange(choice.value) })
                }
            }
        }
    }

    @Composable
    private fun StringRow(f: ModSettings.Field, saved: String, onChange: (String) -> Unit) {
        var text by remember(f.key) { mutableStateOf(saved) }
        Titles(f)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onChange(it) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
        )
    }
}
