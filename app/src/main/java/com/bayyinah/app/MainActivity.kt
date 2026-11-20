package com.bayyinah.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bayyinah.app.ui.MainViewModel
import com.bayyinah.app.ui.PermissionScreen
import com.bayyinah.app.ui.BayyinahApp
import com.bayyinah.app.ui.theme.BayyinahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light icons over the dark camera feed.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            BayyinahTheme {
                val vm: MainViewModel = viewModel()
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (BuildConfig.DEBUG) intent?.getStringExtra("ocr_test")?.let { vm.debugRecognizeFile(it) }
                }
                val context = LocalContext.current

                var granted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                    )
                }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted = it }

                // Stop recitation when the app leaves the screen (battery + courtesy).
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onAppBackgrounded() }

                if (granted) BayyinahApp(vm)
                else PermissionScreen { launcher.launch(Manifest.permission.CAMERA) }
            }
        }
    }
}
