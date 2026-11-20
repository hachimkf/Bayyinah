package com.bayyinah.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bayyinah.app.ui.BayyinahApp
import com.bayyinah.app.ui.MainViewModel
import com.bayyinah.app.ui.theme.BayyinahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            BayyinahTheme {
                val vm: MainViewModel = viewModel()
                // Stop recitation when the app leaves the screen (battery + courtesy).
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onAppBackgrounded() }
                BayyinahApp(vm)
            }
        }
    }
}
