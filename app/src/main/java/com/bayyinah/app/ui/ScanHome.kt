package com.bayyinah.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayyinah.app.R
import com.bayyinah.app.data.Verse
import com.bayyinah.app.ui.theme.Amber
import com.bayyinah.app.ui.theme.Cream
import com.bayyinah.app.ui.theme.EmeraldDeep
import com.bayyinah.app.ui.theme.Jade
import com.bayyinah.app.ui.theme.Night
import com.bayyinah.app.ui.theme.VerseTextStyle

/**
 * Home: shows how to scan — a Quran page with the light focused on one ayah inside the frame,
 * and the scan line sweeping. Tapping the page (or the round scan button in the bar) opens the camera.
 */
@Composable
fun ScanHomeScreen(vm: MainViewModel, state: UiState, update: UpdateUi, sample: List<Verse>) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Night)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.openScanner() },
    ) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val frame = remember(w, h) { scanFrame(w, h) }
        val frameMid = with(density) { frame.centerY().toDp() }

        // The page: ayat centred on the frame (our own KFGQPC text, so nothing copyrighted is shown).
        Box(Modifier.fillMaxSize().background(Cream))
        Box(Modifier.fillMaxWidth().height(frameMid * 2), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(56.dp),
                modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).padding(horizontal = 26.dp),
            ) {
                sample.forEach { v ->
                    Text(
                        v.verseText, textAlign = TextAlign.Center,
                        style = VerseTextStyle.copy(fontSize = 32.sp, lineHeight = 64.sp, textDirection = TextDirection.Rtl),
                    )
                }
            }
        }
        // Light only inside the frame: dark gradients from the top and the bottom.
        val top = FRAME_TOP; val bot = FRAME_TOP + FRAME_HEIGHT
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Night, (top - 0.12f) to Night.copy(alpha = 0.95f), (top - 0.04f) to Night.copy(alpha = 0.80f),
                    top to Night.copy(alpha = 0.55f), (top + 0.05f) to Night.copy(alpha = 0f), (bot - 0.05f) to Night.copy(alpha = 0f),
                    bot to Night.copy(alpha = 0.55f), (bot + 0.04f) to Night.copy(alpha = 0.80f), (bot + 0.12f) to Night.copy(alpha = 0.95f), 1f to Night,
                ),
            ),
        )
        ScanFrame(frame, Modifier.fillMaxSize())

        // Top: logo · torch (the torch choice carries into the camera page).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = SidePad, vertical = 12.dp),
        ) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(Jade), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.app_logo), null, Modifier.size(44.dp).clip(CircleShape))
            }
            Spacer(Modifier.weight(1f))
            TorchButton(state.torch) { vm.toggleTorch() }
        }
        if (update != UpdateUi.Hidden) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 84.dp, start = SidePad, end = SidePad)) { UpdateBanner(update, vm) }
        }

        Box(Modifier.fillMaxWidth().offset(y = with(density) { frame.bottom.toDp() } + 24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.frame_hint), color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(Night.copy(alpha = 0.85f)).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

/** Round jade torch toggle (amber when on). */
@Composable
fun TorchButton(on: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, modifier = Modifier.size(60.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = if (on) Amber else Jade, contentColor = EmeraldDeep),
    ) { Icon(if (on) AppIcons.FlashOn else AppIcons.FlashOff, stringResource(R.string.cd_torch), Modifier.size(28.dp)) }
}
