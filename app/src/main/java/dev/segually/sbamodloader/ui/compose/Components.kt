package dev.segually.sbamodloader.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.segually.sbamodloader.R

/** The 28dp card every page uses (`Widget.SBA.Card`). */
@Composable
fun SbaCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) { Column { content() } }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
/** A small coloured heading above a card (`TextAppearance.SBA.Section`). */
@Composable
fun SectionLabel(text: String) {
    SbaText(
        text,
        Modifier.padding(start = 12.dp, top = 28.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = LocalSbaText.current.section,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
/** The bold line of a row (`TextAppearance.SBA.RowTitle`). */
@Composable
fun RowTitle(text: String, modifier: Modifier = Modifier) {
    SbaText(text, modifier, color = MaterialTheme.colorScheme.onSurface, style = LocalSbaText.current.rowTitle)
}

/** The quieter line under it (`TextAppearance.SBA.RowBody`). */
@Composable
fun RowBody(text: String, modifier: Modifier = Modifier) {
    SbaText(text, modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, style = LocalSbaText.current.rowBody)
}

/** A switch with a check or a cross in its thumb (`Widget.SBA.Switch`). */
@Composable
fun SbaSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = {
            Icon(painterResource(if (checked) R.drawable.ic_check else R.drawable.ic_close), null, Modifier.size(16.dp))
        },
    )
}

/** A setting with a switch: the whole row toggles it. */
@Composable
fun SwitchRow(title: String, description: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            RowTitle(title)
            if (description != null) RowBody(description, Modifier.padding(top = 2.dp))
        }
        SbaSwitch(checked, null, Modifier.padding(start = 16.dp))
    }
}

/**
 * Pick one of a few: outlined buttons side by side, the chosen one filled (`MaterialButtonToggleGroup` with
 * `toggle_bg` / `toggle_text`). The outer ends are round, the inner corners small.
 */
@Composable
fun ChoiceGroup(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { index, label ->
            val checked = index == selected
            val outer = CornerSize(50)
            val inner = CornerSize(8.dp)
            val shape = if (checked) RoundedCornerShape(50) else RoundedCornerShape(
                topStart = if (index == 0) outer else inner,
                bottomStart = if (index == 0) outer else inner,
                topEnd = if (index == options.lastIndex) outer else inner,
                bottomEnd = if (index == options.lastIndex) outer else inner,
            )
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = shape,
                color = if (checked) colors.secondaryContainer else Color.Transparent,
                contentColor = if (checked) colors.onSecondaryContainer else colors.onSurface,
                border = if (checked) null else BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Text that takes the height a TextView gave it. A TextView's last line is the font's natural height (about 1.52 times
 * the size for Roboto Flex), which is more than the line height of the Material styles; Compose makes every line
 * exactly the line height. The difference is added under the text so that pages keep the same height as the Views.
 */
@Composable
fun SbaText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
) {
    val extra = with(androidx.compose.ui.platform.LocalDensity.current) {
        val diff = style.fontSize.value * 1.52f - style.lineHeight.value
        if (style.fontSize.isSp && style.lineHeight.isSp && diff > 0f) diff.sp.toDp() else 0.dp
    }
    Text(text, modifier.padding(bottom = extra), color = color, style = style, maxLines = maxLines, overflow = overflow)
}
