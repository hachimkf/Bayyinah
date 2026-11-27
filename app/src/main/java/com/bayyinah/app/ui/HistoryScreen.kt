package com.bayyinah.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayyinah.app.R
import com.bayyinah.app.ui.theme.Amber
import com.bayyinah.app.ui.theme.Cream
import com.bayyinah.app.ui.theme.Emerald
import com.bayyinah.app.ui.theme.EmeraldDeep

/** «المحفوظات»: heart-marked ayat, then the ones she listened to recently. */
@Composable
fun HistoryScreen(vm: MainViewModel, index: IndexState) {
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 76.dp + FadeTail
    Box(Modifier.fillMaxSize().background(Cream)) {
        LazyColumn(
            contentPadding = PaddingValues(start = SidePad, end = SidePad, top = topPad, bottom = NavSpace),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (index.favorites.isEmpty() && index.recent.isEmpty()) item(key = "empty") {
                Text(stringResource(R.string.history_empty), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
            }
            if (index.favorites.isNotEmpty()) {
                item(key = "favTitle") { SectionTitle(stringResource(R.string.favorites_section)) }
                items(index.favorites, key = { "f${it.id}" }) { v ->
                    VerseCard(v, showSurah = true, trailing = {
                        IconButton(onClick = { vm.toggleFavorite(v) }) {
                            Icon(AppIcons.Heart, stringResource(R.string.cd_unfavorite), tint = Amber, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                    }) { vm.selectVerse(v) }
                }
            }
            if (index.recent.isNotEmpty()) {
                item(key = "recentTitle") { SectionTitle(stringResource(R.string.recent_section)) }
                items(index.recent, key = { "r${it.id}" }) { v -> VerseCard(v, showSurah = true) { vm.selectVerse(v) } }
                item(key = "clear") {
                    TextButton(onClick = vm::clearRecent, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.clear_recent), color = Emerald, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
        // Same soft fade as the home bar.
        Box(Modifier.fillMaxWidth().background(TopFade).statusBarsPadding().height(76.dp + FadeTail)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(76.dp).padding(horizontal = SidePad)) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(AppIcons.Heart, null, tint = Amber, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.history_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                }
            }
        }
    }
}
