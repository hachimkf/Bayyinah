package com.bayyinah.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.bayyinah.app.ui.theme.Jade
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

/**
 * Floating bar: a jade pill with the four tabs (icon + small name, the chosen one on a deep-green
 * indicator) and, apart from it, a round scan button that opens the camera.
 */
@Composable
fun FloatingNav(current: Screen, onSelect: (Screen) -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.navigationBarsPadding().padding(bottom = 16.dp),
    ) {
        Surface(color = Jade, shape = CircleShape, shadowElevation = 8.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Tabs.forEach { t ->
                    val sel = t.screen == current
                    val ind by animateColorAsState(if (sel) EmeraldDeep else Jade, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "ind")
                    val fg by animateColorAsState(if (sel) Cream else EmeraldDeep, label = "fg")
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(66.dp).clip(RoundedCornerShape(22.dp))
                            .clickable { if (!sel) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(t.screen) } }
                            .padding(vertical = 4.dp)
                            .semantics { role = Role.Tab; selected = sel },
                    ) {
                        Box(Modifier.width(52.dp).height(32.dp).clip(CircleShape).background(ind), contentAlignment = Alignment.Center) {
                            Icon(t.icon, null, tint = fg, modifier = Modifier.size(22.dp))
                        }
                        Text(stringResource(t.label), fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp, color = EmeraldDeep, maxLines = 1)
                    }
                }
            }
        }
        // The scan button, apart from the tabs.
        Surface(
            onClick = { haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); onScan() },
            color = EmeraldDeep, contentColor = Amber, shape = CircleShape, shadowElevation = 8.dp,
            modifier = Modifier.size(68.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(AppIcons.Scan, stringResource(R.string.scan_title), Modifier.size(32.dp)) }
        }
    }
}
