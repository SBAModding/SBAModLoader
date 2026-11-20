package dev.segually.sbamodloader.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.segually.sbamodloader.R
import kotlin.math.roundToInt

/** What the in-game overlay shows. */
data class OverlayState(
    val menuOpen: Boolean = false,
    val frozen: Boolean = false,
    /** The log overlay is a developer tool: its menu row only exists with developer features on. */
    val developer: Boolean = false,
    val logcatOpen: Boolean = false,
    val logLines: List<String> = emptyList(),
)

interface OverlayActions {
    fun closeMenu()
    fun toggleFreeze()
    fun toggleLogcat()
    fun exitToDashboard()
    fun clearLog()
    fun hideLog()
}

/** The in-game menu: a frozen chip, a sidebar with three actions, and the floating log. Touches fall through elsewhere. */
@Composable
fun GameOverlayScreen(state: OverlayState, actions: OverlayActions) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        if (state.frozen) {
            SbaText(
                stringResource(R.string.game_frozen),
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    .background(colors.surfaceContainer.copy(alpha = 0xD9 / 255f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
                color = colors.onSurface,
                style = TextStyle(fontFamily = SbaFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp),
            )
        }
        if (state.logcatOpen) LogcatPanel(state.logLines, actions)
        AnimatedVisibility(state.menuOpen, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(remember { MutableInteractionSource() }, indication = null) { actions.closeMenu() })
        }
        AnimatedVisibility(
            state.menuOpen,
            Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(tween(200)) { -(it + 48) },
            exit = slideOutHorizontally(tween(160)) { -(it + 48) },
        ) {
            Menu(state, actions)
        }
    }
}

@Composable
private fun Menu(state: OverlayState, actions: OverlayActions) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.padding(12.dp).width(288.dp)
            .background(colors.surfaceContainer.copy(alpha = 0xE6 / 255f), RoundedCornerShape(28.dp))
            .border(1.dp, colors.onSurface.copy(alpha = 0x2E / 255f), RoundedCornerShape(28.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .padding(12.dp),
    ) {
        SbaText(
            stringResource(R.string.game_menu_title), Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = colors.onSurface, style = MaterialTheme.typography.titleLargeEmphasized,
        )
        MenuRow(R.drawable.ic_pause, stringResource(R.string.game_menu_freeze), colors.onSurface, actions::toggleFreeze) {
            SbaSwitch(state.frozen, null)
        }
        if (state.developer) {
            MenuRow(
                R.drawable.ic_terminal,
                stringResource(if (state.logcatOpen) R.string.game_menu_logcat_close else R.string.game_menu_logcat_open),
                colors.onSurface, actions::toggleLogcat,
            ) { }
        }
        MenuRow(R.drawable.ic_exit, stringResource(R.string.game_menu_exit), colors.error, actions::exitToDashboard) { }
    }
}

@Composable
private fun MenuRow(icon: Int, label: String, tint: Color, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = tint)
        SbaText(label, Modifier.weight(1f).padding(start = 16.dp), color = tint, style = MaterialTheme.typography.bodyLarge)
        trailing()
    }
}

private const val VISIBLE_LINES = 12

/**
 * A small floating log. Only its header takes touches (to drag it); the log text takes none, so touches over it reach
 * the game underneath.
 */
@Composable
private fun BoxScope.LogcatPanel(lines: List<String>, actions: OverlayActions) {
    var drag by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val margin = with(LocalDensity.current) { 16.dp.roundToPx() }
    Column(
        Modifier.align(Alignment.TopEnd).padding(16.dp).width(380.dp).alpha(0.88f)
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) },
    ) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).background(Color(0xE61C211E))
                .pointerInput(Unit) { detectDragGestures { change, amount -> change.consume(); drag += amount } }
                .padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogText(stringResource(R.string.logcat_title), 12f, Color.White, bold = true, modifier = Modifier.weight(1f))
            LogText(stringResource(R.string.logcat_clear), 12f, Color(0xFF7FD1A7), modifier = Modifier.clickable { actions.clearLog() }.padding(8.dp))
            LogText("✕", 14f, Color.White, modifier = Modifier.clickable { actions.hideLog() }.padding(horizontal = 10.dp, vertical = 8.dp))
        }
        val tail = lines.takeLast(VISIBLE_LINES)
        val text: AnnotatedString = buildAnnotatedString {
            tail.forEachIndexed { i, l ->
                val color = when {
                    l.startsWith("E/") || l.startsWith("F/") -> Color(0xFFFF6B6B)
                    l.startsWith("W/") -> Color(0xFFFFC857)
                    l.startsWith("D/") || l.startsWith("V/") -> Color(0xFF9AA3AF)
                    else -> Color(0xFFE6E9EE)
                }
                withStyle(SpanStyle(color = color)) { append(l) }
                if (i < tail.size - 1) append('\n')
            }
        }
        // No pointer handling here: the log lets touches through.
        Box(Modifier.fillMaxWidth().height((VISIBLE_LINES * 12 + 12).dp).background(Color(0xB3000000)).padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.BottomStart) {
            androidx.compose.material3.Text(
                text, maxLines = VISIBLE_LINES, softWrap = false,
                style = TextStyle(fontFamily = SbaFontFamily, fontSize = 9.sp, lineHeight = 12.sp, color = Color(0xFFE6E9EE)),
            )
        }
    }
}

