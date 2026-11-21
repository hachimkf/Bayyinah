package com.bayyinah.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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

    // Dark status-bar icons over the cream background.
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = true }
    }
    LaunchedEffect(state.verse?.id) {
        if (state.verse != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    BackHandler(enabled = state.verse != null) { vm.dismissVerse() }
    BackHandler(enabled = state.verse == null && index.openSurah != null) { vm.back() }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(vm, index, update, sheetOpen = state.verse != null, setupError = state.setupError)
        AnimatedVisibility(
            visible = state.verse != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(),
            exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } + fadeOut(),
        ) {
            val verse = state.verse
            if (verse != null) VerseSheet(vm = vm, state = state, verse = verse)
        }
    }
}
