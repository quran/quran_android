package com.quran.labs.androidquran.hybrid.contract

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Finite, event-driven motion specs for Proposal 5.
 *
 * All specs use Compose's Choreographer-backed frame clock so animations render at the device's
 * native refresh rate (60 / 90 / 120+ Hz) and stop requesting frames as soon as they settle.
 * Never introduce infinite animation loops or manual Choreographer frame callbacks here.
 */
object AdaptiveMotionTokens {
  const val TAB_SWITCH_DURATION_MS: Int = 220
  const val QUICK_FADE_DURATION_MS: Int = 160
  const val PRESS_DOWN_DURATION_MS: Long = 85L
  const val SPRING_SETTLE_DURATION_MS: Long = 240L
  const val PRESS_SCALE: Float = 0.975f
  const val ICON_PRESS_SCALE: Float = 0.96f
  const val DEFAULT_REFRESH_RATE_HZ: Float = 60f

  val SPRING_CRITICALLY_DAMPED: PathInterpolator = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
  val APPLE_PRESS_EASE: PathInterpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f)

  val TabSwitchFloatSpec: FiniteAnimationSpec<Float> = tween(
    durationMillis = TAB_SWITCH_DURATION_MS,
    easing = FastOutSlowInEasing
  )

  val TabSwitchColorSpec: FiniteAnimationSpec<Color> = tween(
    durationMillis = TAB_SWITCH_DURATION_MS,
    easing = FastOutSlowInEasing
  )

  val QuickFadeFloatSpec: FiniteAnimationSpec<Float> = tween(
    durationMillis = QUICK_FADE_DURATION_MS,
    easing = LinearOutSlowInEasing
  )

  fun <T> panelSpring(): SpringSpec<T> = spring(
    stiffness = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioNoBouncy
  )

  fun <T> drawerSheetSpring(): SpringSpec<T> = spring(
    stiffness = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioNoBouncy
  )

  @Suppress("DEPRECATION")
  fun displayRefreshRateHz(context: Context): Float {
    val displayRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val visualDisplay = runCatching { context.display }.getOrNull()
      visualDisplay?.refreshRate
        ?: (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
          ?.getDisplay(Display.DEFAULT_DISPLAY)
          ?.refreshRate
    } else {
      (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
        ?.defaultDisplay
        ?.refreshRate
    }
    return displayRate?.takeIf { it >= 59f } ?: DEFAULT_REFRESH_RATE_HZ
  }

  @Volatile
  private var cachedAnimationsEnabled: Boolean? = null
  private val observerRegistered = AtomicBoolean(false)

  fun areAnimationsEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) {
      return false
    }
    cachedAnimationsEnabled?.let { return it }
    val appCtx = context.applicationContext ?: context
    val enabled = readAnimatorScaleEnabled(appCtx)
    cachedAnimationsEnabled = enabled
    if (observerRegistered.compareAndSet(false, true)) {
      runCatching {
        appCtx.contentResolver.registerContentObserver(
          Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
          false,
          object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
              cachedAnimationsEnabled = readAnimatorScaleEnabled(appCtx)
            }
          }
        )
      }
    }
    return enabled
  }

  private fun readAnimatorScaleEnabled(context: Context): Boolean {
    return runCatching {
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
      ) > 0f
    }.getOrDefault(true)
  }
}
