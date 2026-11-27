package com.bayyinah.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayyinah.app.R
import com.bayyinah.app.data.Surah
import com.bayyinah.app.data.Verse
import com.bayyinah.app.ui.theme.Amber
import com.bayyinah.app.ui.theme.AmberDeep
import com.bayyinah.app.ui.theme.Cream
import com.bayyinah.app.ui.theme.Emerald
import com.bayyinah.app.ui.theme.EmeraldDeep
import com.bayyinah.app.ui.theme.Ink
import com.bayyinah.app.ui.theme.Mint
import com.bayyinah.app.ui.theme.MintStrong
import com.bayyinah.app.ui.theme.QuranFont
import com.bayyinah.app.ui.theme.Sand
import com.bayyinah.app.ui.theme.VerseTextStyle
import kotlinx.coroutines.launch

/** Even side margin used by every screen. */
val SidePad = 20.dp
/** Height of the floating top bar (below the status bar). */
private val TopBarHeight = 76.dp
/** Soft fade below the bar; content starts under it, so it only shows while scrolling. */
val FadeTail = 44.dp

/** Space kept under every tab's content for the floating navigation bar. */
val NavSpace = 40.dp

/**
 * «الفهرس» tab: quick jump (surah + ayah number), search, and the (collapsible) surah list.
 * The layout is right-to-left (Arabic only); the player sheet floats above it (see [BayyinahApp]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IndexScreen(vm: MainViewModel, index: IndexState, setupError: String?) {
    val keyboard = LocalSoftwareKeyboardController.current
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var surahsOpen by rememberSaveable { mutableStateOf(false) }
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight + FadeTail
    val contentPad = PaddingValues(start = SidePad, end = SidePad, top = topPad, bottom = NavSpace)

    Box(Modifier.fillMaxSize().background(Cream)) {
        val mode = when {
            index.openSurah != null -> 2
            index.query.isNotBlank() -> 1
            else -> 0
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = { (slideInHorizontally { if (targetState == 2) -it / 4 else it / 4 } + fadeIn()) togetherWith fadeOut() },
            label = "homeMode",
            modifier = Modifier.fillMaxSize().imePadding(),
        ) { m ->
            if (m == 2) {
                SurahDetail(vm, index, contentPad)
            } else {
                LazyColumn(
                    contentPadding = contentPad,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (setupError != null) item(key = "error") {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.setup_error) + "\n" + setupError, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
                        }
                    }
                    item(key = "jump") {
                        QuickJumpCard(index, vm, onPickSurah = { keyboard?.hide(); pickerOpen = true }, onGo = { keyboard?.hide(); vm.goToJump() })
                    }
                    item(key = "search") {
                        SearchField(index.query, index.clearSignal, vm::setQuery, onSearch = { keyboard?.hide() }, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (m == 1) searchResults(index, onSurah = { keyboard?.hide(); vm.clearQuery(); vm.openSurah(it) }, onVerse = { keyboard?.hide(); vm.selectVerse(it) })
                    else {
                        item(key = "listTitle") { SurahListHeader(index.surahs.size, surahsOpen) { surahsOpen = !surahsOpen } }
                        if (surahsOpen) itemsIndexed(index.surahs, key = { _, s -> s.number }) { i, s ->
                            SurahCard(s, if (i % 2 == 0) Mint else Sand, Modifier.animateItem()) { vm.openSurah(s) }
                        }
                    }
                }
            }
        }

        TopBar(
            index.openSurah, index.openLesson,
            onBack = { keyboard?.hide(); vm.back() },
            onHome = { keyboard?.hide(); vm.backToHome() },
            onSaved = { keyboard?.hide(); vm.openHistory() },
            onMushaf = { keyboard?.hide(); vm.openMushaf() },
        )
    }

    if (pickerOpen) SurahPickerSheet(index.surahs, index.jumpSurah, onDismiss = { pickerOpen = false }) {
        vm.setJumpSurah(it); pickerOpen = false
    }
}

/** Cream that fades smoothly to transparent: content scrolls softly under the bar (no hard edge). */
val TopFade = Brush.verticalGradient(
    0f to Cream, 0.50f to Cream,
    0.60f to Cream.copy(alpha = 0.93f), 0.70f to Cream.copy(alpha = 0.78f), 0.80f to Cream.copy(alpha = 0.55f),
    0.88f to Cream.copy(alpha = 0.32f), 0.95f to Cream.copy(alpha = 0.12f), 1f to Cream.copy(alpha = 0f),
)

