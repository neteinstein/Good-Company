package com.neteinstein.goodcompany.core.ui

import com.neteinstein.goodcompany.core.ui.components.SheetDrag
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SheetDragTest {
    private fun dismisses(offset: Float, height: Float = 900f, velocity: Float = 0f) =
        SheetDrag.shouldDismiss(offset, height, velocity, positionalThreshold = 300f, velocityThreshold = 1500f)

    @Test fun anUntouchedSheetStaysOpen() {
        assertFalse(dismisses(offset = 0f, velocity = 5000f))
        assertFalse(dismisses(offset = 100f, height = 0f))
    }

    @Test fun aShortSlowPullSpringsBack() = assertFalse(dismisses(offset = 200f))

    @Test fun aLongPullCloses() = assertTrue(dismisses(offset = 300f))

    @Test fun aShortSheetClosesAfterAThirdOfItsHeight() {
        assertTrue(dismisses(offset = 100f, height = 300f))
        assertFalse(dismisses(offset = 99f, height = 300f))
    }

    @Test fun aFastDownwardFlingCloses() = assertTrue(dismisses(offset = 20f, velocity = 1500f))

    @Test fun anUpwardFlingKeepsItOpen() = assertFalse(dismisses(offset = 600f, velocity = -1500f))
}
