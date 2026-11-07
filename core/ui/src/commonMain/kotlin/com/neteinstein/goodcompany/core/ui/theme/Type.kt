package com.neteinstein.goodcompany.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.plus_jakarta_sans_bold
import com.neteinstein.goodcompany.core.ui.resources.plus_jakarta_sans_extrabold
import com.neteinstein.goodcompany.core.ui.resources.plus_jakarta_sans_medium
import com.neteinstein.goodcompany.core.ui.resources.plus_jakarta_sans_regular
import com.neteinstein.goodcompany.core.ui.resources.plus_jakarta_sans_semibold
import org.jetbrains.compose.resources.Font

/** Plus Jakarta Sans (OFL, docs/licenses) — friendly geometric sans with great numerals for dates. */
@Composable
fun appFontFamily(): FontFamily = FontFamily(
    Font(Res.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(Res.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(Res.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(Res.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

@Composable
internal fun appTypography(): Typography {
    val family = appFontFamily()
    val base = Typography()
    fun androidx.compose.ui.text.TextStyle.f(weight: FontWeight? = null, tracking: Float? = null) =
        copy(fontFamily = family, fontWeight = weight ?: fontWeight, letterSpacing = tracking?.sp ?: letterSpacing)
    return Typography(
        displayLarge = base.displayLarge.f(FontWeight.ExtraBold, -1f),
        displayMedium = base.displayMedium.f(FontWeight.ExtraBold, -0.75f),
        displaySmall = base.displaySmall.f(FontWeight.ExtraBold, -0.5f),
        headlineLarge = base.headlineLarge.f(FontWeight.ExtraBold, -0.5f),
        headlineMedium = base.headlineMedium.f(FontWeight.ExtraBold, -0.25f),
        headlineSmall = base.headlineSmall.f(FontWeight.Bold),
        titleLarge = base.titleLarge.f(FontWeight.Bold),
        titleMedium = base.titleMedium.f(FontWeight.Bold),
        titleSmall = base.titleSmall.f(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(FontWeight.SemiBold),
        labelMedium = base.labelMedium.f(FontWeight.SemiBold),
        labelSmall = base.labelSmall.f(FontWeight.SemiBold),
    )
}
