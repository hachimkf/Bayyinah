package com.bayyinah.app.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayyinah.app.R
import com.bayyinah.app.ui.theme.Amber
import com.bayyinah.app.ui.theme.Cream
import com.bayyinah.app.ui.theme.EmeraldDeep

/** Expressive update card (dark emerald, amber action) shown over the camera screen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateBanner(state: UpdateUi, vm: MainViewModel, modifier: Modifier = Modifier) {
    if (state == UpdateUi.Hidden) return
    val activity = LocalContext.current as? Activity
    Surface(color = EmeraldDeep, contentColor = Cream, shape = RoundedCornerShape(28.dp), modifier = modifier.fillMaxWidth()) {
        AnimatedContent(targetState = state, contentKey = { it::class }, label = "update") { s ->
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(MaterialShapes.Sunny.toShape()).background(Amber), contentAlignment = Alignment.Center) {
                        if (s is UpdateUi.Checking) LoadingIndicator(color = EmeraldDeep, modifier = Modifier.size(32.dp))
                        else Text("↑", color = EmeraldDeep, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        when (s) {
                            UpdateUi.Checking -> Text(stringResource(R.string.update_checking), fontWeight = FontWeight.Bold)
                            UpdateUi.UpToDate -> Text(stringResource(R.string.update_uptodate), fontWeight = FontWeight.Bold)
                            is UpdateUi.Available -> {
                                Text(stringResource(R.string.update_available_ar), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(stringResource(R.string.update_available_fr, s.info.version), fontSize = 13.sp, color = Cream.copy(alpha = 0.8f))
                            }
                            is UpdateUi.Downloading -> {
                                Text(stringResource(R.string.update_downloading, (s.progress * 100).toInt()), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                LinearWavyProgressIndicator(progress = { s.progress }, color = Amber, trackColor = Cream.copy(alpha = 0.2f), modifier = Modifier.fillMaxWidth().height(10.dp))
                            }
                            is UpdateUi.Ready -> {
                                Text(stringResource(R.string.update_ready_ar), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(stringResource(R.string.update_ready_fr) + " · ${s.info.version}", fontSize = 13.sp, color = Cream.copy(alpha = 0.8f))
                            }
                            is UpdateUi.Failed -> Text(stringResource(R.string.update_failed), fontWeight = FontWeight.Bold)
                            UpdateUi.Hidden -> {}
                        }
                    }
                    if (s !is UpdateUi.Checking) {
                        IconButton(onClick = { vm.dismissUpdate() }, modifier = Modifier.size(48.dp)) {
                            Icon(AppIcons.Close, stringResource(R.string.cd_close), tint = Cream.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                        }
                    }
                }
                // Action on its own row so the bilingual text never gets squeezed.
                val action: Pair<String, () -> Unit>? = when (s) {
                    is UpdateUi.Available, is UpdateUi.Failed -> stringResource(R.string.update_button) to { vm.downloadUpdate() }
                    is UpdateUi.Ready -> stringResource(R.string.update_install) to { activity?.let(vm::installUpdate) }
                    else -> null
                }
                if (action != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                        ActionButton(action.first, action.second)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = EmeraldDeep),
        shape = CircleShape,
        modifier = Modifier.height(52.dp),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

