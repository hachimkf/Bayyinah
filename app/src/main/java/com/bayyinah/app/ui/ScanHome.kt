package com.bayyinah.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.bayyinah.app.ui.theme.Emerald
import com.bayyinah.app.ui.theme.VerseTextStyle

/**
 * Home, exactly as in the design (app ui home and scan pages.svg): a Mushaf page under a white
 * fade that leaves the frame clear, dark green brackets and scan line, two mint circles at the top
 * (app logo → the whole Mushaf, gear → settings) and a big mint pill that opens the camera.
 */
@Composable
fun ScanHomeScreen(vm: MainViewModel, state: UiState, update: UpdateUi, sample: List<Verse>) {
    BoxWithConstraints(Modifier.fillMaxSize().background(com.bayyinah.app.ui.theme.Cream)) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val frame = remember(w, h) { scanFrame(w, h) }
        val frameTop = with(density) { frame.top.toDp() }
        val frameH = with(density) { frame.height().toDp() }
        val hDp = with(density) { h.toDp() }; val wDp = with(density) { w.toDp() }

        // Her own home image (a Mushaf page with its soft fade), exactly as given, filling the screen.
        Image(
            painterResource(R.drawable.home_page), null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        ScanFrame(frame, Modifier.fillMaxSize(), accent = ScanLine, line = ScanLine, beamTop = ScanLine.copy(alpha = 0.30f))

        // Top: two mint circles — the app logo (the whole Mushaf) and settings.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = wDp * 0.074f, vertical = 12.dp),
        ) {
            Surface(onClick = { vm.openIndex() }, shape = CircleShape, color = ScanMint, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.app_logo), stringResource(R.string.nav_index), Modifier.size(40.dp).clip(CircleShape))
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(onClick = { vm.openSettings() }, shape = CircleShape, color = ScanMint, contentColor = com.bayyinah.app.ui.theme.EmeraldDeep, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(AppIcons.Settings, stringResource(R.string.cd_settings), Modifier.size(26.dp)) }
            }
        }
        if (update != UpdateUi.Hidden) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 80.dp, start = SidePad, end = SidePad)) { UpdateBanner(update, vm) }
        }

        // The big mint pill (design: 82 % wide, 12 % of the height, near the bottom).
        Button(
            onClick = { vm.openScanner() },
            colors = ButtonDefaults.buttonColors(containerColor = com.bayyinah.app.ui.theme.EmeraldDeep, contentColor = com.bayyinah.app.ui.theme.Cream),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = hDp * 0.0457f)
                .width(wDp * 0.823f).height(hDp * 0.119f),
        ) {
            Icon(AppIcons.Scan, null, Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.scan_big), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** linear-gradient in the design: y from 0.86 % to 97.2 % of the screen, white, clear at its middle. */
private val HomeFade: Brush = run {
    val y0 = 0.0086f; val span = 0.9631f
    val stops = listOf(
        .08f to 1f, .13f to .98f, .18f to .93f, .23f to .84f, .29f to .72f, .34f to .56f, .40f to .36f, .46f to .14f,
        .49f to 0f, .52f to .18f, .55f to .37f, .59f to .54f, .63f to .68f, .67f to .80f, .71f to .89f, .76f to .95f,
        .81f to .99f, .88f to 1f,
    )
    val pts = listOf(0f to com.bayyinah.app.ui.theme.Cream) +
        stops.map { (o, a) -> (y0 + o * span) to com.bayyinah.app.ui.theme.Cream.copy(alpha = a) } + listOf(1f to com.bayyinah.app.ui.theme.Cream)
    Brush.verticalGradient(*pts.toTypedArray())
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

/** The ayat as one block with a small numbered medallion after each, like a Mushaf line. */
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

/** Mint torch circle with a white icon (camera page). */
@Composable
fun TorchButton(on: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = if (on) com.bayyinah.app.ui.theme.Amber else ScanMint,
        contentColor = com.bayyinah.app.ui.theme.EmeraldDeep, modifier = Modifier.size(52.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (on) AppIcons.FlashOn else AppIcons.FlashOff, stringResource(R.string.cd_torch), Modifier.size(26.dp))
        }
    }
}
