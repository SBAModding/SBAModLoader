package dev.segually.sbamodloader.ui.compose

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.segually.sbamodloader.R

data class LoadingState(val text: String? = null, val visible: Boolean = true)

/**
 * The spinner and line of text laid over the game while Fusion starts. It lives in the game's window; native code
 * drives it through `JniBridge` (SetLoadingState, SetLoadingText).
 */
object LoadingOverlay {
    private val states = java.util.WeakHashMap<Activity, androidx.compose.runtime.MutableState<LoadingState>>()

    @JvmStatic
    @Synchronized
    fun attach(activity: Activity, ui: Context) {
        val state = mutableStateOf(LoadingState())
        states[activity] = state
        val decor = activity.window.decorView as ViewGroup
        GameOverlayHost.attachContent(decor, GameOverlayHost.env(activity)) { Content(state.value) }
    }

    /** Shows or hides it. Returns false when there is none (no Fusion, or not yet attached). */
    @JvmStatic
    @Synchronized
    fun setVisible(activity: Activity, visible: Boolean): Boolean {
        val state = states[activity] ?: return false
        state.value = state.value.copy(visible = visible)
        return true
    }

    @JvmStatic
    @Synchronized
    fun setText(activity: Activity, text: String): Boolean {
        val state = states[activity] ?: return false
        state.value = state.value.copy(text = text)
        return true
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun Content(state: LoadingState) {
        if (!state.visible) return
        val line = state.text ?: stringResource(R.string.loading)
        val outline = with(LocalDensity.current) { 3.dp.toPx() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator(Modifier.size(128.dp))
                val style = MaterialTheme.typography.headlineSmallEmphasized.copy(textAlign = TextAlign.Center)
                // White text with a black outline, so it reads over any game scene: the outline is drawn first.
                Box(Modifier.padding(2.dp)) {
                    SbaText(line, color = Color.Black, style = style.copy(drawStyle = Stroke(outline, join = StrokeJoin.Round, cap = StrokeCap.Round)))
                    SbaText(line, color = Color.White, style = style)
                }
            }
        }
    }
}
