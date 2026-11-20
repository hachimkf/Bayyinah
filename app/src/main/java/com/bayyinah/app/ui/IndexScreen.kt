package com.bayyinah.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayyinah.app.R
import com.bayyinah.app.data.Surah
import com.bayyinah.app.data.Verse
import com.bayyinah.app.ui.theme.Amber
import com.bayyinah.app.ui.theme.Cream
import com.bayyinah.app.ui.theme.Emerald
import com.bayyinah.app.ui.theme.EmeraldDeep
import com.bayyinah.app.ui.theme.Ink
import com.bayyinah.app.ui.theme.Mint
import com.bayyinah.app.ui.theme.MintStrong
import com.bayyinah.app.ui.theme.Sand
import com.bayyinah.app.ui.theme.VerseTextStyle

/** Surah index, ayah list and search — the manual path when scanning isn't convenient. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IndexScreen(vm: MainViewModel, index: IndexState, bottomInset: Boolean) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(index.focusSearch) { if (index.focusSearch) { focus.requestFocus(); keyboard?.show() } }

    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding()) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            FilledTonalIconButton(
                onClick = { keyboard?.hide(); vm.indexBack() },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Mint, contentColor = EmeraldDeep),
            ) { Icon(AppIcons.Back, stringResource(R.string.cd_back), Modifier.size(28.dp)) }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    index.openSurah?.let { "سورة ${it.nameAr}" } ?: stringResource(R.string.index_ar),
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep,
                )
                Text(
                    index.openSurah?.let { "${it.number}. ${it.nameEn}" } ?: stringResource(R.string.index_title_fr),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Search pill
        SearchField(
            value = index.query,
            clearSignal = index.clearSignal,
            onChange = vm::setQuery,
            onSearch = { keyboard?.hide() },
            busy = index.searching,
            modifier = Modifier.padding(horizontal = 16.dp).focusRequester(focus),
        )
        Spacer(Modifier.height(12.dp))

        val mode = when {
            index.query.isNotBlank() -> 2
            index.openSurah != null -> 1
            else -> 0
        }
        val bottomPad = if (bottomInset) 420.dp else 24.dp // keep last items above the player sheet
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (slideInHorizontally { if (targetState > initialState) -it / 4 else it / 4 } + fadeIn()) togetherWith fadeOut()
            },
            label = "indexMode",
            modifier = Modifier.weight(1f).imePadding(),
        ) { m ->
            when (m) {
                2 -> SearchResults(index, bottomPad, onSurah = { keyboard?.hide(); vm.clearQuery(); vm.openSurah(it) }, onVerse = { keyboard?.hide(); vm.selectVerse(it) })
                1 -> SurahDetail(index, bottomPad, onVerse = vm::selectVerse)
                else -> SurahList(index.surahs, bottomPad, onSurah = vm::openSurah)
            }
        }
    }
}

@Composable
private fun SearchField(value: String, clearSignal: Int, onChange: (String) -> Unit, onSearch: () -> Unit, busy: Boolean, modifier: Modifier) {
    // The field owns its text (no two-way sync: that raced with fast typing, e.g. "18:10" → "18:1").
    val field = rememberTextFieldState(value)
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { onChange(it) } }
    LaunchedEffect(clearSignal) { if (clearSignal > 0) field.clearText() }
    Surface(color = Mint, shape = CircleShape, modifier = modifier.fillMaxWidth().height(64.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp)) {
            Icon(AppIcons.Search, null, tint = Emerald, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    state = field,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = TextStyle(fontSize = 20.sp, color = Ink, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(Emerald),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = { onSearch() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { field.clearText() }, modifier = Modifier.size(48.dp)) {
                    Icon(AppIcons.Clear, null, tint = EmeraldDeep)
                }
            }
        }
    }
}

@Composable
private fun SurahList(surahs: List<Surah>, bottomPad: androidx.compose.ui.unit.Dp, onSurah: (Surah) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    ) {
        itemsIndexed(surahs, key = { _, s -> s.number }) { i, s -> SurahCard(s, tint = if (i % 2 == 0) Mint else Sand) { onSurah(s) } }
    }
}

@Composable
private fun SurahCard(s: Surah, tint: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, color = tint, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            AyahBadge(s.number, size = 52)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.nameEn, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                // Separate Texts: mixing Arabic + Latin in one string scrambles the bidi order.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ayat_fr, s.ayahCount), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("  ·  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(if (s.makki) R.string.makki else R.string.madani), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(s.nameAr, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep)
        }
    }
}

@Composable
private fun SurahDetail(index: IndexState, bottomPad: androidx.compose.ui.unit.Dp, onVerse: (Verse) -> Unit) {
    val s = index.openSurah ?: return
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    ) {
        item(key = "hero") {
            // Dark emerald hero card (the reference's dark navy card, in green)
            Surface(color = EmeraldDeep, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) {
                    Text("سورة ${s.nameAr}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Cream)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(stringResource(if (s.makki) R.string.makki else R.string.madani))
                        Pill(stringResource(R.string.ayat_ar, s.ayahCount.toArabicDigits()))
                        Pill(stringResource(R.string.ayat_fr, s.ayahCount))
                    }
                }
            }
        }
        if (index.surahVerses.isEmpty()) item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicatorBox() }
        }
        items(index.surahVerses, key = { it.id }) { v -> VerseCard(v, showSurah = false) { onVerse(v) } }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingIndicatorBox() = LoadingIndicator(color = Emerald, modifier = Modifier.size(56.dp))

@Composable
private fun Pill(text: String) {
    Surface(color = Amber, shape = CircleShape) {
        Text(text, color = EmeraldDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
private fun VerseCard(v: Verse, showSurah: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (v.ayah % 2 == 0) Sand else Mint, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AyahBadge(v.ayah, size = 42, container = MintStrong, content = EmeraldDeep)
                Spacer(Modifier.width(10.dp))
                if (showSurah) Text("سورة ${v.surahName} · ${v.surah}:${v.ayah}", fontWeight = FontWeight.Bold, color = EmeraldDeep, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Surface(color = Emerald, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(AppIcons.Play, null, tint = Amber, modifier = Modifier.size(24.dp)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                v.verseText,
                style = VerseTextStyle.copy(fontSize = 24.sp, lineHeight = 46.sp, textDirection = TextDirection.Rtl),
                textAlign = TextAlign.Start,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SearchResults(index: IndexState, bottomPad: androidx.compose.ui.unit.Dp, onSurah: (Surah) -> Unit, onVerse: (Verse) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    ) {
        if (index.surahHits.isNotEmpty()) {
            item(key = "sh") { SectionTitle(stringResource(R.string.surahs_section)) }
            itemsIndexed(index.surahHits, key = { _, s -> "s${s.number}" }) { i, s -> SurahCard(s, if (i % 2 == 0) Mint else Sand) { onSurah(s) } }
        }
        if (index.verseHits.isNotEmpty()) {
            item(key = "vh") { SectionTitle(stringResource(R.string.verses_section) + " (${index.verseHits.size})") }
            items(index.verseHits, key = { "v${it.id}" }) { v -> VerseCard(v, showSurah = true) { onVerse(v) } }
        }
        if (!index.searching && index.surahHits.isEmpty() && index.verseHits.isEmpty()) {
            item(key = "none") {
                Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Emerald, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp))
}

