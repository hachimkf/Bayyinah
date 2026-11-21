package com.bayyinah.app.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

/**
 * The whole app: quick jump (surah + ayah number), search, surah list and surah detail.
 * The verse player sheet floats above it (see [BayyinahApp]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(vm: MainViewModel, index: IndexState, update: UpdateUi, sheetOpen: Boolean, setupError: String?) {
    val keyboard = LocalSoftwareKeyboardController.current
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val bottomPad = if (sheetOpen) 440.dp else 32.dp // keep the last items above the player sheet

    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding()) {
        TopBar(index.openSurah, index.openLesson, onBack = { keyboard?.hide(); vm.back() }, onLogo = { vm.checkForUpdates(force = true) }, onSettings = { keyboard?.hide(); vm.openSettings() })

        val mode = when {
            index.openSurah != null -> 2
            index.query.isNotBlank() -> 1
            else -> 0
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = { (slideInHorizontally { if (targetState == 2) it / 4 else -it / 4 } + fadeIn()) togetherWith fadeOut() },
            label = "homeMode",
            modifier = Modifier.weight(1f).imePadding(),
        ) { m ->
            if (m == 2) {
                SurahDetail(vm, index, bottomPad)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPad),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "update") { UpdateBanner(update, vm) }
                    if (setupError != null) item(key = "error") {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("تعذّر فتح المصحف · Impossible d'ouvrir le texte du Coran. Redémarrez l'application.\n$setupError",
                                color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
                        }
                    }
                    item(key = "jump") {
                        QuickJumpCard(index, vm, onPickSurah = { keyboard?.hide(); pickerOpen = true }, onGo = { keyboard?.hide(); vm.goToJump() }, onScan = { keyboard?.hide(); vm.openScanner() })
                    }
                    index.lastVerse?.let { last ->
                        item(key = "continue") { ContinueChip(last) { vm.selectVerse(last) } }
                    }
                    if (index.lessons.isNotEmpty()) item(key = "book") { MyBookCard(index, vm, onDone = { keyboard?.hide() }) }
                    item(key = "search") {
                        SearchField(index.query, index.clearSignal, vm::setQuery, onSearch = { keyboard?.hide() }, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (m == 1) searchResults(index, onSurah = { keyboard?.hide(); vm.clearQuery(); vm.openSurah(it) }, onVerse = { keyboard?.hide(); vm.selectVerse(it) })
                    else {
                        item(key = "listTitle") { SectionTitle(stringResource(R.string.surahs_section)) }
                        itemsIndexed(index.surahs, key = { _, s -> s.number }) { i, s -> SurahCard(s, if (i % 2 == 0) Mint else Sand) { vm.openSurah(s) } }
                    }
                }
            }
        }
    }

    if (pickerOpen) SurahPickerSheet(index.surahs, index.jumpSurah, onDismiss = { pickerOpen = false }) {
        vm.setJumpSurah(it); pickerOpen = false
    }
}

@Composable
private fun TopBar(open: Surah?, lesson: com.bayyinah.app.book.BookLesson?, onBack: () -> Unit, onLogo: () -> Unit, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (open != null) {
            FilledTonalIconButton(
                onClick = onBack, modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Mint, contentColor = EmeraldDeep),
            ) { Icon(AppIcons.Back, stringResource(R.string.cd_back), Modifier.size(28.dp)) }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("سورة ${open.nameAr}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                Text(lesson?.let { stringResource(R.string.lesson_subtitle, it.page, it.firstAyah, it.lastAyah) } ?: "${open.number}. ${open.nameEn}",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Tapping the logo checks GitHub for a new version.
            Surface(onClick = onLogo, color = Color.Transparent, shape = CircleShape) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)) {
                    LogoMark()
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("بيّنة", fontFamily = QuranFont, fontSize = 30.sp, color = EmeraldDeep)
                        Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(
                onClick = onSettings, modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Mint, contentColor = EmeraldDeep),
            ) { Icon(AppIcons.Settings, stringResource(R.string.cd_settings), Modifier.size(26.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogoMark() {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(R.drawable.app_logo), contentDescription = null,
        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
    )
}

/** Dark emerald hero: choose a surah, type the ayah number, read. */
@Composable
private fun QuickJumpCard(index: IndexState, vm: MainViewModel, onPickSurah: () -> Unit, onGo: () -> Unit, onScan: () -> Unit) {
    val s = index.jumpSurah
    Surface(color = EmeraldDeep, contentColor = Cream, shape = RoundedCornerShape(36.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.jump_title_ar), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(stringResource(R.string.jump_title_fr), fontSize = 13.sp, color = Cream.copy(alpha = 0.75f))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                // Surah selector
                Column(Modifier.weight(1.7f)) {
                    FieldLabel(stringResource(R.string.surah_label))
                    Surface(onClick = onPickSurah, color = Cream, contentColor = Ink, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(76.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                            if (s != null) AyahBadge(s.number, size = 44)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(s?.nameAr ?: "—", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = EmeraldDeep, maxLines = 1)
                                if (s != null) Text(s.nameEn, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(AppIcons.ChevronDown, null, tint = Emerald, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                // Ayah number
                Column(Modifier.weight(1f)) {
                    FieldLabel(stringResource(R.string.ayah_label))
                    AyahNumberField(
                        surahKey = s?.number ?: 0,
                        error = index.jumpError,
                        onChange = vm::setJumpAyah,
                        onGo = onGo,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (s != null) {
                // Two Texts: one bilingual string would be scrambled by bidi ("7 من ١ إلى").
                val c = if (index.jumpError) Amber else Cream.copy(alpha = 0.7f)
                val w = if (index.jumpError) FontWeight.Bold else FontWeight.Normal
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(stringResource(if (index.jumpError) R.string.jump_range_error_fr else R.string.jump_range_fr, s.ayahCount), color = c, fontSize = 13.sp, fontWeight = w)
                    Text("  ·  ", color = c, fontSize = 13.sp)
                    Text(stringResource(if (index.jumpError) R.string.jump_range_error_ar else R.string.jump_range_ar, s.ayahCount.toArabicDigits()), color = c, fontSize = 13.sp, fontWeight = w)
                }
            }
            Spacer(Modifier.height(12.dp))
            // Two ways in: read the chosen ayah, or scan it from the book.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onGo,
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = EmeraldDeep),
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.weight(1f).height(72.dp),
                ) { ButtonLabel(AppIcons.Play, stringResource(R.string.read_ar), stringResource(R.string.read_fr)) }
                Button(
                    onClick = onScan,
                    colors = ButtonDefaults.buttonColors(containerColor = Cream, contentColor = EmeraldDeep),
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.weight(1f).height(72.dp),
                ) { ButtonLabel(AppIcons.Scan, stringResource(R.string.scan_btn_ar), stringResource(R.string.scan_btn_fr)) }
            }
        }
    }
}

/** Icon + Arabic (bold) over French: fits half-width buttons without truncation. */
@Composable
private fun ButtonLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, ar: String, fr: String) {
    Icon(icon, null, Modifier.size(26.dp))
    Spacer(Modifier.width(10.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ar, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
        Text(fr, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun FieldLabel(text: String) =
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream.copy(alpha = 0.8f), modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))

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

@Composable
private fun ContinueChip(last: Verse, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Sand, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Surface(color = Emerald, shape = CircleShape, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(AppIcons.Play, null, tint = Amber, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.continue_fr, last.surah, last.ayah), fontWeight = FontWeight.Bold, color = AmberDeep, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Text("${stringResource(R.string.continue_ar)} · ${last.surahName}", fontWeight = FontWeight.Bold, color = EmeraldDeep, fontSize = 16.sp)
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
        val f = filter.trim()
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
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.pick_surah), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = EmeraldDeep, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
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
                            Column(Modifier.weight(1f)) {
                                Text(s.nameEn, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                                Text(stringResource(R.string.ayat_fr, s.ayahCount), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(s.nameAr, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep)
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
                IconButton(onClick = { field.clearText() }, modifier = Modifier.size(48.dp)) { Icon(AppIcons.Clear, null, tint = EmeraldDeep) }
            }
        }
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

/** One surah: hero, a "go to ayah" number field that scrolls + plays, then every ayah. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SurahDetail(vm: MainViewModel, index: IndexState, bottomPad: Dp) {
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero") {
            Surface(color = EmeraldDeep, contentColor = Cream, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                    Text("سورة ${s.nameAr}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    index.openLesson?.let { l ->
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.lesson_hero, l.page), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Amber)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (index.openLesson == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(stringResource(if (s.makki) R.string.makki else R.string.madani))
                        Pill(stringResource(R.string.ayat_ar, s.ayahCount.toArabicDigits()))
                        Pill(stringResource(R.string.ayat_fr, s.ayahCount))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(120.dp)) { AyahNumberField(s.number, index.jumpError, vm::setJumpAyah, goTo) }
                        Button(
                            onClick = goTo,
                            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = EmeraldDeep),
                            shape = CircleShape, modifier = Modifier.height(64.dp),
                        ) { Text(stringResource(R.string.go_to_ayah), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    }
                    if (index.jumpError) Text(stringResource(R.string.jump_range_error_fr, s.ayahCount), color = Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
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

/** «كتابي»: her book — type a page number, or tap one of its 24 Quran lessons. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MyBookCard(index: IndexState, vm: MainViewModel, onDone: () -> Unit) {
    val surahs = index.surahs.associateBy { it.number }
    val page = rememberTextFieldState()
    LaunchedEffect(page) { snapshotFlow { page.text.toString() }.collect { vm.clearPageError() } }
    Surface(color = Sand, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                Box(Modifier.size(44.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(Emerald), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Book, null, tint = Amber, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.book_title_ar), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = EmeraldDeep)
                    Text(stringResource(R.string.book_title_fr), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            // Page number → lesson
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                Surface(color = Cream, shape = RoundedCornerShape(22.dp),
                    border = if (index.pageError) androidx.compose.foundation.BorderStroke(3.dp, Amber) else null,
                    modifier = Modifier.width(120.dp).height(64.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                        if (page.text.isEmpty()) Text(stringResource(R.string.book_page_hint), fontSize = 15.sp, color = Ink.copy(alpha = 0.35f))
                        BasicTextField(
                            state = page, lineLimits = TextFieldLineLimits.SingleLine,
                            textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep, textAlign = TextAlign.Center),
                            cursorBrush = SolidColor(Emerald),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                            onKeyboardAction = { onDone(); vm.goToBookPage(page.text.toString()) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Button(
                    onClick = { onDone(); vm.goToBookPage(page.text.toString()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Cream),
                    shape = CircleShape, modifier = Modifier.weight(1f).height(64.dp),
                ) { Text(stringResource(R.string.book_open_page), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1) }
            }
            if (index.pageError) Text(stringResource(R.string.book_page_error), color = AmberDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            Spacer(Modifier.height(12.dp))
            // The 24 lessons, in book order
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(index.lessons, key = { it.page }) { l ->
                    Surface(onClick = { onDone(); vm.openLesson(l) }, color = Cream, shape = RoundedCornerShape(22.dp), modifier = Modifier.width(118.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
                            Text(stringResource(R.string.book_page_n, l.page), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Emerald)
                            Text(surahs[l.surah]?.nameAr ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EmeraldDeep, maxLines = 1)
                            Text(if (l.firstAyah == l.lastAyah) "${l.firstAyah}" else "${l.firstAyah}–${l.lastAyah}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

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

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Emerald, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp))
}

