package com.bayyinah.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import com.bayyinah.app.R
import com.bayyinah.app.data.Verse
import com.bayyinah.app.ui.theme.Amber
import com.bayyinah.app.ui.theme.AmberDeep
import com.bayyinah.app.ui.theme.AmberSoft
import com.bayyinah.app.ui.theme.Cream
import com.bayyinah.app.ui.theme.Emerald
import com.bayyinah.app.ui.theme.EmeraldDeep
import com.bayyinah.app.ui.theme.Mint
import com.bayyinah.app.ui.theme.Parchment
import com.bayyinah.app.ui.theme.VerseTextStyle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VerseSheet(vm: MainViewModel, state: UiState, verse: Verse) {
    val haptics = LocalHapticFeedback.current
    Surface(
        color = Cream,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
        ) {
            Box(Modifier.width(44.dp).height(5.dp).background(Parchment, CircleShape))
            Spacer(Modifier.height(10.dp))

            // Header: close · surah title · cookie-shaped ayah badge
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                FilledTonalIconButton(
                    onClick = { vm.dismissVerse() },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Parchment, contentColor = EmeraldDeep),
                ) { Icon(AppIcons.Close, stringResource(R.string.cd_close), modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                    Text("سورة ${verse.surahName}", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep)
                    Text(
                        "${verse.surah}:${verse.ayah}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                AyahBadge(verse.ayah, size = 60)
            }
            Spacer(Modifier.height(8.dp))
            ReciterChip(state, onPick = vm::selectReciter)
            Spacer(Modifier.height(10.dp))

            // The verse on a soft mint card, large KFGQPC Warsh script.
            val fontSize = when {
                verse.verseText.length < 50 -> 34.sp
                verse.verseText.length < 110 -> 29.sp
                else -> 25.sp
            }
            Surface(color = Mint, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .heightIn(max = 230.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        verse.verseText,
                        style = VerseTextStyle.copy(fontSize = fontSize, lineHeight = fontSize * 1.9f, textDirection = TextDirection.Rtl),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            when {
                !state.audioAvailable -> Notice(stringResource(R.string.audio_missing_ar), stringResource(R.string.audio_missing_fr))
                state.audioError -> Notice(stringResource(R.string.audio_offline_ar), stringResource(R.string.audio_offline_fr), action = stringResource(R.string.retry), onAction = vm::retryAudio)
                else -> LinearWavyProgressIndicator(
                    progress = { state.progress },
                    color = Emerald,
                    trackColor = Parchment,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(14.dp),
                )
            }
            Spacer(Modifier.height(14.dp))

            // Big controls: previous · play/pause · repeat · next
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StepButton(
                    AppIcons.ChevronLeft, state.hasPrevious,
                    stringResource(R.string.prev_ar), stringResource(R.string.prev_fr), stringResource(R.string.cd_prev),
                ) { haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); vm.previous() }

                PlayButton(state.isPlaying, state.buffering, state.audioAvailable) {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); vm.togglePlay()
                }

                RepeatButton(state.repeat) {
                    haptics.performHapticFeedback(if (state.repeat) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    vm.toggleRepeat()
                }

                StepButton(
                    AppIcons.ChevronRight, state.hasNext,
                    stringResource(R.string.next_ar), stringResource(R.string.next_fr), stringResource(R.string.cd_next),
                ) { haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); vm.next() }
            }
            Spacer(Modifier.height(14.dp))

            SpeedSelector(state.slow) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                vm.setSlow(it)
            }
        }
    }
}

