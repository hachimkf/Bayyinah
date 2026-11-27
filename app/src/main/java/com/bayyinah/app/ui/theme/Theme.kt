package com.bayyinah.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.bayyinah.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand palette — greens and white only (Material 3 Expressive look: pale green background,
// white cards, deep green actions, a soft lime glow for accents).
val Emerald = Color(0xFF1F6B47)
val EmeraldDeep = Color(0xFF0F3D2A)
val EmeraldSoft = Color(0xFFD3EBDA)
/** Lime accent (was amber): icons on deep green, active states, glows. */
val Amber = Color(0xFFC6EF8A)
val AmberDeep = Color(0xFF3E6B12)
val AmberSoft = Color(0xFFE6F6D2)
/** Page background, and light text on deep green. */
val Cream = Color(0xFFF3F9F3)
val Parchment = Color(0xFFE2EDE4)
val Ink = Color(0xFF14211A)
val InkMuted = Color(0xFF55665B)
/** Floating bar and round buttons. */
val Jade = Color(0xFFCBE7D2)
/** Background of the home page (light). */
val Night = Color(0xFFE8F3EA)
val Mint = Color(0xFFE1F0E4)        // tinted card
val MintStrong = Color(0xFFC3E3CB)
val Sand = Color(0xFFFFFFFF)        // white card

/**
 * KFGQPC Warsh Uthmanic Script (King Fahd Complex). Required: the Warsh text uses codepoints
 * (Arabic Extended-B alefs, open tanween) that general Arabic fonts can't draw.
 */
val QuranFont = FontFamily(Font(R.font.warsh_uthmanic))

private val BayyinahColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Cream,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = EmeraldDeep,
    secondary = Amber,
    onSecondary = EmeraldDeep,
    secondaryContainer = AmberSoft,
    onSecondaryContainer = AmberDeep,
    tertiary = AmberDeep,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FBF7),
    surfaceContainer = Parchment,
    surfaceContainerHigh = Color(0xFFD9E7DC),
    outline = Color(0xFFB5C7BA),
    outlineVariant = Color(0xFFD5E2D8),
)

private val BayyinahTypography = Typography().run {
    copy(
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    )
}

/** Large, bold, high-contrast style for the matched verse. */
val VerseTextStyle = TextStyle(
    fontFamily = QuranFont,
    fontSize = 34.sp,
    lineHeight = 64.sp, // generous: tashkeel above and below the line
    color = Ink,
)

private val BayyinahShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BayyinahTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = BayyinahColors,
        typography = BayyinahTypography,
        shapes = BayyinahShapes,
        motionScheme = MotionScheme.expressive(), // springy, bouncy spatial motion
        content = content,
    )
}
