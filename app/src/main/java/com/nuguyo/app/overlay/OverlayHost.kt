package com.nuguyo.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nuguyo.app.R

/**
 * `WindowManager` 에 직접 붙이는 Compose 창.
 *
 * Activity 를 띄우지 않는 이유: Android 12 부터 백그라운드 액티비티 실행이 막혀 있고,
 * 풀스크린 인텐트 알림도 Android 14 부터 전화/알람 앱이 아니면 자동으로 허용되지 않는다.
 * 통화 화면 위에 무언가를 그릴 수 있는 정식 수단은 `TYPE_APPLICATION_OVERLAY` 뿐이다.
 *
 * `ComposeView` 는 뷰 트리에서 Lifecycle / ViewModelStore / SavedStateRegistry 오너를
 * 찾지 못하면 컴포지션을 시작하지 못한다. Activity 밖이라 그 셋을 여기서 직접 구현한다.
 */
class OverlayHost(context: Context) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val themedContext = ContextThemeWrapper(context, R.style.Theme_Nuguyo)
    private val windowManager = context.getSystemService(WindowManager::class.java)

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var view: ComposeView? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // NOT_FOCUSABLE 이어도 창 자신에 대한 터치는 받는다. 키보드 포커스를 빼앗지
        // 않으므로 통화 화면의 조작을 방해하지 않는다.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    val isShowing: Boolean get() = view != null

    /** 사용자가 끌어다 놓은 세로 위치. 다음 팝업에서 그대로 재사용한다. */
    val offsetY: Int get() = params.y

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    /**
     * 창을 띄우거나, 이미 떠 있으면 내용만 갈아끼운다.
     * @return 실패하면 false. 호출부는 알림 폴백으로 넘어가야 한다.
     */
    fun show(offsetY: Int, content: @Composable () -> Unit): Boolean {
        if (!Settings.canDrawOverlays(themedContext)) {
            Log.w(TAG, "다른 앱 위에 표시 권한이 없다")
            return false
        }
        val manager = windowManager ?: return false

        view?.let { existing ->
            existing.setContent(content)
            return true
        }

        val composeView = ComposeView(themedContext).apply {
            setViewTreeLifecycleOwner(this@OverlayHost)
            setViewTreeViewModelStoreOwner(this@OverlayHost)
            setViewTreeSavedStateRegistryOwner(this@OverlayHost)
            setContent(content)
        }
        params.y = offsetY.coerceAtLeast(0)

        // 컴포지션이 시작될 수 있도록 붙이기 전에 RESUMED 로 올려 둔다.
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        return try {
            manager.addView(composeView, params)
            view = composeView
            true
        } catch (t: Throwable) {
            // 제조사 정책이나 권한 회수로 addView 가 거부될 수 있다.
            Log.w(TAG, "오버레이 창을 붙이지 못했다", t)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            false
        }
    }

    /** 세로로 끌어 옮긴다. */
    fun moveBy(dy: Int) {
        val current = view ?: return
        params.y = (params.y + dy).coerceAtLeast(0)
        runCatching { windowManager?.updateViewLayout(current, params) }
    }

    fun dismiss() {
        view?.let { existing ->
            runCatching { windowManager?.removeView(existing) }
        }
        view = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }

    private companion object {
        const val TAG = "OverlayHost"
    }
}
