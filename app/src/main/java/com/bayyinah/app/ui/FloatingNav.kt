package com.bayyinah.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayyinah.app.R
import com.bayyinah.app.ui.theme.Amber
import com.bayyinah.app.ui.theme.Cream
import com.bayyinah.app.ui.theme.EmeraldDeep

private data class Tab(val screen: Screen, val icon: ImageVector, val label: Int)

private val Tabs = listOf(
    Tab(Screen.Home, AppIcons.Home, R.string.nav_home),
    Tab(Screen.Index, AppIcons.List, R.string.nav_index),
    Tab(Screen.History, AppIcons.Heart, R.string.nav_saved),
    Tab(Screen.Settings, AppIcons.Settings, R.string.nav_settings),
)

fun Screen.isTab() = this == Screen.Home || this == Screen.Index || this == Screen.History || this == Screen.Settings

/** Floating pill navigation bar (Material 3 Expressive style): the chosen tab grows into an amber pill with its name. */
@Composable
fun FloatingNav(current: Screen, onSelect: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Surface(
        color = EmeraldDeep, shape = CircleShape, shadowElevation = 10.dp,
        modifier = modifier.navigationBarsPadding().padding(bottom = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(8.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            Tabs.forEach { t ->
                val sel = t.screen == current
                val bg by animateColorAsState(if (sel) Amber else EmeraldDeep, label = "tabBg")
                val fg by animateColorAsState(if (sel) EmeraldDeep else Cream.copy(alpha = 0.75f), label = "tabFg")
                Surface(
                    onClick = { if (!sel) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(t.screen) } },
                    color = bg, contentColor = fg, shape = CircleShape,
                    modifier = Modifier.height(56.dp).semantics { role = Role.Tab; selected = sel },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Icon(t.icon, stringResource(t.label), Modifier.size(26.dp))
                        AnimatedVisibility(sel, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                            Row {
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(t.label), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
