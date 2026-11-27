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

// Brand palette — warm and calm for readability: cream page, beige verse card, sage circles,
// deep green actions, gold accents (progress line, repeat).
val Emerald = Color(0xFF2D6B4F)
val EmeraldDeep = Color(0xFF174A3A)
val EmeraldSoft = Color(0xFFDDE6D8)
/** Gold accent (repeat icon, progress line, highlights). */
val Amber = Color(0xFFB8975A)
val AmberDeep = Color(0xFF7D5F28)
val AmberSoft = Color(0xFFF1E7D2)
/** Page background, and light text on deep green. */
val Cream = Color(0xFFF8F4EC)
val Parchment = Color(0xFFE2E7DC)
val Ink = Color(0xFF1C2420)
val InkMuted = Color(0xFF5F665F)
/** Sage circles and round buttons. */
val Jade = Color(0xFFDFE6DA)
val Night = Color(0xFFF1ECE2)
val Mint = Color(0xFFEDE8DC)        // beige verse card / tinted card
val MintStrong = Color(0xFFD6DFCF)
val Sand = Color(0xFFFCFAF5)        // light card

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
    surfaceContainerLow = Color(0xFFFBF8F2),
    surfaceContainer = Parchment,
    surfaceContainerHigh = Color(0xFFE6E1D5),
    outline = Color(0xFFC9C2B2),
    outlineVariant = Color(0xFFE3DDCF),
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
