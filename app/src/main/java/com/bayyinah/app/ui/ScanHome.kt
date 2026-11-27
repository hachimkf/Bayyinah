package com.bayyinah.app.ui

import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayyinah.app.R
import com.bayyinah.app.data.Verse
import com.bayyinah.app.ui.theme.Amber
import com.bayyinah.app.ui.theme.Cream
import com.bayyinah.app.ui.theme.Emerald
import com.bayyinah.app.ui.theme.EmeraldDeep
import com.bayyinah.app.ui.theme.Jade
import com.bayyinah.app.ui.theme.QuranFont
import com.bayyinah.app.ui.theme.VerseTextStyle

/** Deep green of the vignette around the lit frame. */
private val Shade = Color(0xFF0B241A)
/** Paper of the illustrated page. */
private val Paper = Color(0xFFF1F3EC)

/** Home's own frame (taller than the camera's: it holds the title band and three lines). */
private const val HOME_TOP = 0.24f
private const val HOME_BOTTOM = 0.60f

/**
 * Home is the scan itself: a Mushaf page fills the screen, a dark green vignette leaves only the
 * framed part lit, the scan line sweeps, and one huge button opens the camera.
 * Top: the app logo (opens the whole Mushaf: index of surahs) and settings, as circles.
 */
@Composable
fun ScanHomeScreen(vm: MainViewModel, state: UiState, update: UpdateUi, sample: List<Verse>) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Paper)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.openScanner() },
    ) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val frame = remember(w, h) { RectF(w * 0.05f, h * HOME_TOP, w * 0.95f, h * HOME_BOTTOM) }
        val frameTop = with(density) { frame.top.toDp() }
        val frameH = with(density) { frame.height().toDp() }

        // The page: a surah title band and its ayat with numbered medallions, centred in the frame.
        Box(Modifier.fillMaxWidth().offset(y = frameTop).height(frameH), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).padding(horizontal = 34.dp),
            ) {
                TitleBand("سورة الفلق")
                PageText(sample)
                TitleBand("سورة الناس")
            }
        }
        // Vignette: dark green above and below, the frame stays bright.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Shade, (HOME_TOP - 0.10f) to Shade.copy(alpha = 0.92f), (HOME_TOP - 0.02f) to Shade.copy(alpha = 0.55f),
                    (HOME_TOP + 0.02f) to Shade.copy(alpha = 0f), (HOME_BOTTOM - 0.02f) to Shade.copy(alpha = 0f),
                    (HOME_BOTTOM + 0.02f) to Shade.copy(alpha = 0.55f), (HOME_BOTTOM + 0.12f) to Shade.copy(alpha = 0.94f), 1f to Shade,
                ),
            ),
        )
        ScanFrame(frame, Modifier.fillMaxSize(), accent = Color(0xFFE6F7EA))

        // Top: logo (the whole Mushaf) · settings — circles, no text.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = SidePad, vertical = 12.dp),
        ) {
            Surface(onClick = { vm.openIndex() }, shape = CircleShape, color = Jade, modifier = Modifier.size(60.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.app_logo), stringResource(R.string.nav_index), Modifier.size(46.dp).clip(CircleShape))
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { vm.openSettings() }, modifier = Modifier.size(60.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Jade, contentColor = EmeraldDeep),
            ) { Icon(AppIcons.Settings, stringResource(R.string.cd_settings), Modifier.size(28.dp)) }
        }
        if (update != UpdateUi.Hidden) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 84.dp, start = SidePad, end = SidePad)) { UpdateBanner(update, vm) }
        }

        // The huge button.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(start = SidePad, end = SidePad, bottom = 28.dp),
        ) {
            Text(stringResource(R.string.frame_hint), color = Cream.copy(alpha = 0.85f), fontSize = 17.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { vm.openScanner() },
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = EmeraldDeep),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(104.dp),
            ) {
                Icon(AppIcons.Scan, null, Modifier.size(34.dp))
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.scan_big), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** A decorated surah title band (drawn, not copied from any Mushaf). */
@Composable
private fun TitleBand(title: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFD9E3D6), Color(0xFFF4F7F1), Color(0xFFD9E3D6))))
            .border(2.dp, Color(0xFF9DB09F), RoundedCornerShape(10.dp)),
    ) { Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F4F44)) }
}

/** The ayat as one justified block with a small numbered medallion after each, like a Mushaf line. */
@Composable
private fun PageText(verses: List<Verse>) {
    val text = buildAnnotatedString {
        verses.forEachIndexed { i, v ->
            append(v.verseText); append(" ")
            appendInlineContent("m${v.ayah}", "(${v.ayah})")
            if (i < verses.size - 1) append(" ")
        }
    }
    val inline = verses.associate { v ->
        "m${v.ayah}" to InlineTextContent(Placeholder(26.sp, 26.sp, PlaceholderVerticalAlign.Center)) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Emerald.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
                Text("${v.ayah}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Text(
        text, inlineContent = inline, textAlign = TextAlign.Center,
        style = VerseTextStyle.copy(fontSize = 25.sp, lineHeight = 56.sp, color = Color(0xFF2C3530), textDirection = TextDirection.Rtl),
    )
}

/** Round torch toggle for the camera page (lime when on). */
@Composable
fun TorchButton(on: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, modifier = Modifier.size(60.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = if (on) Amber else Jade, contentColor = EmeraldDeep),
    ) { Icon(if (on) AppIcons.FlashOn else AppIcons.FlashOff, stringResource(R.string.cd_torch), Modifier.size(28.dp)) }
}
