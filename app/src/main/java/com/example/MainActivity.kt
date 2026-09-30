package com.example

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aetherdeck.ui.AetherDeckRootApp
import com.aetherdeck.ui.AetherDeckViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AetherDeckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { state ->
                    // Keep screen awake option
                    if (state.keepScreenAwakeOnLaunch) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }

                    // Optional Immersive Fullscreen mode while respecting safe areas when visible
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    if (state.immersiveMode) {
                        insetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        insetsController.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }

        setContent {
            AetherDeckRootApp(viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshEmulatorsAndControllers()
        viewModel.refreshNetworkStatus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val state = viewModel.settings.value
        val consumed = viewModel.gamepadController.handleKeyEvent(
            event = event,
            swapAB = state.swapAB,
            swapXY = state.swapXY
        )
        if (consumed) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val state = viewModel.settings.value
        val consumed = viewModel.gamepadController.handleMotionEvent(
            event = event,
            deadzone = state.deadzone
        )
        if (consumed) return true
        return super.dispatchGenericMotionEvent(event)
    }
}
