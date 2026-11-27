package com.bayyinah.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayyinah.app.R
import com.bayyinah.app.book.MushafText
import com.bayyinah.app.ui.theme.Amber
import com.bayyinah.app.ui.theme.Cream
import com.bayyinah.app.ui.theme.EmeraldDeep
import com.bayyinah.app.ui.theme.Ink
import com.bayyinah.app.ui.theme.InkMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * «المصحف»: the Mushaf Mohammadi page by page, in its own Moroccan font (from the private text pack).
 * Swipe between the 638 pages; tap a word to play its ayah (the player opens above); the ayah being
 * played is softly highlighted. Without the pack, a short note says where to import it.
 */
@Composable
fun MushafReaderScreen(vm: MainViewModel, state: UiState) {
    val text by vm.mushafText.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = SidePad, vertical = 10.dp)) {
            RoundIconButton(AppIcons.Back, stringResource(R.string.cd_back), { vm.openIndex() })
            Text(stringResource(R.string.mushaf_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(52.dp))
        }
        val t = text
        if (t == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.mushaf_needs_pack), fontSize = 18.sp, color = InkMuted, textAlign = TextAlign.Center, lineHeight = 28.sp)
            }
            return@Column
        }
        val count = MushafText.LAST_PAGE - MushafText.FIRST_PAGE + 1
        val pager = rememberPagerState(initialPage = (state.mushafPage - MushafText.FIRST_PAGE).coerceIn(0, count - 1)) { count }
        LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { vm.setMushafPage(it + MushafText.FIRST_PAGE) } }
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { i ->
            MushafPage(t, i + MushafText.FIRST_PAGE, playing = state.verse?.id) { s, a -> vm.playVerse(s, a) }
        }
    }
}

@Composable
private fun MushafPage(text: MushafText, page: Int, playing: Int?, onAyah: (Int, Int) -> Unit) {
    val lines by produceState<List<List<MushafText.Item>>?>(null, page) { value = withContext(Dispatchers.IO) { text.page(page) } }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        val ls = lines ?: return@BoxWithConstraints
        val density = LocalDensity.current
        val avail = with(density) { maxWidth.toPx() }
        // One font size for the whole page: the longest line (at 24 sp, with a space between words)
        // must fit the width.
        val size = remember(ls, avail) {
            val base = TextStyle(fontFamily = text.font, fontSize = 24.sp, textDirection = TextDirection.Rtl)
            val longest = ls.filter { l -> l.none { it.kind == 't' } }.maxOfOrNull { l ->
                measurer.measure(l.joinToString(" ") { display(it) }, base).size.width.toFloat()
            } ?: avail
            (24f * avail / longest * 0.97f).coerceIn(14f, 34f)
        }
        val style = TextStyle(fontFamily = text.font, fontSize = size.sp, lineHeight = (size * 2.05f).sp, color = Ink, textDirection = TextDirection.Rtl)
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
        ) {
            ls.forEach { line ->
                if (line.any { it.kind == 't' }) {
                    TitleBand(line.first { it.kind == 't' }.text, style)
                } else {
                    val full = measurer.measure(line.joinToString(" ") { display(it) }, style).size.width > avail * 0.62f
                    Row(
                        horizontalArrangement = if (full) Arrangement.SpaceBetween else Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        line.forEach { item ->
                            val hl = playing != null && item.kind != 'o' && item.surah * 1000 + item.ayah == playing
                            Text(
                                display(item), style = if (item.kind == 'm') style.copy(color = EmeraldDeep) else style,
                                softWrap = false, maxLines = 1,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (hl) Amber.copy(alpha = 0.22f) else Color.Transparent)
                                    .clickable(enabled = item.kind != 'o') { onAyah(item.surah, item.ayah) },
                            )
                        }
                    }
                }
            }
            Text("$page", fontSize = 13.sp, color = InkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Shown as stored: Android's shaping already orders multi-digit markers "(12)" correctly
 *  (the web reader's digit flip is not needed here). */
private fun display(item: MushafText.Item): String = item.text

@Composable
private fun TitleBand(title: String, style: TextStyle) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(Amber.copy(alpha = 0.14f)).border(1.5.dp, Amber.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(vertical = 2.dp),
    ) { Text(title.trim('[', ']', ' '), style = style.copy(color = EmeraldDeep), textAlign = TextAlign.Center) }
}
