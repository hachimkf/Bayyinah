package com.bayyinah.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Root: the home screen, with the verse player sheet floating above it. */
@Composable
fun BayyinahApp(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val index by vm.index.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // Dark status-bar icons over cream; light ones over the camera.
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let {
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = state.screen != Screen.Scanner
        }
        // Never let the screen sleep while she follows an ayah (player open) or scans.
        view.keepScreenOn = state.verse != null || state.screen == Screen.Scanner
    }
    LaunchedEffect(state.verse?.id) {
        if (state.verse != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    BackHandler(enabled = state.verse != null) { vm.dismissVerse() }
    BackHandler(enabled = state.verse == null && state.screen != Screen.Home) { vm.backToHome() }
    // Index tab with a surah open: back closes the surah first.
    BackHandler(enabled = state.verse == null && state.screen == Screen.Index && index.openSurah != null) { vm.back() }
    // Home: the first back swipe only shows a hint; a second one within 2 s leaves the app.
    var lastBack by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = state.verse == null && state.screen == Screen.Home) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBack < 2000) (view.context as? Activity)?.finish()
        else {
            lastBack = now
            android.widget.Toast.makeText(view.context, com.bayyinah.app.R.string.back_again, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Arabic only: the whole app is laid out right-to-left.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    val sheetOver = state.verse != null && state.screen != Screen.Reader
    // Blur (Android 12+) and dim what's behind the player so the eye goes to the ayah.
    val blur by animateDpAsState(if (sheetOver) 14.dp else 0.dp, label = "blur")
    val dim by animateFloatAsState(if (sheetOver) 0.38f else 0f, label = "dim")
    Box(Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedContent(
            modifier = Modifier.blur(blur),
            targetState = state.screen,
            transitionSpec = { (fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Home -> ScanHomeScreen(vm, update)
                Screen.Index -> IndexScreen(vm, index, setupError = state.setupError)
                Screen.History -> HistoryScreen(vm, index)
                // The camera is only bound while the scanner is shown (battery).
                Screen.Scanner -> ScannerRoute(vm, state)
                Screen.Settings -> SettingsScreen(vm, state, update)
                // After a scan: full-screen player, camera closed.
                Screen.Reader -> state.verse?.let { v -> VerseSheet(vm = vm, state = state, verse = v, fullScreen = true, favorite = index.favorites.any { it.id == v.id }) }
                    ?: androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(com.bayyinah.app.ui.theme.Cream))
            }
        }
        AnimatedVisibility(
            visible = state.screen.isTab() && state.verse == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            FloatingNav(state.screen, onSelect = { s ->
                when (s) {
                    Screen.Home -> vm.backToHome()
                    Screen.Index -> vm.openIndex()
                    Screen.History -> vm.openHistory()
                    else -> vm.openSettings()
                }
            })
        }
        if (dim > 0f) Box(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = dim))
                .pointerInput(Unit) { detectTapGestures { vm.dismissVerse() } },
        )
        AnimatedVisibility(
            visible = sheetOver,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(),
            exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } + fadeOut(),
        ) {
            val verse = state.verse
            if (verse != null) VerseSheet(vm = vm, state = state, verse = verse, favorite = index.favorites.any { it.id == verse.id })
        }
    }
    }
}
