package com.bayyinah.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayyinah.app.BuildConfig
import com.bayyinah.app.R
import com.bayyinah.app.ui.theme.Amber
import com.bayyinah.app.ui.theme.Cream
import com.bayyinah.app.ui.theme.Emerald
import com.bayyinah.app.ui.theme.EmeraldDeep
import com.bayyinah.app.ui.theme.Ink
import com.bayyinah.app.ui.theme.Mint
import com.bayyinah.app.ui.theme.Sand

/** Updates (version, check now, auto-check), reciter choice, about/attribution. */
@Composable
fun SettingsScreen(vm: MainViewModel, state: UiState, update: UpdateUi) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = SidePad, vertical = 16.dp)) {
            RoundIconButton(AppIcons.Back, stringResource(R.string.cd_back), { vm.backToHome() })
            Text(stringResource(R.string.settings_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(52.dp))
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SidePad).navigationBarsPadding(),
        ) {
            // Updates
            Section(stringResource(R.string.settings_updates), color = EmeraldDeep, content = Cream) {
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), fontSize = 15.sp, color = Cream.copy(alpha = 0.85f))
                Spacer(Modifier.height(10.dp))
                if (update != UpdateUi.Hidden) {
                    UpdateBanner(update, vm)
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = { vm.checkForUpdates(force = true) },
                    enabled = update !is UpdateUi.Checking && update !is UpdateUi.Downloading,
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = EmeraldDeep),
                    shape = CircleShape, modifier = Modifier.fillMaxWidth().height(60.dp),
                ) { Text(stringResource(R.string.settings_check_now), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.autoUpdate, onCheckedChange = vm::setAutoUpdate,
                        colors = SwitchDefaults.colors(checkedTrackColor = Amber, checkedThumbColor = EmeraldDeep),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_auto), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_auto_desc), fontSize = 13.sp, color = Cream.copy(alpha = 0.75f))
                    }
                }
            }

            // Whole Mushaf: a pack file she receives and imports (never bundled with the app)
            val pickPack = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
            ) { uri -> if (uri != null) vm.importMushafPack(uri) }
            // Scan pack only (the Moroccan font and text download by themselves). Shown only while it's
            // missing; once in, the card disappears (no replace / delete, nothing removed by accident).
            if (state.mushafLines == null) Section(stringResource(R.string.settings_mushaf), color = Mint, content = EmeraldDeep) {
                val installed = false
                Text(
                    stringResource(if (installed) R.string.settings_mushaf_ready else R.string.settings_mushaf_none),
                    fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                when (state.mushafImport) {
                    MushafImport.Working -> R.string.settings_mushaf_working
                    MushafImport.Done -> R.string.settings_mushaf_done
                    MushafImport.Failed -> R.string.settings_mushaf_failed
                    MushafImport.Idle -> null
                }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(it), fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { pickPack.launch(arrayOf("*/*")) },
                    enabled = state.mushafImport != MushafImport.Working,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep, contentColor = Cream),
                    shape = CircleShape, modifier = Modifier.fillMaxWidth().height(60.dp),
                ) {
                    Text(stringResource(if (installed) R.string.settings_mushaf_replace else R.string.settings_mushaf_import), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Reciter
            Section(stringResource(R.string.settings_reciter), color = Mint, content = EmeraldDeep) {
                state.reciters.forEach { rc ->
                    val sel = rc.id == state.reciter?.id
                    Surface(
                        onClick = { vm.selectReciter(rc) },
                        color = if (sel) EmeraldDeep else Cream, contentColor = if (sel) Cream else EmeraldDeep,
                        shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().height(64.dp).padding(vertical = 3.dp),
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Text(rc.nameAr, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(NavSpace))
        }
    }
}

@Composable
private fun Section(title: String, color: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color, body: @Composable () -> Unit) {
    Surface(color = color, contentColor = content, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            body()
        }
    }
}
