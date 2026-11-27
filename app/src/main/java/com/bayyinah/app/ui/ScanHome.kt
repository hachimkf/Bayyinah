package com.bayyinah.app.ui

import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.bayyinah.app.ui.theme.Emerald
import com.bayyinah.app.ui.theme.EmeraldDeep
import com.bayyinah.app.ui.theme.Jade
import com.bayyinah.app.ui.theme.Mint
import com.bayyinah.app.ui.theme.QuranFont
import com.bayyinah.app.ui.theme.VerseTextStyle

/**
 * Home, Material 3 Expressive style: a white card that shows how to scan (an ayah lit inside the
 * frame), a card of quick settings rows, and one huge pill button that opens the camera.
 */
@Composable
fun ScanHomeScreen(vm: MainViewModel, state: UiState, update: UpdateUi, sample: List<Verse>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().background(Cream).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = SidePad, end = SidePad, top = 12.dp, bottom = NavSpace),
    ) {
        // Name in a soft glowing pill, logo at the side.
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            Box(
                Modifier.align(Alignment.Center).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White, Cream.copy(alpha = 0f))))
                    .padding(horizontal = 34.dp, vertical = 4.dp),
            ) { Text("بيّنة", fontFamily = QuranFont, fontSize = 34.sp, color = EmeraldDeep) }
            Image(
                painterResource(R.drawable.app_logo), null,
                Modifier.align(Alignment.CenterEnd).size(48.dp).clip(CircleShape),
            )
        }
        if (update != UpdateUi.Hidden) UpdateBanner(update, vm)

        // «هكذا تمسح الآية»: a little page with one ayah lit inside the scan frame.
        Surface(color = Color.White, shape = RoundedCornerShape(36.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.how_to_scan), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald,
                    modifier = Modifier.padding(horizontal = 24.dp))
                DemoPage(sample)
            }
        }

        // Quick settings rows (like the reference's Duration / Haptics / Sound card).
        Surface(color = Mint, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                SettingRow(AppIcons.Listen, stringResource(R.string.row_speed)) { SpeedChips(state.slow, vm::setSlow) }
                HorizontalDivider(color = Color.White)
                SettingRow(AppIcons.Repeat, stringResource(R.string.row_repeat)) {
                    GreenSwitch(state.repeat) { vm.toggleRepeat() }
                }
                HorizontalDivider(color = Color.White)
                SettingRow(AppIcons.FlashOn, stringResource(R.string.row_torch)) {
                    GreenSwitch(state.torch) { vm.toggleTorch() }
                }
            }
        }

        // The huge pill.
        Button(
            onClick = { vm.openScanner() },
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = Cream),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(104.dp),
        ) {
            Icon(AppIcons.Scan, null, Modifier.size(34.dp), tint = Amber)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.scan_big), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** A few ayat on a white page; soft white fades above and below leave one ayah lit in the frame. */
@Composable
private fun DemoPage(sample: List<Verse>) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(28.dp))) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val frame = remember(w, h) { RectF(w * 0.06f, h * 0.30f, w * 0.94f, h * 0.70f) }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().wrapContentHeight(unbounded = true).padding(horizontal = 20.dp),
        ) {
            // ayat 3–5 of Al-Fatiha: the middle one sits in the frame
            sample.drop(2).take(3).forEach { v ->
                Text(v.verseText, textAlign = TextAlign.Center, maxLines = 1,
                    style = VerseTextStyle.copy(fontSize = 26.sp, lineHeight = 52.sp, textDirection = TextDirection.Rtl))
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.White, 0.18f to Color.White.copy(alpha = 0.85f), 0.30f to Color.White.copy(alpha = 0f),
                    0.70f to Color.White.copy(alpha = 0f), 0.82f to Color.White.copy(alpha = 0.85f), 1f to Color.White,
                ),
            ),
        )
        ScanFrame(frame, Modifier.fillMaxSize(), accent = Emerald)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(68.dp)) {
        Icon(icon, null, tint = Emerald, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = EmeraldDeep, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun GreenSwitch(on: Boolean, onToggle: () -> Unit) {
    Switch(
        checked = on, onCheckedChange = { onToggle() },
        colors = SwitchDefaults.colors(
            checkedTrackColor = EmeraldDeep, checkedThumbColor = Amber,
            uncheckedTrackColor = Color.White, uncheckedThumbColor = Color(0xFF9DBFA6), uncheckedBorderColor = Color(0xFF9DBFA6),
        ),
    )
}

/** Connected chips «بطيء | عادي», like the reference's 1 min / 3 min / 10 min. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedChips(slow: Boolean, onChange: (Boolean) -> Unit) {
    val colors = ToggleButtonDefaults.colors(
        containerColor = Color.White, contentColor = EmeraldDeep,
        checkedContainerColor = EmeraldDeep, checkedContentColor = Cream,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        ToggleButton(checked = slow, onCheckedChange = { onChange(true) }, colors = colors,
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(), modifier = Modifier.height(44.dp)) {
            Text(stringResource(R.string.slow_label), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        ToggleButton(checked = !slow, onCheckedChange = { onChange(false) }, colors = colors,
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(), modifier = Modifier.height(44.dp)) {
            Text(stringResource(R.string.normal_label), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/** Round torch toggle for the camera page (lime when on). */
@Composable
fun TorchButton(on: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, modifier = Modifier.size(60.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = if (on) Amber else Jade, contentColor = EmeraldDeep),
    ) { Icon(if (on) AppIcons.FlashOn else AppIcons.FlashOff, stringResource(R.string.cd_torch), Modifier.size(28.dp)) }
}
