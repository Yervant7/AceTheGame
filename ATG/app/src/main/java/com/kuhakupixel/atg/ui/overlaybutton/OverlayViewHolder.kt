package com.kuhakupixel.atg.ui.overlaybutton

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.kuhakupixel.atg.ui.overlaybutton.service.FloatingService
import com.kuhakupixel.atg.ui.overlaybutton.service.overlayViewFactory

class OverlayViewHolder(

    val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    val alpha: Float,
    val service: FloatingService,
) {

    var view: ComposeView? = null
        get() = field

    val originalWindowFlag: Int
    val originalWindowAlpha: Float

    init {
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.alpha = alpha
        // save original params
        originalWindowFlag = params.flags
        originalWindowAlpha = params.alpha
    }

    fun getParams(): WindowManager.LayoutParams {
        return this.params

    }

    fun updateViewPos(x: Int, y: Int) {
        params.x = x
        params.y = y
        windowManager.updateViewLayout(view, params)
    }


    fun disable() {
        params.alpha = 0f
        // not touchable so it won't block input when disabled
        params.flags =
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowManager.updateViewLayout(view, params)
    }

    fun enable() {
        params.alpha = originalWindowAlpha
        // not touchable so it won't block input when disabled
        params.flags = originalWindowFlag
        windowManager.updateViewLayout(view, params)
    }

    fun setContent(content: @Composable (composeView: ComposeView) -> Unit = {}) {
        this.view = overlayViewFactory(service = service, content = content)

    }
}