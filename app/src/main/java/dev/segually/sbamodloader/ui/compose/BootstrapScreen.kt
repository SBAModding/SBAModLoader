package dev.segually.sbamodloader.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.segually.sbamodloader.R

/** What the launch screen shows. */
data class BootstrapState(
    val status: String = "",
    /** The spinner shows while the work has no progress of its own. */
    val spinner: Boolean = true,
    /** The download bar: hidden, or filled to [progress] 0..1, or running without an end when [progress] is null. */
    val bar: Boolean = false,
    val progress: Float? = null,
    val details: String? = null,
)

/** The screen between pressing Launch and the game: a spinner or a download bar, and a line saying what is going on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BootstrapScreen(state: BootstrapState) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.spinner) LoadingIndicator(Modifier.size(96.dp))
        if (state.bar) {
            val modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            val progress = state.progress
            if (progress == null) LinearWavyProgressIndicator(modifier = modifier)
            else LinearWavyProgressIndicator(progress = { progress }, modifier = modifier)
        }
        SbaText(
            stringResource(R.string.bootstrap_title), Modifier.fillMaxWidth().padding(top = 28.dp), color = colors.onSurface,
            style = LocalSbaText.current.hero.copy(textAlign = TextAlign.Center),
        )
        SbaText(
            state.status, Modifier.fillMaxWidth().padding(top = 12.dp), color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        )
        state.details?.let {
            SbaText(it, Modifier.fillMaxWidth().padding(top = 6.dp), color = colors.onSurfaceVariant, style = LocalSbaText.current.caption.copy(textAlign = TextAlign.Center))
        }
    }
}

object BootstrapScreenHost {
    @JvmStatic
    fun setContent(view: androidx.compose.ui.platform.ComposeView, state: androidx.compose.runtime.State<BootstrapState>) {
        view.setContent { SbaTheme { BootstrapScreen(state.value) } }
    }
}
