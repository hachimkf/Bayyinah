package com.bayyinah.app.ui

import android.graphics.RectF
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.bayyinah.app.ui.theme.QuranFont

/** Fractions of the screen used by the framing box (upper-middle area, wide for a verse line). */
private const val FRAME_LEFT = 0.06f
private const val FRAME_RIGHT = 0.94f
private const val FRAME_TOP = 0.15f
private const val FRAME_HEIGHT = 0.20f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReaderScreen(vm: MainViewModel, state: UiState, update: UpdateUi) {
    val haptics = LocalHapticFeedback.current
    var focusTick by remember { mutableIntStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val frame = remember(wPx, hPx) {
            RectF(wPx * FRAME_LEFT, hPx * FRAME_TOP, wPx * FRAME_RIGHT, hPx * (FRAME_TOP + FRAME_HEIGHT))
        }
        LaunchedEffect(frame) {
            vm.analyzer.frameInView = frame
            vm.analyzer.viewWidth = wPx.toInt()
            vm.analyzer.viewHeight = hPx.toInt()
        }

        CameraPreview(
            analyzer = vm.analyzer,
            focusPoint = Offset(frame.centerX(), frame.centerY()),
            focusTick = focusTick,
            torch = state.torch,
            modifier = Modifier.fillMaxSize(),
        )

        FramingOverlay(frame = frame, scanning = state.scanState == ScanState.Scanning)

        // Top bar: brand pill + torch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Tapping the logo checks GitHub for a new version.
            Surface(onClick = { vm.checkForUpdates(force = true) }, color = Color.Black.copy(alpha = 0.45f), shape = CircleShape) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Box(Modifier.size(12.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(Amber))
                    Spacer(Modifier.width(10.dp))
                    Text("بيّنة", color = Cream, fontFamily = QuranFont, fontSize = 22.sp)
                    Text("  ·  Bayyinah", color = Cream.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); vm.toggleTorch() },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (state.torch) Amber else Color.Black.copy(alpha = 0.45f),
                    contentColor = if (state.torch) EmeraldDeep else Cream,
                ),
            ) { Icon(if (state.torch) AppIcons.FlashOn else AppIcons.FlashOff, stringResource(R.string.cd_torch), Modifier.size(28.dp)) }
        }

        // Instruction + scan button, positioned just under the frame.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().offset(y = with(density) { frame.bottom.toDp() } + 16.dp),
        ) {
            HintLabel(state)
            Spacer(Modifier.height(20.dp))
            ScanButton(state.scanState) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                focusTick++
                vm.scan()
            }
        }

        // Bottom: expressive floating toolbar for manual navigation.
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                toolbarContainerColor = Cream, toolbarContentColor = EmeraldDeep,
                fabContainerColor = Emerald, fabContentColor = Cream,
            ),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        ) {
            ToolbarAction(AppIcons.Book, stringResource(R.string.index_ar), stringResource(R.string.index_fr)) { vm.openIndex(search = false) }
            Spacer(Modifier.width(6.dp))
            ToolbarAction(AppIcons.Search, stringResource(R.string.search_ar), stringResource(R.string.search_fr)) { vm.openIndex(search = true) }
        }

        UpdateBanner(
            update, vm,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 80.dp),
        )

        if (state.setupError != null) SetupErrorBanner(state.setupError, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ToolbarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, ar: String, fr: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(64.dp), shape = RoundedCornerShape(24.dp)) {
        Icon(icon, null, modifier = Modifier.size(26.dp), tint = Emerald)
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ar, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = EmeraldDeep)
            Text(fr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FramingOverlay(frame: RectF, scanning: Boolean) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "glow",
    )
    val glow = if (scanning) pulse else 0.55f
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val radius = 28.dp.toPx()
        val tl = Offset(frame.left, frame.top)
        val size = Size(frame.width(), frame.height())
        drawRect(Color.Black.copy(alpha = 0.58f))
        drawRoundRect(Color.Transparent, tl, size, CornerRadius(radius), blendMode = BlendMode.Clear)
        drawRoundRect(Amber.copy(alpha = 0.20f * glow), tl, size, CornerRadius(radius), style = Stroke(16.dp.toPx()))
        drawRoundRect(Amber.copy(alpha = 0.5f), tl, size, CornerRadius(radius), style = Stroke(1.5.dp.toPx()))
        val arm = 36.dp.toPx(); val sw = 6.dp.toPx(); val r = radius
        val (l, t, rt, b) = listOf(frame.left, frame.top, frame.right, frame.bottom)
        val path = Path().apply {
            moveTo(l, t + arm); lineTo(l, t + r); quadraticTo(l, t, l + r, t); lineTo(l + arm, t)
            moveTo(rt - arm, t); lineTo(rt - r, t); quadraticTo(rt, t, rt, t + r); lineTo(rt, t + arm)
            moveTo(rt, b - arm); lineTo(rt, b - r); quadraticTo(rt, b, rt - r, b); lineTo(rt - arm, b)
            moveTo(l + arm, b); lineTo(l + r, b); quadraticTo(l, b, l, b - r); lineTo(l, b - arm)
        }
        drawPath(path, Amber.copy(alpha = 0.6f + 0.4f * glow), style = Stroke(sw, cap = StrokeCap.Round))
    }
}

@Composable
private fun HintLabel(state: UiState) {
    val (ar, fr, color) = when (state.scanState) {
        ScanState.Loading -> Triple(R.string.loading_ar, R.string.loading_fr, Cream)
        ScanState.Scanning -> Triple(R.string.scanning_ar, R.string.scanning_fr, Amber)
        ScanState.NoMatch -> Triple(R.string.no_match_ar, R.string.no_match_fr, Amber)
        ScanState.Idle -> Triple(R.string.frame_hint_ar, R.string.frame_hint_fr, Cream)
    }
    AnimatedContent(targetState = Triple(ar, fr, color), label = "hint") { (a, f, c) ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(stringResource(a), color = c, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(stringResource(f), color = c.copy(alpha = 0.8f), fontSize = 15.sp, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanButton(state: ScanState, onClick: () -> Unit) {
    val busy = state == ScanState.Scanning || state == ScanState.Loading
    val description = stringResource(R.string.cd_scan)
    val spin by rememberInfiniteTransition(label = "scanSpin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "deg",
    )
    val shape = MaterialShapes.Cookie12Sided.toShape()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
            // Slowly turning scalloped halo + solid amber cookie (M3 Expressive shapes).
            Box(Modifier.size(96.dp).rotate(spin).clip(shape).background(Amber.copy(alpha = 0.35f)))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(shape)
                    .background(Amber)
                    .clickable(
                        enabled = !busy,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = androidx.compose.material3.ripple(),
                        onClick = onClick,
                    )
                    .semantics { contentDescription = description; role = Role.Button },
            ) {
                AnimatedContent(
                    targetState = busy,
                    transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
                    label = "scanIcon",
                ) { b ->
                    if (b) LoadingIndicator(color = Color.White, modifier = Modifier.size(54.dp))
                    else Icon(AppIcons.Listen, null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${stringResource(R.string.scan_ar)} • ${stringResource(R.string.scan_fr)}",
            color = Amber, fontWeight = FontWeight.Bold, fontSize = 15.sp,
        )
    }
}

@Composable
private fun SetupErrorBanner(message: String, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.padding(16.dp),
    ) {
        Text(
            "Données manquantes : ajoutez quran_warsh.db et tessdata/ara.traineddata dans assets/.\n$message",
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

