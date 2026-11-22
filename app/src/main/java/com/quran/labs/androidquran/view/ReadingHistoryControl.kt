package com.quran.labs.androidquran.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.feature.reading.ReadingHistoryDirection

class ReadingHistoryControl @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  var onDirectionSelected: ((ReadingHistoryDirection) -> Unit)? = null

  private lateinit var backButton: ImageButton
  private lateinit var forwardButton: ImageButton
  private var touchesEnabled = true
  private var chromeVisible = true
  private var backAvailable = false
  private var forwardAvailable = false

  override fun onFinishInflate() {
    super.onFinishInflate()
    backButton = findViewById(R.id.reading_history_back)
    forwardButton = findViewById(R.id.reading_history_forward)
    backButton.setOnClickListener {
      onDirectionSelected?.invoke(ReadingHistoryDirection.BACK)
    }
    forwardButton.setOnClickListener {
      onDirectionSelected?.invoke(ReadingHistoryDirection.FORWARD)
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    if (changed && !chromeVisible) {
      translationY = offscreenTranslation()
    }
  }

  fun render(
    hasHistory: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    nightPage: Boolean
  ) {
    isVisible = hasHistory
    if (!hasHistory) {
      return
    }
    val background = if (nightPage) NIGHT_BACKGROUND else DAY_BACKGROUND
    val tint = if (nightPage) Color.WHITE else DAY_TINT
    backAvailable = canGoBack
    forwardAvailable = canGoForward
    bind(backButton, canGoBack, background, tint)
    bind(forwardButton, canGoForward, background, tint)
  }

  fun setChromeVisible(visible: Boolean, animate: Boolean) {
    chromeVisible = visible
    if (!isVisible) {
      animate().cancel()
      alpha = 0f
      translationY = 0f
      setTouchesEnabled(false)
      return
    }
    val targetY = if (visible) 0f else offscreenTranslation()
    val targetAlpha = if (visible) 1f else 0f
    setTouchesEnabled(visible)
    if (!animate) {
      animate().cancel()
      translationY = targetY
      alpha = targetAlpha
      return
    }
    animate()
      .translationY(targetY)
      .alpha(targetAlpha)
      .setDuration(CHROME_ANIMATION_MS)
      .start()
  }

  fun setTouchesEnabled(enabled: Boolean) {
    touchesEnabled = enabled
    if (!::backButton.isInitialized) {
      return
    }
    backButton.isClickable = enabled && backAvailable
    forwardButton.isClickable = enabled && forwardAvailable
  }

  private fun bind(button: ImageButton, enabled: Boolean, background: Int, tint: Int) {
    val colorScale = if (enabled) 1f else DISABLED_ALPHA
    button.background = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(withScaledAlpha(background, colorScale))
    }
    button.outlineProvider = ViewOutlineProvider.BACKGROUND
    button.clipToOutline = false
    button.imageTintList = ColorStateList.valueOf(withScaledAlpha(tint, colorScale))
    button.alpha = 1f
    button.isEnabled = true
    button.isClickable = touchesEnabled && enabled
    button.invalidateOutline()
  }

  private fun offscreenTranslation(): Float {
    val margin = (layoutParams as? MarginLayoutParams)?.bottomMargin ?: 0
    return (height + margin).toFloat()
  }

  private fun withScaledAlpha(color: Int, scale: Float): Int {
    return Color.argb(
      (Color.alpha(color) * scale).toInt(),
      Color.red(color),
      Color.green(color),
      Color.blue(color)
    )
  }

  private companion object {
    const val DAY_BACKGROUND = 0xF2FFFFFF.toInt()
    const val NIGHT_BACKGROUND = 0xF22D2D2D.toInt()
    const val DAY_TINT = 0xFF44464E.toInt()
    const val DISABLED_ALPHA = 0.35f
    const val CHROME_ANIMATION_MS = 250L
  }
}
