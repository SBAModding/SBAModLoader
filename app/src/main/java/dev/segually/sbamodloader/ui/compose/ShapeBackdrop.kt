package dev.segually.sbamodloader.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A rounded blob: radius [r] with [lobes] soft bumps of relative depth [depth]. The same maths as `ShapeBackdropView`. */
fun blobPath(cx: Float, cy: Float, r: Float, lobes: Int, depth: Float, rotation: Float): Path {
    val out = Path()
    val steps = 180
    for (i in 0..steps) {
        val a = Math.PI * 2 * i / steps
        val rr = r * (1 + depth * cos(lobes * a + rotation))
        val x = (cx + rr * cos(a)).toFloat()
        val y = (cy + rr * sin(a)).toFloat()
        if (i == 0) out.moveTo(x, y) else out.lineTo(x, y)
    }
    out.close()
    return out
}

/** Decoration for hero cards: a scalloped "cookie", a flower and a circle in the theme's accent roles. Takes no input. */
@Composable
fun ShapeBackdrop(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tertiary = scheme.tertiary.copy(alpha = 0xE6 / 255f)
    val secondary = scheme.secondary.copy(alpha = 0xD9 / 255f)
    val primary = scheme.primary.copy(alpha = 0x59 / 255f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val unit = min(w, h)
        drawPath(blobPath(w - unit * 0.06f, unit * 0.12f, unit * 0.42f, 10, 0.055f, 0.3f), tertiary)
        drawPath(blobPath(w - unit * 0.1f, h - unit * 0.02f, unit * 0.27f, 6, 0.12f, 0.9f), secondary)
        drawCircle(primary, unit * 0.26f, Offset(unit * 0.12f, h - unit * 0.1f))
    }
}
