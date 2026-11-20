package dev.segually.sbamodloader.ui.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class DropTargetTest {
    private fun box(x: Float, y: Float) = Rect(Offset(x, y), Size(100f, 50f))

    // One column: three mods, then a plugin that cannot be moved.
    private val column = listOf(box(0f, 0f), box(0f, 60f), box(0f, 120f), box(0f, 180f))
    private val orderable = listOf(true, true, true, false)

    @Test fun overAnotherRowPicksIt() = assertEquals(1, dropTarget(column, orderable, 0, Offset(50f, 80f)))
    @Test fun overItselfPicksNothing() = assertEquals(-1, dropTarget(column, orderable, 0, Offset(50f, 20f)))
    @Test fun overTheGapPicksNothing() = assertEquals(-1, dropTarget(column, orderable, 0, Offset(50f, 55f)))
    @Test fun aPluginIsNotATarget() = assertEquals(-1, dropTarget(column, orderable, 2, Offset(50f, 200f)))
    @Test fun movesUp() = assertEquals(0, dropTarget(column, orderable, 2, Offset(50f, 10f)))

    @Test fun twoColumnsMoveSideways() {
        val grid = listOf(box(0f, 0f), box(110f, 0f), box(0f, 60f), box(110f, 60f))
        val all = listOf(true, true, true, true)
        assertEquals(1, dropTarget(grid, all, 0, Offset(150f, 20f)))
        assertEquals(3, dropTarget(grid, all, 0, Offset(150f, 80f)))
    }

    @Test fun rowsNotMeasuredYetAreSkipped() = assertEquals(-1, dropTarget(listOf(null, null), listOf(true, true), 0, Offset(10f, 10f)))
}