/** Amber notice with an optional action (e.g. retry). */
@Composable
private fun Notice(ar: String, fr: String, action: String? = null, onAction: () -> Unit = {}) {
    Surface(color = AmberSoft, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ar, fontWeight = FontWeight.Bold, color = AmberDeep)
                Text(fr, fontSize = 13.sp, color = AmberDeep)
            }
            if (action != null) {
                Spacer(Modifier.width(10.dp))
                androidx.compose.material3.Button(
                    onClick = onAction,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Cream),
                    shape = CircleShape, modifier = Modifier.height(48.dp),
                ) { Text(action, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** "القارئ · Omar Al-Qazabri ▾" — opens the reciter list. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReciterChip(state: UiState, onPick: (com.bayyinah.app.data.Reciter) -> Unit) {
    val r = state.reciter ?: return
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Surface(onClick = { open = true }, color = Mint, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Icon(AppIcons.Listen, null, tint = Emerald, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            // Stacked names: long names (e.g. Mahmoud Khalil Al-Husary) never collide.
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(r.nameAr, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EmeraldDeep, maxLines = 1)
                Text(r.nameEn, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
            Icon(AppIcons.ChevronDown, null, tint = Emerald, modifier = Modifier.size(22.dp))
        }
    }
    if (open) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { open = false }, containerColor = Cream) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pick_reciter), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = EmeraldDeep,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                Text(stringResource(R.string.reciter_source), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                state.reciters.forEach { rc ->
                    val sel = rc.id == r.id
                    Surface(onClick = { onPick(rc); open = false }, color = if (sel) EmeraldDeep else Mint, contentColor = if (sel) Cream else EmeraldDeep,
                        shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(68.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp)) {
                            Text(rc.nameEn, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            Text(rc.nameAr, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Ayah number on an expressive "cookie" shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AyahBadge(ayah: Int, size: Int, container: Color = Emerald, content: Color = Cream) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(container),
    ) {
        Text(ayah.toArabicDigits(), color = content, fontWeight = FontWeight.Bold, fontSize = (size * 0.32f).sp)
    }
}

/** Spring "squish" on press, driven by the expressive motion scheme. */
@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.86f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")
    return s
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayButton(playing: Boolean, buffering: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(if (playing) 1f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "morph")
    // Slow spin of the cookie while the recitation plays.
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "spinDeg",
    )
    val shape = MorphShape(morph, progress, rotation = if (playing) spin else 0f)
    val desc = stringResource(if (playing) R.string.cd_pause else R.string.cd_play)
    // NB: no .shadow() here — elevation shadows on concave MaterialShapes stall the render thread
    // (Skia concave-shadow tessellation). An amber border ring gives the depth instead.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(100.dp)
            .scale(pressScale(source))
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(Emerald)
            .border(5.dp, Amber.copy(alpha = 0.55f), shape)
            .clickable(enabled = enabled, interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .semantics { contentDescription = desc; role = Role.Button },
    ) {
        AnimatedContent(
            targetState = if (buffering) 2 else if (playing) 1 else 0,
            transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
            label = "playIcon",
        ) { p ->
            when (p) {
                2 -> LoadingIndicator(color = Amber, modifier = Modifier.size(60.dp)) // fetching the ayah
                1 -> Icon(AppIcons.Pause, null, tint = Amber, modifier = Modifier.size(52.dp))
                else -> Icon(AppIcons.Play, null, tint = Amber, modifier = Modifier.size(52.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RepeatButton(active: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val morph = remember { Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Sunny) }
    val progress by animateFloatAsState(if (active) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "rMorph")
    val turn by animateFloatAsState(if (active) 180f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "rTurn")
    val container by animateColorAsState(if (active) Amber else Parchment, label = "rBg")
    val content by animateColorAsState(if (active) Color.White else AmberDeep, label = "rFg")
    val shape = MorphShape(morph, progress, rotation = turn / 4f)
    val desc = stringResource(R.string.cd_repeat)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(78.dp)
                .scale(pressScale(source))
                .clip(shape)
                .background(container)
                .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick)
                .semantics { contentDescription = desc; role = Role.Switch; stateDescription = if (active) "ON" else "OFF" },
        ) {
            Icon(AppIcons.Repeat, null, tint = content, modifier = Modifier.size(36.dp).graphicsRotate(turn))
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.repeat_ar), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) AmberDeep else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.repeat_fr), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberDeep)
    }
}

private fun Modifier.graphicsRotate(deg: Float) = this.then(Modifier.rotate(deg))

@Composable
private fun StepButton(
    icon: ImageVector, enabled: Boolean, labelAr: String, labelFr: String, description: String, onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (enabled) 1f else 0.35f)) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = Mint,
            contentColor = EmeraldDeep,
            interactionSource = source,
            modifier = Modifier.size(64.dp).scale(pressScale(source)),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, description, modifier = Modifier.size(36.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(labelAr, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(labelFr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** M3 Expressive connected button group: shapes morph between pill and rounded square on select. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedSelector(slow: Boolean, onChange: (Boolean) -> Unit) {
    val colors = ToggleButtonDefaults.colors(
        containerColor = Parchment, contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = Emerald, checkedContentColor = Cream,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ToggleButton(
            checked = slow, onCheckedChange = { onChange(true) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(), colors = colors,
            modifier = Modifier.weight(1f).height(58.dp).semantics { role = Role.RadioButton },
        ) { Text(stringResource(R.string.slow_label), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        ToggleButton(
            checked = !slow, onCheckedChange = { onChange(false) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(), colors = colors,
            modifier = Modifier.weight(1f).height(58.dp).semantics { role = Role.RadioButton },
        ) { Text(stringResource(R.string.normal_label), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
    }
}