@Composable
private fun TopBar(open: Surah?, lesson: com.bayyinah.app.book.BookLesson?, onBack: () -> Unit, onHome: () -> Unit, onSaved: () -> Unit, onMushaf: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(TopFade).statusBarsPadding().height(TopBarHeight + FadeTail)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = SidePad),
        ) {
            if (open != null) {
                RoundIconButton(AppIcons.Back, stringResource(R.string.cd_back), onBack)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("سورة ${open.nameAr}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep, maxLines = 1)
                    Text(
                        lesson?.let { stringResource(R.string.lesson_subtitle, it.page.toArabicDigits(), it.firstAyah.toArabicDigits(), it.lastAyah.toArabicDigits()) }
                            ?: (stringResource(R.string.ayat, open.ayahCount.toArabicDigits()) + " · " + stringResource(if (open.makki) R.string.makki else R.string.madani)),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(52.dp)) // balances the back button so the title is truly centred
            } else {
                RoundIconButton(AppIcons.Back, stringResource(R.string.cd_back), onHome)
                Text(stringResource(R.string.index_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                RoundIconButton(AppIcons.Book, stringResource(R.string.mushaf_title), onMushaf)
                Spacer(Modifier.width(8.dp))
                RoundIconButton(AppIcons.Heart, stringResource(R.string.cd_history), onSaved)
            }
        }
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = EmeraldDeep) {
    FilledTonalIconButton(
        onClick = onClick, modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Mint, contentColor = tint),
    ) { Icon(icon, description, Modifier.size(26.dp)) }
}

/** Dark emerald hero: choose a surah, type the ayah number, read (or scan). */
@Composable
private fun QuickJumpCard(index: IndexState, vm: MainViewModel, onPickSurah: () -> Unit, onGo: () -> Unit) {
    val s = index.jumpSurah
    Surface(color = EmeraldDeep, contentColor = Cream, shape = RoundedCornerShape(36.dp), modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(18.dp)) {
            Text(stringResource(R.string.jump_title), fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(stringResource(R.string.jump_subtitle), fontSize = 14.sp, color = Cream.copy(alpha = 0.75f))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1.7f), horizontalAlignment = Alignment.CenterHorizontally) {
                    FieldLabel(stringResource(R.string.surah_label))
                    Surface(onClick = onPickSurah, color = Cream, contentColor = Ink, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(76.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                            if (s != null) AyahBadge(s.number, size = 44)
                            Text(s?.nameAr ?: "—", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = EmeraldDeep, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Icon(AppIcons.ChevronDown, null, tint = Emerald, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    FieldLabel(stringResource(R.string.ayah_label))
                    AyahNumberField(surahKey = s?.number ?: 0, error = index.jumpError, onChange = vm::setJumpAyah, onGo = onGo)
                }
            }
            if (s != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (index.jumpError) R.string.jump_range_error else R.string.jump_range, s.ayahCount.toArabicDigits()),
                    color = if (index.jumpError) Amber else Cream.copy(alpha = 0.7f), fontSize = 14.sp,
                    fontWeight = if (index.jumpError) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onGo,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = EmeraldDeep),
                shape = CircleShape, modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { ButtonLabel(AppIcons.Play, stringResource(R.string.read)) }
        }
    }
}

/** Icon + label, centred together. */
@Composable
private fun ButtonLabel(icon: ImageVector, text: String) {
    Icon(icon, null, Modifier.size(22.dp))
    Spacer(Modifier.width(6.dp))
    Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
}

@Composable
private fun FieldLabel(text: String) =
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Cream.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 4.dp))

