package com.quran.mobile.feature.memorize.presenter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import com.quran.mobile.feature.memorize.ui.MemorizeMicButton
import com.quran.mobile.feature.memorize.ui.MicButtonState
import kotlin.math.hypot

/**
 * The floating mic over the reading screen while a session exists (see [MemorizeMicButton]). It can
 * be dragged out of the way; where it was left is remembered.
 */
internal class FloatingMicButton(
  private val activity: Activity,
  private val preferences: MemorizePreferences,
  onToggle: () -> Unit,
  onChooseMode: () -> Unit,
  onEnd: () -> Unit,
  onReplay: () -> Unit,
) {
  private val state = mutableStateOf<MicButtonState?>(null)
  private val density = activity.resources.displayMetrics.density

  private val container = DraggableFrame(activity) { x, y ->
    preferences.micButtonOffset = x to y
  }.apply {
    isVisible = false
    addView(ComposeView(activity).apply {
      setContent {
        val current = state.value ?: return@setContent
        QuranTheme { MemorizeMicButton(current, onToggle, onChooseMode, onEnd, onReplay) }
      }
    })
  }

  fun attach() {
    if (container.parent != null) return
    val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
    root.addView(
      container,
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END,
      ).apply {
        marginEnd = (MARGIN_DP * density).toInt()
        bottomMargin = (BOTTOM_DP * density).toInt()
      },
    )
    // above the navigation bar and the audio bar
    ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      view.updateLayoutParams<FrameLayout.LayoutParams> { bottomMargin = bars.bottom + (BOTTOM_DP * density).toInt() }
      insets
    }
    ViewCompat.requestApplyInsets(container)
    preferences.micButtonOffset?.let { (x, y) ->
      container.translationX = x
      container.translationY = y
    }
    container.post { container.clampToParent() }
  }

  fun detach() {
    (container.parent as? ViewGroup)?.removeView(container)
  }

  fun show(state: MicButtonState?) {
    this.state.value = state
    container.isVisible = state != null
  }

  /** Moves with the finger once it has moved past the touch slop; taps reach the buttons. */
  @SuppressLint("ViewConstructor")
  private class DraggableFrame(context: Context, private val onMoved: (Float, Float) -> Unit) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
      track(event)
      return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
      track(event)
      if (dragging && event.actionMasked == MotionEvent.ACTION_MOVE) {
        translationX = startX + event.rawX - downX
        translationY = startY + event.rawY - downY
        clampToParent()
      }
      if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
        if (dragging) onMoved(translationX, translationY)
        dragging = false
      }
      return true
    }

    private fun track(event: MotionEvent) {
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downX = event.rawX
          downY = event.rawY
          startX = translationX
          startY = translationY
          dragging = false
        }
        MotionEvent.ACTION_MOVE ->
          if (!dragging && hypot(event.rawX - downX, event.rawY - downY) > slop) dragging = true
      }
    }

    /** Keeps the whole button on screen (e.g. after rotating). */
    fun clampToParent() {
      val parent = parent as? ViewGroup ?: return
      if (width == 0 || parent.width == 0) return
      translationX = translationX.coerceIn(-left.toFloat(), (parent.width - width - left).toFloat().coerceAtLeast(-left.toFloat()))
      translationY = translationY.coerceIn(-top.toFloat(), (parent.height - height - top).toFloat().coerceAtLeast(-top.toFloat()))
    }
  }

  private companion object {
    const val MARGIN_DP = 12
    const val BOTTOM_DP = 72
  }
}
