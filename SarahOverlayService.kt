package com.sarah.assistant

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Draws the 3D Sarah over other apps (SYSTEM_ALERT_WINDOW). Tap her to talk. */
class SarahOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private lateinit var wm: WindowManager
    private var view: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach(); savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        SarahBrain.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val d = resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            (200 * d).toInt(), (280 * d).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.END; x = 8; y = 80 }

        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SarahOverlayService)
            setViewTreeSavedStateRegistryOwner(this@SarahOverlayService)
            setViewTreeViewModelStoreOwner(this@SarahOverlayService)
            setContent {
                val s by SarahBrain.state.collectAsState()
                Avatar3DView(
                    speaking = s.speaking, amplitude = s.amplitude,
                    modifier = Modifier.fillMaxSize().clickable { SarahBrain.startListening() }
                )
            }
        }
        wm.addView(view, lp)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        view?.let { wm.removeView(it) }; view = null
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
