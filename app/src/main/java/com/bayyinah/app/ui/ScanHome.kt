package com.bayyinah.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayyinah.app.R
import com.bayyinah.app.ui.theme.Amber
import com.bayyinah.app.ui.theme.Cream
import com.bayyinah.app.ui.theme.Emerald
import com.bayyinah.app.ui.theme.EmeraldDeep
import com.bayyinah.app.ui.theme.Mint
import com.bayyinah.app.ui.theme.QuranFont

/**
 * Home tab: one huge button — scan the ayah in her book. Everything else (index, saved,
 * settings) is one tap away in the floating bar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScanHomeScreen(vm: MainViewModel, update: UpdateUi) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Cream, 0.55f to Cream, 1f to Mint)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().statusBarsPadding()
                .padding(start = SidePad, end = SidePad, top = 16.dp, bottom = NavSpace),
        ) {
            // Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.app_logo), null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(10.dp))
                Text("بيّنة", fontFamily = QuranFont, fontSize = 32.sp, color = EmeraldDeep)
            }
            if (update != UpdateUi.Hidden) {
                Spacer(Modifier.height(12.dp))
                UpdateBanner(update, vm)
            }
            Spacer(Modifier.weight(1f))

            BigScanButton { vm.openScanner() }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.scan_home_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.scan_home_sub), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, lineHeight = 26.sp)
            Spacer(Modifier.height(28.dp))
            Surface(onClick = { vm.openIndex() }, color = Cream, contentColor = EmeraldDeep, shape = CircleShape) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Icon(AppIcons.List, null, tint = Emerald, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.choose_manually), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** 260 dp expressive "cookie": a slowly turning halo around a big emerald button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BigScanButton(onClick: () -> Unit) {
    val anim = rememberInfiniteTransition(label = "scanBtn")
    val spin by anim.animateFloat(0f, 360f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "spin")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val desc = stringResource(R.string.scan_home_title)
    val halo = MaterialShapes.Cookie12Sided.toShape()
    val core = MaterialShapes.Cookie9Sided.toShape()
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(270.dp)) {
        // One slowly turning halo only (cheap on low-end phones: rotation happens in the layer).
        Box(Modifier.size(262.dp).rotate(spin).clip(halo).background(Emerald.copy(alpha = 0.22f)))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(208.dp)
                .scale(if (pressed) 0.92f else 1f)
                .clip(core)
                .background(Brush.linearGradient(listOf(Emerald, EmeraldDeep)))
                .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick)
                .semantics { contentDescription = desc; role = Role.Button },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(AppIcons.Scan, null, tint = Amber, modifier = Modifier.size(84.dp))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.scan_home_btn), color = Cream, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            }
        }
    }
}