/** Big number-pad field; cleared whenever the surah changes. */
@Composable
private fun AyahNumberField(surahKey: Int, error: Boolean, onChange: (String) -> Unit, onGo: () -> Unit) {
    val field = rememberTextFieldState()
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { onChange(it) } }
    LaunchedEffect(surahKey) { field.clearText() }
    Surface(
        color = Cream, shape = RoundedCornerShape(24.dp),
        border = if (error) androidx.compose.foundation.BorderStroke(3.dp, Amber) else null,
        modifier = Modifier.fillMaxWidth().height(76.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            if (field.text.isEmpty()) Text("1", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink.copy(alpha = 0.25f))
            BasicTextField(
                state = field,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Emerald),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                onKeyboardAction = { onGo() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Searchable surah list in a bottom sheet (type a number or a name). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurahPickerSheet(surahs: List<Surah>, selected: Surah?, onDismiss: () -> Unit, onPick: (Surah) -> Unit) {
    // Hidden ⇄ fully expanded only (no half-open state), so the whole list is reachable.
    val sheet = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val filterField = rememberTextFieldState()
    val filter = filterField.text.toString()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ((selected?.number ?: 1) - 2).coerceAtLeast(0))
    val shown = remember(filter, surahs) {
        val f = filter.trim().map { c -> if (c in '٠'..'٩') '0' + (c - '٠') else c }.joinToString("")
        when {
            f.isEmpty() -> surahs
            f.all { it.isDigit() } -> surahs.filter { it.number.toString().startsWith(f) }
            else -> {
                val ar = com.bayyinah.app.match.ArabicNormalizer.normalize(f)
                val la = java.text.Normalizer.normalize(f.lowercase(), java.text.Normalizer.Form.NFD).filter { it in 'a'..'z' }
                surahs.filter {
                    (ar.isNotEmpty() && com.bayyinah.app.match.ArabicNormalizer.normalize(it.nameAr).contains(ar)) ||
                        (la.isNotEmpty() && java.text.Normalizer.normalize(it.nameEn.lowercase(), java.text.Normalizer.Form.NFD).filter { c -> c in 'a'..'z' }.contains(la))
                }
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Cream) {
        Column(Modifier.padding(horizontal = SidePad), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.pick_surah), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = EmeraldDeep)
            Spacer(Modifier.height(10.dp))
            Surface(color = Mint, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp)) {
                    Icon(AppIcons.Search, null, tint = Emerald)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (filter.isEmpty()) Text(stringResource(R.string.pick_surah_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(
                            state = filterField, lineLimits = TextFieldLineLimits.SingleLine,
                            textStyle = TextStyle(fontSize = 18.sp, color = Ink), cursorBrush = SolidColor(Emerald),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(state = listState, modifier = Modifier.height(480.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown, key = { it.number }) { s ->
                    val isSel = s.number == selected?.number
                    Surface(onClick = { onPick(s) }, color = if (isSel) MintStrong else Mint, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            AyahBadge(s.number, size = 40)
                            Spacer(Modifier.width(12.dp))
                            Text(s.nameAr, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.ayat, s.ayahCount.toArabicDigits()), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SearchField(value: String, clearSignal: Int, onChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier) {
    // The field owns its text (no two-way sync: that raced with fast typing, e.g. "18:10" → "18:1").
    val field = rememberTextFieldState(value)
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { onChange(it) } }
    LaunchedEffect(clearSignal) { if (clearSignal > 0) field.clearText() }
    Surface(color = Mint, shape = CircleShape, modifier = modifier.fillMaxWidth().height(64.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp)) {
            Icon(AppIcons.Search, null, tint = Emerald, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                IconButton(onClick = { field.clearText() }, modifier = Modifier.size(48.dp)) { Icon(AppIcons.Clear, null, tint = EmeraldDeep) }
            }
        }
    }
}

/** «السور ١١٤ ▾»: tap to show or hide the 114 surahs (hidden by default). */
@Composable
private fun SurahListHeader(count: Int, open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "chev")
    Surface(onClick = onToggle, color = Sand, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 20.dp),
        ) {
            Icon(AppIcons.Book, null, tint = Emerald, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.surahs_section) + " · " + count.toArabicDigits(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = EmeraldDeep)
            Spacer(Modifier.width(10.dp))
            Icon(AppIcons.ChevronDown, null, tint = Emerald, modifier = Modifier.size(26.dp).rotate(turn))
        }
    }
}

@Composable
private fun SurahCard(s: Surah, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = tint, shape = RoundedCornerShape(28.dp), modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            AyahBadge(s.number, size = 50)
            Spacer(Modifier.width(14.dp))
            Text(s.nameAr, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.ayat, s.ayahCount.toArabicDigits()), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(if (s.makki) R.string.makki else R.string.madani), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One surah: hero, a "go to ayah" number field that scrolls + plays, then every ayah. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SurahDetail(vm: MainViewModel, index: IndexState, contentPad: PaddingValues) {
    val s = index.openSurah ?: return
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val goTo: () -> Unit = {
        val n = index.jumpAyah.toIntOrNull()
        val pos = index.surahVerses.indexOfFirst { it.ayah == n }
        if (n != null && pos >= 0) {
            keyboard?.hide()
            scope.launch { listState.animateScrollToItem(pos + HEADER_ITEMS) }
            vm.selectVerse(index.surahVerses[pos])
        } else if (index.openLesson == null) vm.goToJump() // shows the range error
    }
    LazyColumn(
        state = listState,
        contentPadding = contentPad,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero") {
            Surface(color = EmeraldDeep, contentColor = Cream, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                    Text("سورة ${s.nameAr}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    index.openLesson?.let { l ->
                        Pill(stringResource(R.string.lesson_hero, l.page.toArabicDigits()))
                    } ?: Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(stringResource(if (s.makki) R.string.makki else R.string.madani))
                        Pill(stringResource(R.string.ayat, s.ayahCount.toArabicDigits()))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(120.dp)) { AyahNumberField(s.number, index.jumpError, vm::setJumpAyah, goTo) }
                        Button(
                            onClick = goTo,
                            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = EmeraldDeep),
                            shape = CircleShape, modifier = Modifier.height(64.dp),
                        ) { Text(stringResource(R.string.go_to_ayah), fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                    }
                    if (index.jumpError) Text(stringResource(R.string.jump_range_error, s.ayahCount.toArabicDigits()), color = Amber,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        if (index.surahVerses.isEmpty()) item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator(color = Emerald, modifier = Modifier.size(56.dp)) }
        }
        items(index.surahVerses, key = { it.id }) { v -> VerseCard(v, showSurah = false) { vm.selectVerse(v) } }
    }
}

private const val HEADER_ITEMS = 1 // the hero card before the first ayah

@Composable
private fun Pill(text: String) {
    Surface(color = Amber, shape = CircleShape) {
        Text(text, color = EmeraldDeep, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
fun VerseCard(v: Verse, showSurah: Boolean, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (v.ayah % 2 == 0) Sand else Mint, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AyahBadge(v.ayah, size = 42, container = MintStrong, content = EmeraldDeep)
                Spacer(Modifier.width(10.dp))
                if (showSurah) Text("سورة ${v.surahName}", fontWeight = FontWeight.Bold, color = EmeraldDeep, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
                Surface(color = Emerald, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(AppIcons.Play, null, tint = Amber, modifier = Modifier.size(24.dp)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                v.verseText,
                style = VerseTextStyle.copy(fontSize = 24.sp, lineHeight = 46.sp, textDirection = TextDirection.Rtl),
                textAlign = TextAlign.Center,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun LazyListScope.searchResults(index: IndexState, onSurah: (Surah) -> Unit, onVerse: (Verse) -> Unit) {
    if (index.surahHits.isNotEmpty()) {
        item(key = "sh") { SectionTitle(stringResource(R.string.surahs_section)) }
        itemsIndexed(index.surahHits, key = { _, s -> "s${s.number}" }) { i, s -> SurahCard(s, if (i % 2 == 0) Mint else Sand) { onSurah(s) } }
    }
    if (index.verseHits.isNotEmpty()) {
        item(key = "vh") { SectionTitle(stringResource(R.string.verses_section) + " · " + index.verseHits.size.toArabicDigits()) }
        items(index.verseHits, key = { "v${it.id}" }) { v -> VerseCard(v, showSurah = true) { onVerse(v) } }
    }
    if (!index.searching && index.surahHits.isEmpty() && index.verseHits.isEmpty()) {
        item(key = "none") {
            Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Emerald, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp))
}
