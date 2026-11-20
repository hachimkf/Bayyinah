package com.bayyinah.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayyinah.app.R
import com.bayyinah.app.ui.theme.Amber
import com.bayyinah.app.ui.theme.Cream
import com.bayyinah.app.ui.theme.Emerald
import com.bayyinah.app.ui.theme.EmeraldSoft

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    // Dark status-bar icons on the cream background (camera screen uses light ones).
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = false }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().background(Cream).padding(28.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp).background(EmeraldSoft, CircleShape),
        ) { Icon(AppIcons.Camera, null, tint = Emerald, modifier = Modifier.size(56.dp)) }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.perm_title_ar), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Emerald)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.perm_body_fr), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Cream),
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) { Text(stringResource(R.string.perm_button), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Amber) }
    }
}
