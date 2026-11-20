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

// Brand palette
val Emerald = Color(0xFF0D5C3A)
val EmeraldDeep = Color(0xFF073F27)
val EmeraldSoft = Color(0xFFD6E9DD)
val Amber = Color(0xFFE5A93B)
val AmberDeep = Color(0xFF9A6A12)
val AmberSoft = Color(0xFFFBEBC8)
val Cream = Color(0xFFFFFDF6)
val Parchment = Color(0xFFEFEBE0)
val Ink = Color(0xFF1A1C17)
val InkMuted = Color(0xFF5E6157)
val Mint = Color(0xFFE3F1E7)        // soft card tint (reference: pastel cards)
val MintStrong = Color(0xFFC6E3CF)
val Sand = Color(0xFFF7F0DC)        // second soft card tint

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
    onSecondary = Color(0xFF2B1D00),
    secondaryContainer = AmberSoft,
    onSecondaryContainer = Color(0xFF4A3300),
    tertiary = AmberDeep,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF7EE),
    surfaceContainer = Parchment,
    surfaceContainerHigh = Color(0xFFE7E2D4),
    outline = Color(0xFFC9C3B2),
    outlineVariant = Color(0xFFE2DDCF),
)

private val BayyinahTypography = Typography().run {
    copy(
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
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
