package dev.segually.sbamodloader.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dev.segually.sbamodloader.R
import dev.segually.sbamodloader.ui.AccentSwatches

/** A mod repository in the Mod sources card. The official one is always first and has no switch or remove button. */
data class SourceRow(val name: String, val detail: String, val enabled: Boolean, val official: Boolean)

data class SettingsState(
    val themeMode: Int = 0,
    val layoutMode: Int = 0,
    val accentSupported: Boolean = true,
    val materialYou: Boolean = true,
    /** The chosen accent as ARGB, 0 for the app's own green. */
    val accent: Int = 0,
    val autoUpdate: Boolean = false,
    val prereleases: Boolean = false,
    /** The line under the update switches, or null when there is none. */
    val updateStatus: String? = null,
    val updateButton: String = "",
    val updateButtonEnabled: Boolean = true,
    val fusion: Boolean = false,
    val skipFusion: Boolean = false,
    val libunity: Boolean = false,
    val developer: Boolean = false,
    val patchInfo: String = "",
    val patchUrl: String = "",
    val patchUrlError: String? = null,
    val sources: List<SourceRow> = emptyList(),
    val dataPath: String = "",
    val aboutVersion: String = "",
    val aboutBuild: String = "",
)

/** What the page can ask the app to do. Implemented by the fragment, which owns the settings and the dialogs. */
interface SettingsActions {
    fun setThemeMode(mode: Int)
    fun setLayoutMode(mode: Int)
    fun setMaterialYou(on: Boolean)
    fun setAccent(argb: Int)
    fun pickCustomAccent()
    fun setAutoUpdate(on: Boolean)
    fun setPrereleases(on: Boolean)
    fun checkForUpdate()
    fun setFusion(on: Boolean)
    fun setSkipFusion(on: Boolean)
    fun setLibunity(on: Boolean)
    fun setDeveloper(on: Boolean)
    fun savePatchUrl(url: String)
    fun loadLocalPatch()
    fun exportAssemblies()
    fun removePatch()
    fun addRepository()
    fun setRepositoryEnabled(index: Int, on: Boolean)
    fun removeRepository(index: Int)
    fun openGithub()
    fun openDiscord()
    fun onVersionTap()
}

@Composable
fun SettingsScreen(state: SettingsState, actions: SettingsActions) {
    Column(Modifier.fillMaxWidth()) {
        SafetyCard(actions)

        SectionLabel(stringResource(R.string.settings_section_appearance))
        SbaCard { AppearanceCard(state, actions) }

        SectionLabel(stringResource(R.string.updates_title))
        SbaCard { UpdatesCard(state, actions) }

        SectionLabel(stringResource(R.string.settings_section_sources))
        SbaCard { SourcesCard(state, actions) }

        SectionLabel(stringResource(R.string.settings_section_game))
        SbaCard {
            SwitchRow(stringResource(R.string.settings_fusion_title), stringResource(R.string.settings_fusion_desc), state.fusion, actions::setFusion)
            SwitchRow(stringResource(R.string.settings_skipfusion_title), stringResource(R.string.settings_skipfusion_desc), state.skipFusion, actions::setSkipFusion)
            if (state.developer) {
                SwitchRow(stringResource(R.string.settings_libunity_title), stringResource(R.string.settings_libunity_desc), state.libunity, actions::setLibunity)
            }
        }

        SectionLabel(stringResource(R.string.settings_section_developer))
        SbaCard { DeveloperCard(state, actions) }

        SectionLabel(stringResource(R.string.settings_about_title))
        SbaCard { AboutCard(state, actions) }
    }
}

