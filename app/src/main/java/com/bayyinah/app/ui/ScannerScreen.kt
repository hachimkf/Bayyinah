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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.bayyinah.app.ui.theme.EmeraldDeep

/**
 * Camera scanning: frame the verse in the box, tap the amber button; the matched ayah opens in
 * the player sheet (which floats above this screen). Camera permission is asked here, on first use.
 */
@Composable
fun ScannerRoute(vm: MainViewModel, state: UiState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted = it; if (!it) vm.backToHome() }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(android.Manifest.permission.CAMERA) }
    if (granted) ScannerScreen(vm, state)
    else Box(Modifier.fillMaxSize().background(Color.Black))
}

/** Fractions of the screen used by the framing box (upper-middle area, wide for a verse line). */
private const val FRAME_LEFT = 0.06f
private const val FRAME_RIGHT = 0.94f
private const val FRAME_TOP = 0.15f
private const val FRAME_HEIGHT = 0.20f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScannerScreen(vm: MainViewModel, state: UiState) {
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

        // Top bar: back + title + torch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            IconButton(
                onClick = { vm.backToHome() }, modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Cream),
            ) { Icon(AppIcons.Back, stringResource(R.string.cd_back), Modifier.size(28.dp)) }
            Spacer(Modifier.width(12.dp))
            Surface(color = Color.Black.copy(alpha = 0.45f), shape = CircleShape) {
                Text(stringResource(R.string.scan_title), color = Cream, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
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

        // Instruction + live "searching" indicator under the frame. Scanning is automatic;
        // tapping the indicator just refocuses the camera on the frame.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().offset(y = with(density) { frame.bottom.toDp() } + 16.dp),
        ) {
            HintLabel(state)
            Spacer(Modifier.height(20.dp))
            SearchingIndicator {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                focusTick++
            }
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
        ScanState.Loading -> Triple(R.string.loading, null, Cream)
        ScanState.Scanning -> Triple(R.string.scanning, R.string.scanning_sub, Cream)
        ScanState.NoMatch -> Triple(R.string.no_match, null, Amber)
        ScanState.Idle -> Triple(R.string.frame_hint, null, Cream)
    }
    AnimatedContent(targetState = Triple(ar, fr, color), label = "hint") { (a, f, c) ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(stringResource(a), color = c, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (f != null) Text(stringResource(f), color = c.copy(alpha = 0.8f), fontSize = 15.sp, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchingIndicator(onTap: () -> Unit) {
    val description = stringResource(R.string.cd_refocus)
    val spin by rememberInfiniteTransition(label = "scanSpin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "deg",
    )
    val shape = MaterialShapes.Cookie12Sided.toShape()
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
        Box(Modifier.size(96.dp).rotate(spin).clip(shape).background(Amber.copy(alpha = 0.30f)))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(shape)
                .background(Amber)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = androidx.compose.material3.ripple(), onClick = onTap)
                .semantics { contentDescription = description; role = Role.Button },
        ) { LoadingIndicator(color = Color.White, modifier = Modifier.size(56.dp)) }
    }
}

