package com.reactnativekeyboardcontroller.modules.statusbar

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.uimanager.PixelUtil
import com.reactnativekeyboardcontroller.BuildConfig
import com.reactnativekeyboardcontroller.log.Logger
import com.reactnativekeyboardcontroller.views.EdgeToEdgeReactViewGroup
import com.reactnativekeyboardcontroller.views.EdgeToEdgeViewRegistry
import java.lang.ref.WeakReference

private val TAG = StatusBarManagerCompatModuleImpl::class.qualifiedName

/**
 * A version-agnostic status bar manager that uses pure Android APIs.
 * This implementation doesn't depend on React Native's internal StatusBarModule,
 * making it compatible across all RN versions.
 */
class StatusBarManagerCompatModuleImpl(
  private val mReactContext: ReactApplicationContext,
) {
  private var controller: WindowInsetsControllerCompat? = null
  private var lastActivity = WeakReference<Activity?>(null)

  /**
   * This method always uses new API, because original implementation may mess up system insets
   * and they will never be restored properly (even if you enabled edge-to-edge mode etc.)
   */
  fun setHidden(hidden: Boolean) {
    UiThreadUtil.runOnUiThread {
      if (hidden) {
        getController()?.hide(WindowInsetsCompat.Type.statusBars())
      } else {
        getController()?.show(WindowInsetsCompat.Type.statusBars())
      }
    }
  }

  // Suppressing deprecation: statusBarColor is deprecated in API 35+ in favor of edge-to-edge,
  // but we still need to support apps that aren't using edge-to-edge mode.
  @Suppress("DEPRECATION", "detekt:ReturnCount")
  @SuppressLint("ObsoleteSdkInt")
  fun setColor(
    color: Int,
    animated: Boolean,
  ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return
    }

    if (BuildConfig.IS_EDGE_TO_EDGE_ENABLED && isEnabled()) {
      Logger.w(TAG, "StatusBarModule: Ignored status bar change, current activity is edge-to-edge.")
      return
    }

    val activity = mReactContext.currentActivity
    if (activity == null) {
      Logger.w(
        TAG,
        "StatusBarManagerCompatModule: Ignored status bar change, current activity is null.",
      )
      return
    }

    UiThreadUtil.runOnUiThread {
      val window = activity.window

      if (animated) {
        val curColor: Int = window.statusBarColor
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), curColor, color)
        colorAnimation.addUpdateListener { animator ->
          window.statusBarColor = animator.animatedValue as Int
        }
        colorAnimation.setDuration(DEFAULT_ANIMATION_TIME).startDelay = 0
        colorAnimation.start()
      } else {
        window.statusBarColor = color
      }
    }
  }

  // Suppressing deprecation: FLAG_TRANSLUCENT_STATUS is deprecated in API 30+ in favor of
  // WindowCompat.setDecorFitsSystemWindows, but we need to support non-edge-to-edge apps.
  @Suppress("DEPRECATION", "detekt:ReturnCount")
  fun setTranslucent(translucent: Boolean) {
    if (BuildConfig.IS_EDGE_TO_EDGE_ENABLED && isEnabled()) {
      Logger.w(TAG, "StatusBarModule: Ignored status bar change, current activity is edge-to-edge.")
      return
    }

    val activity = mReactContext.currentActivity
    if (activity == null) {
      Logger.w(
        TAG,
        "StatusBarManagerCompatModule: Ignored status bar translucent change, current activity is null.",
      )
      return
    }

    UiThreadUtil.runOnUiThread {
      val edgeView = view()
      if (edgeView != null && isEnabled()) {
        edgeView.forceStatusBarTranslucent(translucent)
        return@runOnUiThread
      }

      val window = activity.window
      if (translucent) {
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
      } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
      }
    }
  }

  fun setStyle(style: String) {
    UiThreadUtil.runOnUiThread {
      getController()?.isAppearanceLightStatusBars = style == "dark-content"
    }
  }

  /**
   * Returns constants exposed to JavaScript via NativeModules.StatusBarManager.
   *
   * These constant names are defined by React Native's StatusBarModule API:
   * - HEIGHT: Status bar height in dp, used by StatusBar.currentHeight in JS
   * - DEFAULT_BACKGROUND_COLOR: Current status bar color as hex string (e.g., "#FF0000")
   *
   * We compute these directly from Android system resources instead of delegating
   * to RN's internal StatusBarModule, making this implementation version-agnostic.
   * @see https://github.com/facebook/react-native/blob/07d225557a7774bd2ac5ab8b20447477b2fbe603/packages/react-native/ReactAndroid/src/main/java/com/facebook/react/modules/statusbar/StatusBarModule.kt#L36
   */
  @Suppress("DEPRECATION")
  fun getConstants(): MutableMap<String, Any> {
    val constants = mutableMapOf<String, Any>()
    val resources = mReactContext.resources

    val heightResId = resources.getIdentifier("status_bar_height", "dimen", "android")
    val height = if (heightResId > 0) {
      PixelUtil.toDIPFromPixel(resources.getDimensionPixelSize(heightResId).toFloat())
    } else {
      0f
    }

    val statusBarColor = mReactContext.currentActivity?.window?.statusBarColor?.let { color ->
      String.format("#%06X", 0xFFFFFF and color)
    } ?: "black"

    constants["HEIGHT"] = height
    constants["DEFAULT_BACKGROUND_COLOR"] = statusBarColor
    return constants
  }

  private fun getController(): WindowInsetsControllerCompat? {
    val activity = mReactContext.currentActivity

    if (this.controller == null || activity != lastActivity.get()) {
      if (activity == null) {
        Logger.w(
          TAG,
          "StatusBarManagerCompatModule: can not get `WindowInsetsControllerCompat` because current activity is null.",
        )
        return this.controller
      }

      val window = activity.window
      lastActivity = WeakReference(activity)

      this.controller = WindowInsetsControllerCompat(window, window.decorView)
    }

    return this.controller
  }

  private fun isEnabled(): Boolean = view()?.active ?: false

  private fun view(): EdgeToEdgeReactViewGroup? = EdgeToEdgeViewRegistry.get()

  companion object {
    const val NAME = "StatusBarManager"
    private const val DEFAULT_ANIMATION_TIME = 300L
  }
}