@Composable
private fun SafetyCard(actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    SbaCard(container = colors.surfaceContainerHigh) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(28.dp), tint = colors.error)
                Text(
                    stringResource(R.string.safety_title),
                    Modifier.weight(1f).padding(start = 12.dp),
                    color = colors.error,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
            Text(
                stringResource(R.string.safety_body),
                Modifier.padding(top = 12.dp, bottom = 1.dp).fillMaxWidth(),
                color = colors.onSurface,
                style = LocalSbaText.current.rowBody,
            )
            Row(Modifier.padding(top = 16.dp)) {
                TonalAction(stringResource(R.string.safety_open_github), actions::openGithub, Modifier)
                TonalAction(stringResource(R.string.safety_join_discord), actions::openDiscord, Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * A tonal button as MaterialButton draws it: a [height] layout box with 4dp of inset above and below, so the visible
 * button is 8dp shorter. Keeps the Compose page the same size as the Views one was.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TonalAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, fill: Boolean = false, height: Int = 56) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier).height(height.dp).padding(vertical = 4.dp),
        shapes = ButtonDefaults.shapes(),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun AppearanceCard(state: SettingsState, actions: SettingsActions) {
    Column {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            RowTitle(stringResource(R.string.appearance_theme))
            ChoiceGroup(
                listOf(R.string.appearance_theme_system, R.string.appearance_theme_light, R.string.appearance_theme_dark).map { stringResource(it) },
                state.themeMode, actions::setThemeMode, Modifier.padding(top = 12.dp),
            )
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            RowTitle(stringResource(R.string.appearance_layout))
            RowBody(stringResource(R.string.appearance_layout_desc), Modifier.padding(top = 2.dp))
            ChoiceGroup(
                listOf(R.string.appearance_layout_auto, R.string.appearance_layout_phone, R.string.appearance_layout_desktop).map { stringResource(it) },
                state.layoutMode, actions::setLayoutMode, Modifier.padding(top = 12.dp),
            )
        }
        // Without dynamic colours (Android 11 and older) there is nothing to choose: the app's own colours are used.
        if (state.accentSupported) {
            SwitchRow(stringResource(R.string.appearance_material_you), stringResource(R.string.appearance_material_you_desc), state.materialYou, actions::setMaterialYou)
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                RowTitle(stringResource(R.string.appearance_accent), Modifier.padding(horizontal = 20.dp))
                RowBody(
                    stringResource(if (state.materialYou) R.string.appearance_accent_off else R.string.appearance_accent_desc),
                    Modifier.padding(horizontal = 20.dp).padding(top = 2.dp),
                )
                AccentRow(state, actions, Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun AccentRow(state: SettingsState, actions: SettingsActions, modifier: Modifier) {
    val enabled = !state.materialYou
    val presets = AccentSwatches.PRESETS
    val custom = state.accent != 0 && presets.drop(1).none { it == state.accent }
    Row(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        presets.forEachIndexed { i, argb ->
            val stored = if (i == 0) 0 else argb
            Swatch(argb, state.accent == stored, enabled, "#%06X".format(argb and 0xFFFFFF)) { actions.setAccent(stored) }
        }
        Swatch(if (custom) state.accent else 0xFF3A3F3C.toInt(), custom, enabled,
            if (custom) "#%06X".format(state.accent and 0xFFFFFF) else stringResource(R.string.appearance_accent_custom),
            plus = !custom) { actions.pickCustomAccent() }
    }
}

@Composable
private fun Swatch(argb: Int, selected: Boolean, enabled: Boolean, description: String, plus: Boolean = false, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    val light = androidx.core.graphics.ColorUtils.calculateLuminance(argb) > 0.5
    Box(
        Modifier.size(56.dp).background(Color(argb), CircleShape)
            .then(if (selected) Modifier.border(3.dp, ring, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(24.dp), tint = if (light) Color.Black else Color.White)
        if (plus) Icon(painterResource(R.drawable.ic_add), null, Modifier.size(24.dp), tint = Color.White)
    }
}

@Composable
private fun UpdatesCard(state: SettingsState, actions: SettingsActions) {
    Column {
        SwitchRow(stringResource(R.string.updates_auto), null, state.autoUpdate, actions::setAutoUpdate)
        SwitchRow(stringResource(R.string.updates_prereleases), stringResource(R.string.updates_prereleases_desc), state.prereleases, actions::setPrereleases)
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp)) {
            state.updateStatus?.let { RowBody(it, Modifier.fillMaxWidth().padding(bottom = 12.dp)) }
            TonalAction(state.updateButton, actions::checkForUpdate, enabled = state.updateButtonEnabled)
        }
    }
}

@Composable
private fun SourcesCard(state: SettingsState, actions: SettingsActions) {
    Column {
        state.sources.forEachIndexed { index, row ->
            // `index` counts the official repository too, so the extras are index - 1 in the settings list.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 20.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    RowTitle(row.name)
                    Text(
                        row.detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = LocalSbaText.current.rowBody,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis,
                    )
                }
                if (row.official) {
                    Icon(painterResource(R.drawable.ic_verified), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                } else {
                    SbaSwitch(row.enabled, { actions.setRepositoryEnabled(index - 1, it) })
                    IconButton(onClick = { actions.removeRepository(index - 1) }, Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.sources_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        FilledTonalButton(
            onClick = actions::addRepository,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp).padding(vertical = 4.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sources_add), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeveloperCard(state: SettingsState, actions: SettingsActions) {
    Column {
        // Anything technical is only there once developer features are on, which seven taps on the version turn on.
        if (state.developer) {
            SwitchRow(stringResource(R.string.settings_developer_title), stringResource(R.string.settings_developer_desc), state.developer, actions::setDeveloper)
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                RowTitle(stringResource(R.string.patch_section_title))
                RowBody(state.patchInfo, Modifier.padding(top = 2.dp))
                var url by remember(state.patchUrl) { mutableStateOf(state.patchUrl) }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    label = { Text(stringResource(R.string.settings_patch_url_hint)) },
                    supportingText = { Text(state.patchUrlError ?: stringResource(R.string.settings_patch_url_help)) },
                    isError = state.patchUrlError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                )
                TonalAction(stringResource(R.string.settings_patch_url_save), { actions.savePatchUrl(url.trim()) }, Modifier.padding(top = 4.dp), fill = true)
                TonalAction(stringResource(R.string.patch_load_local), actions::loadLocalPatch, Modifier.padding(top = 8.dp), fill = true)
                RowBody(stringResource(R.string.export_assemblies_info), Modifier.fillMaxWidth().padding(top = 20.dp))
                TonalAction(stringResource(R.string.export_assemblies), actions::exportAssemblies, Modifier.padding(top = 4.dp), fill = true)
                TextButton(
                    onClick = actions::removePatch,
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.patch_remove), style = MaterialTheme.typography.labelLarge) }
            }
            Column(Modifier.fillMaxWidth()) {
                RowTitle(stringResource(R.string.settings_data_location), Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
                SelectionContainer {
                    RowBody(state.dataPath, Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun AboutCard(state: SettingsState, actions: SettingsActions) {
    Column {
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { actions.onVersionTap() }.padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
            RowTitle(state.aboutVersion)
        }
        SelectionContainer {
            RowBody(state.aboutBuild, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp))
        }
    }
}

/** The Java fragment's way in. */
object SettingsScreenHost {
    @JvmStatic
    fun setContent(
        view: androidx.compose.ui.platform.ComposeView,
        state: androidx.compose.runtime.State<SettingsState>,
        actions: SettingsActions,
    ) {
        view.setContent { SbaTheme { SettingsScreen(state.value, actions) } }
    }
}