@Composable
private fun LogText(text: String, sp: Float, color: Color, modifier: Modifier = Modifier, bold: Boolean = false) {
    androidx.compose.material3.Text(
        text, modifier,
        style = TextStyle(fontFamily = SbaFontFamily, fontSize = sp.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
    )
}

/** A lifecycle and saved state for Compose inside a window that is not a ComponentActivity (the game's). */
class OverlayOwner : androidx.lifecycle.LifecycleOwner, androidx.savedstate.SavedStateRegistryOwner, androidx.lifecycle.ViewModelStoreOwner {
    private val registry = androidx.lifecycle.LifecycleRegistry(this)
    private val controller = androidx.savedstate.SavedStateRegistryController.create(this)
    override val viewModelStore = androidx.lifecycle.ViewModelStore()
    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry get() = controller.savedStateRegistry

    init {
        controller.performRestore(null)
        registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

object GameOverlayHost {
    /** The overlay's own recomposer: the window one reads the activity's application context, which the game's activity may not have. */
    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    class Session {
        internal val scope = kotlinx.coroutines.CoroutineScope(androidx.compose.ui.platform.AndroidUiDispatcher.CurrentThread)
        internal val recomposer = androidx.compose.runtime.Recomposer(androidx.compose.ui.platform.AndroidUiDispatcher.CurrentThread)

        init {
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
        }

        fun close() {
            recomposer.cancel()
            scope.cancel()
        }
    }

    /** What Compose needs inside one game window: a lifecycle and a recomposer, shared by everything drawn there. */
    class Env {
        val owner = OverlayOwner()
        val session = Session()
        fun destroy() { session.close(); owner.destroy() }
    }

    private val envs = java.util.WeakHashMap<android.app.Activity, Env>()

    @JvmStatic
    @Synchronized
    fun env(activity: android.app.Activity): Env = envs.getOrPut(activity) { Env() }

    @JvmStatic
    @Synchronized
    fun release(activity: android.app.Activity) {
        envs.remove(activity)?.destroy()
    }

    /** Puts Compose content into [host], a view inside the game's window. */
    fun attachContent(host: android.view.ViewGroup, env: Env, content: @Composable () -> Unit): androidx.compose.ui.platform.ComposeView {
        // Compose looks for the owners from the top of the window, so they go on the decor view, as ComponentActivity does.
        val root = host.rootView
        root.setViewTreeLifecycleOwner(env.owner)
        root.setViewTreeViewModelStoreOwner(env.owner)
        root.setViewTreeSavedStateRegistryOwner(env.owner)
        val view = androidx.compose.ui.platform.ComposeView(host.context)
        view.setParentCompositionContext(env.session.recomposer)
        view.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent { SbaTheme(content) }
        host.addView(view, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        return view
    }

    @JvmStatic
    fun attach(host: android.view.ViewGroup, env: Env, state: androidx.compose.runtime.State<OverlayState>, actions: OverlayActions) =
        attachContent(host, env) { GameOverlayScreen(state.value, actions) }
}

/** Reads this process's log in the background, a line at a time, keeping the last few hundred. */
class LogcatSource(private val onLines: (List<String>) -> Unit) {
    private val lines = java.util.ArrayDeque<String>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var process: java.lang.Process? = null
    private var reader: Thread? = null
    private var queued = false
    private val publish = Runnable { queued = false; onLines(lines.toList()) }

    fun start() {
        if (reader != null) return
        reader = Thread({
            try {
                process = ProcessBuilder("logcat", "-v", "brief", "--pid=" + android.os.Process.myPid(), "-T", "60").redirectErrorStream(true).start()
                process!!.inputStream.bufferedReader().use { input ->
                    while (!Thread.currentThread().isInterrupted) {
                        val line = input.readLine() ?: break
                        handler.post { push(line) }
                    }
                }
            } catch (_: Exception) {
                // The panel just stays empty if logcat cannot be started.
            }
        }, "logcat-overlay").apply { isDaemon = true; start() }
    }

    fun stop() {
        reader?.interrupt(); reader = null
        process?.destroy(); process = null
        handler.removeCallbacks(publish); queued = false
    }

    fun clear() { lines.clear(); onLines(emptyList()) }

    private fun push(line: String) {
        if (lines.size >= 400) lines.removeFirst()
        lines.addLast(line)
        if (!queued) { queued = true; handler.postDelayed(publish, 250) }
    }
}
