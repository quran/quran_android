package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import android.view.View
import android.view.animation.Interpolator
import android.widget.Scroller
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

/**
 * Natural Mushaf book-leaf page-turn transformer and smooth ease-out scroller for [ViewPager].
 *
 * Hinges turning pages along their inner book spine with subtle 3D perspective (`rotationY`),
 * gentle depth scaling, and soft paper shading while guaranteeing a crisp, zero-transform
 * resting state at `position == 0f` so Arabic calligraphy remains razor-sharp.
 */
class MushafPageFlipTransformer : ViewPager.PageTransformer {

  override fun transformPage(page: View, position: Float) {
    val width = page.width.toFloat()
    val height = page.height.toFloat()

    when {
      position <= -1f || position >= 1f -> {
        resetPageProperties(page, width, height)
      }
      abs(position) < 0.001f -> {
        resetPageProperties(page, width, height)
      }
      position < 0f -> {
        // Left-side page hinging around its right spine edge
        val absPos = abs(position)
        page.cameraDistance = (width.coerceAtLeast(1f)) * CAMERA_DISTANCE_MULTIPLIER
        page.pivotX = width
        page.pivotY = height * 0.5f
        page.translationX = 0f
        page.rotationY = (-position * MAX_LEAF_ROTATION_DEG).coerceIn(-MAX_LEAF_ROTATION_DEG, MAX_LEAF_ROTATION_DEG)
        val scale = 1f - DEPTH_SCALE_DROP * absPos
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = (1f - PAPER_SHADE_DROP * absPos).coerceIn(MIN_LEAF_ALPHA, 1f)
        page.translationZ = (1f - absPos) * 2f
      }
      else -> {
        // Right-side page hinging around its left spine edge
        val absPos = abs(position)
        page.cameraDistance = (width.coerceAtLeast(1f)) * CAMERA_DISTANCE_MULTIPLIER
        page.pivotX = 0f
        page.pivotY = height * 0.5f
        page.translationX = 0f
        page.rotationY = (-position * MAX_LEAF_ROTATION_DEG).coerceIn(-MAX_LEAF_ROTATION_DEG, MAX_LEAF_ROTATION_DEG)
        val scale = 1f - DEPTH_SCALE_DROP * absPos
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = (1f - PAPER_SHADE_DROP * absPos).coerceIn(MIN_LEAF_ALPHA, 1f)
        page.translationZ = (1f - absPos) * 2f
      }
    }
  }

  private fun resetPageProperties(page: View, width: Float, height: Float) {
    page.pivotX = width * 0.5f
    page.pivotY = height * 0.5f
    page.translationX = 0f
    page.translationZ = 0f
    page.rotationY = 0f
    page.scaleX = 1f
    page.scaleY = 1f
    page.alpha = 1f
  }

  private class MushafSmoothScroller(
    context: Context,
    interpolator: Interpolator,
    private val flipDurationMs: Int
  ) : Scroller(context, interpolator, true) {

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
      val resolvedDuration = if (dx == 0 && dy == 0) {
        0
      } else {
        duration.coerceIn(MIN_FLIP_DURATION_MS, flipDurationMs)
      }
      super.startScroll(startX, startY, dx, dy, resolvedDuration)
    }

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int) {
      super.startScroll(startX, startY, dx, dy, flipDurationMs)
    }
  }

  companion object {
    private const val CAMERA_DISTANCE_MULTIPLIER = 20f
    private const val MAX_LEAF_ROTATION_DEG = 16f
    private const val DEPTH_SCALE_DROP = 0.018f
    private const val PAPER_SHADE_DROP = 0.12f
    private const val MIN_LEAF_ALPHA = 0.86f
    private const val DEFAULT_FLIP_DURATION_MS = 320
    private const val MIN_FLIP_DURATION_MS = 210

    private val QUINTIC_EASE_OUT = Interpolator { t ->
      val f = t - 1.0f
      f * f * f * f * f + 1.0f
    }

    @JvmStatic
    fun installSmoothFlipping(viewPager: ViewPager) {
      viewPager.setPageTransformer(false, MushafPageFlipTransformer())
      try {
        val scrollerField = ViewPager::class.java.getDeclaredField("mScroller")
        scrollerField.isAccessible = true
        scrollerField.set(
          viewPager,
          MushafSmoothScroller(viewPager.context, QUINTIC_EASE_OUT, DEFAULT_FLIP_DURATION_MS)
        )
      } catch (_: Throwable) {
        // Fallback gracefully to default ViewPager scroller if restricted
      }
    }
  }
}
