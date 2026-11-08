package com.neteinstein.goodcompany.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.neteinstein.goodcompany.core.ui.theme.AvatarGradients
import com.neteinstein.goodcompany.core.ui.theme.OnColorGradients
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/** White text on our gradient cards must stay readable in every theme (WCAG AA: 4.5:1 for body text). */
class ColorContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    /** The dimmest text we put on these cards is white at 85 % opacity. */
    private fun assertReadable(background: Color, label: String) {
        val text = Color.White.copy(alpha = 0.85f).compositeOver(background)
        val ratio = contrast(text, background)
        assertTrue(ratio >= 4.5f, "$label $background: contrast $ratio < 4.5")
    }

    @Test
    fun onColorGradientsKeepWhiteTextReadable() {
        OnColorGradients.all.flatten().forEach { assertReadable(it, "gradient stop") }
    }

    @Test
    fun avatarGradientsKeepWhiteTextReadable() {
        AvatarGradients.forEach { (start, end) ->
            assertReadable(start, "avatar start")
            assertReadable(end, "avatar end")
        }
    }

    @Test
    fun brandRedReadsOnWhiteChip() {
        assertTrue(contrast(OnColorGradients.BrandRed, Color.White) >= 4.5f)
    }
}
