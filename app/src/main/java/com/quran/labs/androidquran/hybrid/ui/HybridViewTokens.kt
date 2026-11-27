package com.quran.labs.androidquran.hybrid.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.LruCache
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import kotlin.math.abs
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import androidx.core.view.doOnPreDraw
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.AppThemePalette
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.BitmapPoolTrimRegistry
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.search.HighlightSpanRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Lazy cached native Android [Typeface] for Uthmani script rendering (`UthmanTN1Ver10.otf`).
 * Loaded post-first-frame (`decorView.post {}`) or on first Search card bind — NEVER in
 * `QuranApplication.onCreate()`.
 */
object NativeUthmaniTypefaceCache {
  @Volatile
  private var cached: Typeface? = null

  fun get(context: Context): Typeface? {
    cached?.let { return it }
    return synchronized(this) {
      cached ?: runCatching {
        Typeface.createFromAsset(context.applicationContext.assets, "UthmanTN1Ver10.otf")
      }.getOrNull()?.also { cached = it }
    }
  }
}

/**
 * Builds highlighted Spannable using ONLY [ForegroundColorSpan] and [BackgroundColorSpan]
 * (NEVER [android.text.style.StyleSpan] or [android.text.style.TypefaceSpan], which break
 * Uthmani OpenType GSUB ligatures such as "الله").
 */
fun buildHybridHighlightedSpan(
  text: String,
  ranges: List<HighlightSpanRange>,
  @ColorInt highlightColor: Int,
  @ColorInt highlightBackgroundColor: Int
): CharSequence {
  if (ranges.isEmpty() || text.isEmpty()) return text
  val sb = SpannableStringBuilder(text)
  val len = text.length
  for (range in ranges) {
    val start = range.start.coerceIn(0, len)
    val end = range.end.coerceIn(start, len)
    if (start < end) {
      sb.setSpan(
        ForegroundColorSpan(highlightColor),
        start,
        end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
      )
      sb.setSpan(
        BackgroundColorSpan(highlightBackgroundColor),
        start,
        end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
      )
    }
  }
  return sb
}

/**
 * Native View design tokens resolved directly from [AppThemeStyle] palettes so
 * light/dark appearance and all 5 color palettes match Proposal 5 1:1 without
 * mounting Compose or Material3.
 */
data class HybridViewPalette(
  val style: AppThemeStyle,
  val isDark: Boolean,
  @ColorInt val surface: Int,
  @ColorInt val surfaceElevated: Int,
  @ColorInt val cardSurface: Int,
  @ColorInt val surfaceVariant: Int,
  @ColorInt val headerBackground: Int,
  @ColorInt val onHeader: Int,
  @ColorInt val toolbarBackground: Int,
  @ColorInt val onToolbar: Int,
  @ColorInt val onToolbarSecondary: Int,
  @ColorInt val accent: Int,
  @ColorInt val onAccent: Int,
  @ColorInt val textPrimary: Int,
  @ColorInt val textSecondary: Int,
  @ColorInt val divider: Int,
  @ColorInt val searchHighlight: Int,
  @ColorInt val searchHighlightBg: Int,
  val isLightToolbar: Boolean,
  @ColorInt val glassBg: Int,
  @ColorInt val glassBorder: Int,
  @ColorInt val mushafOverlayBg: Int,
  @ColorInt val navBg: Int,
  @ColorInt val navBorder: Int,
  @ColorInt val ctaBackground: Int,
  @ColorInt val onCta: Int,
  @ColorInt val ayahHighlightFill: Int,
  @ColorInt val accentSoftBg: Int,
  @ColorInt val medallionFill: Int,
  @ColorInt val medallionBorder: Int,
  @ColorInt val scrim: Int,
  @ColorInt val ripple: Int,
  @ColorInt val editorialAccent: Int,
  @ColorInt val editorialSoftBg: Int,
  @ColorInt val editorialHighlightBg: Int,
  @ColorInt val surfaceTopGlow: Int,
  @ColorInt val glassFloatBg: Int,
  @ColorInt val glassRimHighlight: Int,
  @ColorInt val shadowTint: Int,
  @ColorInt val cardHairlineBorder: Int,
  @ColorInt val floatHairlineBorder: Int,
  val isOled: Boolean = false
) {
  companion object {
    fun from(
      style: AppThemeStyle,
      isDark: Boolean,
      oled: Boolean = AppThemeStyle.isOledDarkMode
    ): HybridViewPalette {
      val effectiveOled = isDark && oled
      val p: AppThemePalette = style.palette(isDark = isDark, oled = effectiveOled)
      val accent = p.accent.toArgb()
      val editorialAccent = p.editorialAccent.toArgb()
      val editorialSoftBg = ColorUtils.setAlphaComponent(editorialAccent, if (isDark) 38 else 33)
      val editorialHighlightBg = ColorUtils.setAlphaComponent(editorialAccent, if (isDark) 51 else 41)
      val searchHighlight = p.searchHighlight.toArgb()
      return HybridViewPalette(
        style = style,
        isDark = isDark,
        surface = p.surface.toArgb(),
        surfaceElevated = p.surfaceElevated.toArgb(),
        cardSurface = p.cardSurface.toArgb(),
        surfaceVariant = p.surfaceVariant.toArgb(),
        headerBackground = p.headerBackground.toArgb(),
        onHeader = p.onHeader.toArgb(),
        toolbarBackground = p.toolbarBackground.toArgb(),
        onToolbar = p.onToolbar.toArgb(),
        onToolbarSecondary = p.onToolbarSecondary.toArgb(),
        accent = accent,
        onAccent = p.onAccent.toArgb(),
        textPrimary = p.textPrimary.toArgb(),
        textSecondary = p.textSecondary.toArgb(),
        divider = p.divider.toArgb(),
        searchHighlight = searchHighlight,
        searchHighlightBg = editorialHighlightBg,
        isLightToolbar = p.isLightToolbar,
        glassBg = p.glassBg.toArgb(),
        glassBorder = p.glassBorder.toArgb(),
        mushafOverlayBg = p.mushafOverlayBg.toArgb(),
        navBg = p.navBg.toArgb(),
        navBorder = p.navBorder.toArgb(),
        ctaBackground = p.ctaBackground.toArgb(),
        onCta = p.onCta.toArgb(),
        ayahHighlightFill = p.ayahHighlightFill.toArgb(),
        accentSoftBg = ColorUtils.setAlphaComponent(accent, if (isDark) 36 else 28),
        medallionFill = editorialSoftBg,
        medallionBorder = editorialAccent,
        scrim = Color.argb(if (isDark) 145 else 92, 0, 0, 0),
        ripple = ColorUtils.setAlphaComponent(accent, if (isDark) 28 else 20),
        editorialAccent = editorialAccent,
        editorialSoftBg = editorialSoftBg,
        editorialHighlightBg = editorialHighlightBg,
        surfaceTopGlow = p.surfaceTopGlow.toArgb(),
        glassFloatBg = p.glassFloatBg.toArgb(),
        glassRimHighlight = Color.argb(if (isDark) 36 else 203, 255, 255, 255),
        shadowTint = p.shadowTint.toArgb(),
        cardHairlineBorder = when {
          effectiveOled -> 0x1FFFFFFF
          isDark -> Color.argb(23, 255, 255, 255)
          else -> Color.argb(13, 0, 0, 0)
        },
        floatHairlineBorder = when {
          effectiveOled -> 0x28FFFFFF
          isDark -> Color.argb(33, 255, 255, 255)
          else -> Color.argb(224, 255, 255, 255)
        },
        isOled = effectiveOled
      )
    }
  }
}

object HybridViewTokens {
  fun dp(context: Context, value: Float): Int {
    return TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      value,
      context.resources.displayMetrics
    ).roundToInt()
  }

  fun dpF(context: Context, value: Float): Float {
    return TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      value,
      context.resources.displayMetrics
    )
  }

  fun roundedDrawable(
    @ColorInt fillColor: Int,
    @ColorInt strokeColor: Int = Color.TRANSPARENT,
    strokeWidthPx: Int = 0,
    radiusPx: Float
  ): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      setColor(fillColor)
      cornerRadius = radiusPx
      if (strokeWidthPx > 0 && strokeColor != Color.TRANSPARENT) {
        setStroke(strokeWidthPx, strokeColor)
      }
    }
  }

  fun topRoundedDrawable(
    @ColorInt fillColor: Int,
    @ColorInt strokeColor: Int = Color.TRANSPARENT,
    strokeWidthPx: Int = 0,
    topRadiusPx: Float
  ): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      setColor(fillColor)
      cornerRadii = floatArrayOf(
        topRadiusPx, topRadiusPx,
        topRadiusPx, topRadiusPx,
        0f, 0f,
        0f, 0f
      )
      if (strokeWidthPx > 0 && strokeColor != Color.TRANSPARENT) {
        setStroke(strokeWidthPx, strokeColor)
      }
    }
  }

  /**
   * Static top-shadow + 1dp hairline + top-rounded sheet background replacing dynamic
   * `elevation` hardware shadow casting on sliding sheets (zero RenderThread shadow pass).
   */
  fun topShadowSheetDrawable(
    @ColorInt fillColor: Int,
    @ColorInt strokeColor: Int,
    strokeWidthPx: Int,
    topRadiusPx: Float,
    shadowHeightPx: Int,
    isDark: Boolean
  ): LayerDrawable {
    val shadowEndColor = Color.argb(if (isDark) 0x38 else 0x16, 0, 0, 0)
    val shadowLayer = GradientDrawable(
      GradientDrawable.Orientation.TOP_BOTTOM,
      intArrayOf(Color.TRANSPARENT, shadowEndColor)
    ).apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadii = floatArrayOf(
        topRadiusPx, topRadiusPx,
        topRadiusPx, topRadiusPx,
        0f, 0f,
        0f, 0f
      )
    }
    val sheetLayer = topRoundedDrawable(
      fillColor = fillColor,
      strokeColor = strokeColor,
      strokeWidthPx = strokeWidthPx,
      topRadiusPx = topRadiusPx
    )
    return LayerDrawable(arrayOf(shadowLayer, sheetLayer)).apply {
      setLayerInset(1, 0, shadowHeightPx, 0, 0)
    }
  }

  fun circleDrawable(
    @ColorInt fillColor: Int,
    @ColorInt strokeColor: Int = Color.TRANSPARENT,
    strokeWidthPx: Int = 0
  ): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(fillColor)
      if (strokeWidthPx > 0 && strokeColor != Color.TRANSPARENT) {
        setStroke(strokeWidthPx, strokeColor)
      }
    }
  }

  fun interactiveRoundedDrawable(
    @ColorInt fillColor: Int,
    @ColorInt strokeColor: Int = Color.TRANSPARENT,
    strokeWidthPx: Int = 0,
    radiusPx: Float,
    @ColorInt rippleColor: Int
  ): Drawable {
    val content = roundedDrawable(fillColor, strokeColor, strokeWidthPx, radiusPx)
    val mask = roundedDrawable(Color.WHITE, Color.TRANSPARENT, 0, radiusPx)
    return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
  }

  fun interactiveCircleDrawable(
    @ColorInt fillColor: Int,
    @ColorInt strokeColor: Int = Color.TRANSPARENT,
    strokeWidthPx: Int = 0,
    @ColorInt rippleColor: Int
  ): Drawable {
    val content = circleDrawable(fillColor, strokeColor, strokeWidthPx)
    val mask = circleDrawable(Color.WHITE)
    return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
  }

  class LightweightPressColorDrawable internal constructor(
    @ColorInt private var fillColor: Int = Color.TRANSPARENT,
    @ColorInt private var strokeColor: Int = Color.TRANSPARENT,
    private var strokeWidthPx: Int = 0,
    private val cornerRadiusPx: Float = 0f,
    private val isOval: Boolean = false,
    @ColorInt private var pressColor: Int = Color.TRANSPARENT
  ) : StateListDrawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    private val strokeRectF = RectF()
    private var isPressedState: Boolean = false
    private var alphaByte: Int = 255

    override fun isStateful(): Boolean = true

    override fun onStateChange(stateSet: IntArray): Boolean {
      var pressed = false
      for (state in stateSet) {
        if (state == android.R.attr.state_pressed) {
          pressed = true
          break
        }
      }
      if (pressed != isPressedState) {
        isPressedState = pressed
        invalidateSelf()
        return true
      }
      return false
    }

    fun setFillColor(@ColorInt color: Int) {
      if (fillColor != color) {
        fillColor = color
        invalidateSelf()
      }
    }

    fun setStroke(widthPx: Int, @ColorInt color: Int) {
      if (strokeWidthPx != widthPx || strokeColor != color) {
        strokeWidthPx = widthPx
        strokeColor = color
        invalidateSelf()
      }
    }

    fun setPressColor(@ColorInt color: Int) {
      if (pressColor != color) {
        pressColor = color
        if (isPressedState) {
          invalidateSelf()
        }
      }
    }

    override fun draw(canvas: Canvas) {
      val b = bounds
      if (b.isEmpty) return
      rectF.set(b)
      val minHalf = minOf(rectF.width(), rectF.height()) * 0.5f
      val radius = if (!isOval && cornerRadiusPx > 0f) minOf(cornerRadiusPx, minHalf) else 0f

      if (Color.alpha(fillColor) > 0) {
        paint.style = Paint.Style.FILL
        paint.color = applyAlpha(fillColor)
        drawShape(canvas, rectF, radius)
      }

      if (isPressedState && Color.alpha(pressColor) > 0) {
        paint.style = Paint.Style.FILL
        paint.color = applyAlpha(pressColor)
        drawShape(canvas, rectF, radius)
      }

      if (strokeWidthPx > 0 && Color.alpha(strokeColor) > 0) {
        val halfStroke = strokeWidthPx * 0.5f
        strokeRectF.set(
          rectF.left + halfStroke,
          rectF.top + halfStroke,
          rectF.right - halfStroke,
          rectF.bottom - halfStroke
        )
        if (strokeRectF.width() > 0f && strokeRectF.height() > 0f) {
          paint.style = Paint.Style.STROKE
          paint.strokeWidth = strokeWidthPx.toFloat()
          paint.color = applyAlpha(strokeColor)
          val strokeRadius = if (!isOval && radius > 0f) (radius - halfStroke).coerceAtLeast(0f) else 0f
          drawShape(canvas, strokeRectF, strokeRadius)
        }
      }
    }

    private fun drawShape(canvas: Canvas, target: RectF, radius: Float) {
      if (isOval) {
        canvas.drawOval(target, paint)
      } else if (radius > 0f) {
        canvas.drawRoundRect(target, radius, radius, paint)
      } else {
        canvas.drawRect(target, paint)
      }
    }

    private fun applyAlpha(@ColorInt color: Int): Int {
      if (alphaByte >= 255) return color
      val a = (Color.alpha(color) * alphaByte + 127) / 255
      return (color and 0x00FFFFFF) or (a shl 24)
    }

    override fun setAlpha(alpha: Int) {
      val clamped = alpha.coerceIn(0, 255)
      if (alphaByte != clamped) {
        alphaByte = clamped
        invalidateSelf()
      }
    }

    override fun getAlpha(): Int = alphaByte

    override fun setColorFilter(colorFilter: ColorFilter?) {
      if (paint.colorFilter != colorFilter) {
        paint.colorFilter = colorFilter
        invalidateSelf()
      }
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getOutline(outline: Outline) {
      val b = bounds
      if (b.isEmpty) {
        super.getOutline(outline)
        return
      }
      if (isOval) {
        outline.setOval(b)
      } else if (cornerRadiusPx > 0f) {
        val r = minOf(cornerRadiusPx, minOf(b.width(), b.height()) * 0.5f)
        outline.setRoundRect(b, r)
      } else {
        outline.setRect(b)
      }
      val fillAlpha = (Color.alpha(fillColor) * alphaByte + 127) / 255
      outline.alpha = fillAlpha / 255f
    }
  }

  fun lightweightPressRoundedDrawable(
    @ColorInt fillColor: Int = Color.TRANSPARENT,
    @ColorInt strokeColor: Int = Color.TRANSPARENT,
    strokeWidthPx: Int = 0,
    radiusPx: Float = 0f,
    @ColorInt pressColor: Int = Color.TRANSPARENT
  ): LightweightPressColorDrawable {
    return LightweightPressColorDrawable(
      fillColor = fillColor,
      strokeColor = strokeColor,
      strokeWidthPx = strokeWidthPx,
      cornerRadiusPx = radiusPx,
      isOval = false,
      pressColor = pressColor
    )
  }

  fun lightweightPressCircleDrawable(
    @ColorInt fillColor: Int = Color.TRANSPARENT,
    @ColorInt strokeColor: Int = Color.TRANSPARENT,
    strokeWidthPx: Int = 0,
    @ColorInt pressColor: Int = Color.TRANSPARENT
  ): LightweightPressColorDrawable {
    return LightweightPressColorDrawable(
      fillColor = fillColor,
      strokeColor = strokeColor,
      strokeWidthPx = strokeWidthPx,
      cornerRadiusPx = 0f,
      isOval = true,
      pressColor = pressColor
    )
  }

  fun lightweightPressDrawable(@ColorInt pressColor: Int): LightweightPressColorDrawable {
    return LightweightPressColorDrawable(pressColor = pressColor)
  }

  fun softShadowAmbientAlpha(isDark: Boolean): Int = if (isDark) 0x38 else 0x12

  fun softShadowSpotAlpha(isDark: Boolean): Int = if (isDark) 0x52 else 0x1E

  fun applySoftElevationShadow(
    view: View,
    elevationDp: Float,
    isDark: Boolean,
    ambientAlpha: Int = softShadowAmbientAlpha(isDark),
    spotAlpha: Int = softShadowSpotAlpha(isDark)
  ) {
    view.elevation = dpF(view.context, elevationDp)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      view.outlineAmbientShadowColor = Color.argb(ambientAlpha, 0, 0, 0)
      view.outlineSpotShadowColor = Color.argb(spotAlpha, 0, 0, 0)
    }
  }

  @ColorInt
  fun luminousHairlineColor(palette: HybridViewPalette, @ColorInt fallbackStroke: Int): Int =
    if (palette.isDark) 0x1FFFFFFF else fallbackStroke

  fun applyTopRoundedOutlineProvider(view: View, radiusDp: Float) {
    val radiusPx = dpF(view.context, radiusDp)
    view.outlineProvider = object : ViewOutlineProvider() {
      override fun getOutline(target: View, outline: Outline) {
        if (target.width <= 0 || target.height <= 0) return
        outline.setRoundRect(0, 0, target.width, (target.height + radiusPx).toInt(), radiusPx)
      }
    }
  }

  /**
   * Static top-edge rounded rim stroke fading softly along the upper rounded shoulders.
   * Zero interior fill (safe over translucent glass), zero offscreen FBO, zero per-frame allocations.
   */
  private class TopRimDrawable(
    @ColorInt private val rimColor: Int,
    private val radiusPx: Float,
    private val strokeWidthPx: Int = 1,
    private val insetPx: Int = 0
  ) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = strokeWidthPx.toFloat()
      color = rimColor
    }
    private val strokeRect = RectF()
    private var alphaByte = 255
    private var lastHeight = -1

    override fun onBoundsChange(bounds: Rect) {
      super.onBoundsChange(bounds)
      val h = bounds.height()
      if (h > 0 && h != lastHeight) {
        lastHeight = h
        rebuildShader(bounds)
      }
    }

    private fun rebuildShader(b: Rect) {
      val inset = insetPx + strokeWidthPx * 0.5f
      val top = b.top + inset
      val clampedR = minOf(radiusPx, minOf(b.width(), b.height()) * 0.5f)
      val fadeDistance = minOf(
        maxOf(clampedR * 0.55f, strokeWidthPx * 3f),
        b.height() * 0.42f
      ).coerceAtLeast(1f)
      val fadeEnd = ColorUtils.setAlphaComponent(rimColor, 0)
      paint.shader = LinearGradient(
        0f,
        top,
        0f,
        top + fadeDistance,
        rimColor,
        fadeEnd,
        Shader.TileMode.CLAMP
      )
    }

    override fun draw(canvas: Canvas) {
      val b = bounds
      if (b.isEmpty || Color.alpha(rimColor) == 0) return
      if (paint.shader == null) {
        rebuildShader(b)
      }
      val inset = insetPx + strokeWidthPx * 0.5f
      strokeRect.set(
        b.left + inset,
        b.top + inset,
        b.right - inset,
        b.bottom - inset
      )
      if (strokeRect.width() <= 0f || strokeRect.height() <= 0f) return
      val r = (minOf(radiusPx, minOf(b.width(), b.height()) * 0.5f) - inset).coerceAtLeast(0f)
      if (r > 0f) {
        canvas.drawRoundRect(strokeRect, r, r, paint)
      } else {
        canvas.drawLine(strokeRect.left, strokeRect.top, strokeRect.right, strokeRect.top, paint)
      }
    }

    override fun setAlpha(alpha: Int) {
      val clamped = alpha.coerceIn(0, 255)
      if (alphaByte != clamped) {
        alphaByte = clamped
        paint.alpha = clamped
        invalidateSelf()
      }
    }

    override fun getAlpha(): Int = alphaByte

    override fun setColorFilter(colorFilter: ColorFilter?) {
      paint.colorFilter = colorFilter
      invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
  }

  /**
   * Static vertical [GradientDrawable] for Z0 canvas ambient top glow (0 FBO, 0 idle frames).
   */
  fun canvasAmbientGlowDrawable(
    @ColorInt surface: Int,
    @ColorInt surfaceTopGlow: Int
  ): GradientDrawable {
    val midGlow = ColorUtils.blendARGB(surfaceTopGlow, surface, 0.62f)
    return GradientDrawable(
      GradientDrawable.Orientation.TOP_BOTTOM,
      intArrayOf(surfaceTopGlow, midGlow, surface, surface)
    ).apply {
      shape = GradientDrawable.RECTANGLE
    }
  }

  /**
   * Static bottom-to-top [GradientDrawable] (`surface` 100% -> 100% -> 80% -> 0% alpha) so the
   * lower band behind the floating bottom navigation dock is fully opaque `surface` and scrolled
   * cards dissolve smoothly before reaching the dock (0 FBO, 0 idle frames).
   */
  fun scrollUnderVeilDrawable(@ColorInt surface: Int): GradientDrawable {
    val solid = ColorUtils.setAlphaComponent(surface, 255)
    val upperFade = ColorUtils.setAlphaComponent(surface, 0xCC)
    val clear = ColorUtils.setAlphaComponent(surface, 0)
    return GradientDrawable(
      GradientDrawable.Orientation.BOTTOM_TOP,
      intArrayOf(solid, solid, upperFade, clear)
    ).apply {
      shape = GradientDrawable.RECTANGLE
    }
  }

  /**
   * Static [LayerDrawable] for Z1-Z4 floating surfaces with a crisp 1px top specular glass rim
   * and hairline border (zero GPU blur, zero offscreen FBO, zero idle frames).
   */
  fun floatingSurfaceDrawable(
    @ColorInt fillColor: Int,
    @ColorInt borderColor: Int,
    @ColorInt specularTopColor: Int,
    strokeWidthPx: Int,
    radiusPx: Float,
    @ColorInt topGradientColor: Int? = null
  ): LayerDrawable {
    val baseLayer = if (topGradientColor != null) {
      GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(topGradientColor, fillColor)
      ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
      }
    } else {
      roundedDrawable(fillColor = fillColor, radiusPx = radiusPx)
    }
    val specularLayer = TopRimDrawable(
      rimColor = specularTopColor,
      radiusPx = radiusPx,
      strokeWidthPx = maxOf(1, strokeWidthPx),
      insetPx = strokeWidthPx
    )
    val borderLayer = roundedDrawable(
      fillColor = Color.TRANSPARENT,
      strokeColor = borderColor,
      strokeWidthPx = strokeWidthPx,
      radiusPx = radiusPx
    )
    return LayerDrawable(arrayOf(baseLayer, specularLayer, borderLayer))
  }

  /**
   * Recessed segmented control track with subtle top inner-shadow hairline and outer rim.
   */
  fun recessedTrackDrawable(
    @ColorInt trackColor: Int,
    @ColorInt innerShadowColor: Int,
    @ColorInt borderColor: Int,
    radiusPx: Float
  ): LayerDrawable {
    val baseLayer = roundedDrawable(fillColor = trackColor, radiusPx = radiusPx)
    val innerShadowLayer = TopRimDrawable(
      rimColor = innerShadowColor,
      radiusPx = radiusPx,
      strokeWidthPx = 2,
      insetPx = 1
    )
    val borderLayer = roundedDrawable(
      fillColor = Color.TRANSPARENT,
      strokeColor = borderColor,
      strokeWidthPx = 1,
      radiusPx = radiusPx
    )
    return LayerDrawable(arrayOf(baseLayer, innerShadowLayer, borderLayer))
  }

  /**
   * Calibrated Balanced (70%) Z0-Z4 hardware elevation ladder in dp.
   */
  fun zLevelElevationDp(zLevel: Int, isDark: Boolean): Float = when {
    zLevel <= 0 -> 0f
    zLevel == 1 -> if (isDark) 2.5f else 2f
    zLevel == 2 -> if (isDark) 7f else 6f
    zLevel == 3 -> if (isDark) 12f else 10f
    else -> if (isDark) 18f else 16f
  }

  /**
   * Calibrated Balanced (70%) hue-tinted ambient shadow ARGB for API 28+ `outlineAmbientShadowColor`.
   */
  @ColorInt
  fun zLevelAmbientShadowColor(
    zLevel: Int,
    isDark: Boolean,
    @ColorInt shadowTintColor: Int
  ): Int {
    val alpha = when {
      zLevel <= 0 -> 0x00
      zLevel == 1 -> if (isDark) 0x28 else 0x10
      zLevel == 2 -> if (isDark) 0x38 else 0x18
      zLevel == 3 -> if (isDark) 0x4A else 0x22
      else -> if (isDark) 0x5C else 0x2C
    }
    return (alpha shl 24) or (shadowTintColor and 0x00FFFFFF)
  }

  /**
   * Calibrated Balanced (70%) hue-tinted spot shadow ARGB for API 28+ `outlineSpotShadowColor`.
   */
  @ColorInt
  fun zLevelSpotShadowColor(
    zLevel: Int,
    isDark: Boolean,
    @ColorInt shadowTintColor: Int
  ): Int {
    val alpha = when {
      zLevel <= 0 -> 0x00
      zLevel == 1 -> if (isDark) 0x3E else 0x1A
      zLevel == 2 -> if (isDark) 0x58 else 0x2C
      zLevel == 3 -> if (isDark) 0x72 else 0x3C
      else -> if (isDark) 0x8A else 0x4E
    }
    return (alpha shl 24) or (shadowTintColor and 0x00FFFFFF)
  }

  /**
   * Applies calibrated Balanced (70%) Z1-Z4 hardware elevation, API 28+ hue-tinted ambient/spot
   * shadows, and an opaque rounded-rect [ViewOutlineProvider] when [radiusPx] >= 0f so translucent
   * glass surfaces cast clean shadows without GPU blur or extra views.
   */
  fun applyZLayerFloatation(
    view: View,
    zLevel: Int,
    isDark: Boolean,
    @ColorInt shadowTintColor: Int,
    radiusPx: Float = -1f
  ) {
    view.elevation = dpF(view.context, zLevelElevationDp(zLevel, isDark))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      view.outlineAmbientShadowColor = zLevelAmbientShadowColor(zLevel, isDark, shadowTintColor)
      view.outlineSpotShadowColor = zLevelSpotShadowColor(zLevel, isDark, shadowTintColor)
    }
    if (radiusPx >= 0f) {
      view.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(target: View, outline: Outline) {
          val w = target.width
          val h = target.height
          if (w <= 0 || h <= 0) return
          val clamped = minOf(radiusPx, minOf(w, h) * 0.5f)
          outline.setRoundRect(0, 0, w, h, clamped)
          outline.alpha = 1f
        }
      }
    }
  }

  internal const val APPLE_PRESS_DELAY_MS = 48L

  fun attachApplePressFeedback(
    view: View,
    scaleDown: Float = AdaptiveMotionTokens.PRESS_SCALE
  ) {
    if (view.getTag(R.id.hybrid_press_feedback_attached) == true) return
    view.setTag(R.id.hybrid_press_feedback_attached, true)
    val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    var downX = 0f
    var downY = 0f
    view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(v: View) = Unit
      override fun onViewDetachedFromWindow(v: View) {
        resetApplePressFeedback(v)
      }
    })
    view.setOnTouchListener { v, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          cancelPendingApplePress(v)
          if (v.isAttachedToWindow && AdaptiveMotionTokens.areAnimationsEnabled(v.context)) {
            downX = event.x
            downY = event.y
            lateinit var pressRunnable: Runnable
            pressRunnable = Runnable {
              if (v.getTag(R.id.hybrid_press_pending_runnable) === pressRunnable) {
                v.setTag(R.id.hybrid_press_pending_runnable, null)
                if (v.isAttachedToWindow && AdaptiveMotionTokens.areAnimationsEnabled(v.context)) {
                  v.animate().cancel()
                  v.setTag(R.id.hybrid_press_anim_active, true)
                  v.animate()
                    .scaleX(scaleDown)
                    .scaleY(scaleDown)
                    .setDuration(AdaptiveMotionTokens.PRESS_DOWN_DURATION_MS)
                    .setInterpolator(AdaptiveMotionTokens.APPLE_PRESS_EASE)
                    .start()
                }
              }
            }
            v.setTag(R.id.hybrid_press_pending_runnable, pressRunnable)
            v.postDelayed(pressRunnable, APPLE_PRESS_DELAY_MS)
          }
        }
        MotionEvent.ACTION_MOVE -> {
          if (v.getTag(R.id.hybrid_press_pending_runnable) != null &&
            (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)
          ) {
            cancelPendingApplePress(v)
          }
        }
        MotionEvent.ACTION_CANCEL -> {
          cancelPendingApplePress(v)
          val hasActiveAnim = v.getTag(R.id.hybrid_press_anim_active) == true
          if (hasActiveAnim || v.scaleX != 1f || v.scaleY != 1f) {
            v.animate().cancel()
            v.scaleX = 1f
            v.scaleY = 1f
            v.setTag(R.id.hybrid_press_anim_active, false)
          }
        }
        MotionEvent.ACTION_UP -> {
          cancelPendingApplePress(v)
          val hasActiveAnim = v.getTag(R.id.hybrid_press_anim_active) == true
          if (hasActiveAnim || v.scaleX != 1f || v.scaleY != 1f) {
            v.animate().cancel()
            if (v.isAttachedToWindow && AdaptiveMotionTokens.areAnimationsEnabled(v.context)) {
              v.setTag(R.id.hybrid_press_anim_active, true)
              v.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(AdaptiveMotionTokens.SPRING_SETTLE_DURATION_MS)
                .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
                .withEndAction {
                  v.setTag(R.id.hybrid_press_anim_active, false)
                }
                .start()
            } else {
              v.setTag(R.id.hybrid_press_anim_active, false)
              v.scaleX = 1f
              v.scaleY = 1f
            }
          }
        }
      }
      false
    }
  }

  private fun cancelPendingApplePress(view: View) {
    val pending = view.getTag(R.id.hybrid_press_pending_runnable) as? Runnable ?: return
    view.removeCallbacks(pending)
    view.setTag(R.id.hybrid_press_pending_runnable, null)
  }

  fun resetApplePressFeedback(view: View) {
    cancelPendingApplePress(view)
    val hasActiveAnim = view.getTag(R.id.hybrid_press_anim_active) == true
    if (hasActiveAnim || view.scaleX != 1f || view.scaleY != 1f) {
      view.animate().cancel()
      view.scaleX = 1f
      view.scaleY = 1f
      view.setTag(R.id.hybrid_press_anim_active, false)
    }
  }

  fun animateVisibilityTransition(
    view: View,
    shouldBeVisible: Boolean,
    translateYDp: Float = 0f,
    durationMs: Long = AdaptiveMotionTokens.SPRING_SETTLE_DURATION_MS,
    hiddenVisibility: Int = View.GONE,
    useHardwareLayer: Boolean = false,
    fade: Boolean = true,
    onEnd: (() -> Unit)? = null
  ) {
    val prevTarget = view.getTag(R.id.hybrid_anim_target_visible) as? Boolean
    val isCurrentlyVisible = view.visibility == View.VISIBLE
    if (prevTarget == shouldBeVisible && isCurrentlyVisible == shouldBeVisible) return
    view.setTag(R.id.hybrid_anim_target_visible, shouldBeVisible)
    val animGen = ((view.getTag(R.id.hybrid_anim_generation) as? Int) ?: 0) + 1
    view.setTag(R.id.hybrid_anim_generation, animGen)

    val animationsEnabled = AdaptiveMotionTokens.areAnimationsEnabled(view.context)
    val offsetPx = if (translateYDp != 0f) dpF(view.context, translateYDp) else 0f

    view.animate().setListener(null)
    view.animate().cancel()
    if (shouldBeVisible) {
      val wasHidden = !isCurrentlyVisible
      view.visibility = View.VISIBLE
      if (wasHidden && view.isAttachedToWindow && animationsEnabled) {
        view.alpha = if (fade) 0f else 1f
        view.translationY = offsetPx
        val startSlide = {
          if (view.getTag(R.id.hybrid_anim_generation) == animGen &&
            view.isAttachedToWindow &&
            view.getTag(R.id.hybrid_anim_target_visible) == true
          ) {
            if (useHardwareLayer && view.layerType != View.LAYER_TYPE_HARDWARE) {
              view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            val anim = view.animate()
              .translationY(0f)
              .setDuration(durationMs)
              .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
            if (fade) {
              anim.alpha(1f)
            }
            anim.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                  if (useHardwareLayer && view.layerType != View.LAYER_TYPE_NONE) {
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                  }
                  view.animate().setListener(null)
                }

                override fun onAnimationEnd(animation: Animator) {
                  if (useHardwareLayer && view.layerType != View.LAYER_TYPE_NONE) {
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                  }
                  view.animate().setListener(null)
                  if (view.getTag(R.id.hybrid_anim_generation) == animGen &&
                    view.getTag(R.id.hybrid_anim_target_visible) == true
                  ) {
                    onEnd?.invoke()
                  }
                }
              })
              .start()
          }
        }
        if (useHardwareLayer) {
          view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
          view.doOnPreDraw { startSlide() }
        } else {
          startSlide()
        }
      } else {
        if (useHardwareLayer && view.layerType != View.LAYER_TYPE_NONE) {
          view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        view.alpha = 1f
        view.translationY = 0f
        onEnd?.invoke()
      }
    } else {
      if (view.isAttachedToWindow && view.isLaidOut && animationsEnabled && isCurrentlyVisible) {
        if (useHardwareLayer && view.layerType != View.LAYER_TYPE_HARDWARE) {
          view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        val anim = view.animate()
          .translationY(offsetPx)
          .setDuration(AdaptiveMotionTokens.QUICK_FADE_DURATION_MS.toLong())
          .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
        if (fade) {
          anim.alpha(0f)
        }
        anim.setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
              if (useHardwareLayer && view.layerType != View.LAYER_TYPE_NONE) {
                view.setLayerType(View.LAYER_TYPE_NONE, null)
              }
              view.animate().setListener(null)
            }

            override fun onAnimationEnd(animation: Animator) {
              if (useHardwareLayer && view.layerType != View.LAYER_TYPE_NONE) {
                view.setLayerType(View.LAYER_TYPE_NONE, null)
              }
              view.animate().setListener(null)
              if (view.getTag(R.id.hybrid_anim_generation) == animGen &&
                view.getTag(R.id.hybrid_anim_target_visible) == false
              ) {
                view.visibility = hiddenVisibility
                view.alpha = 1f
                view.translationY = 0f
                onEnd?.invoke()
              }
            }
          })
          .start()
      } else {
        if (useHardwareLayer && view.layerType != View.LAYER_TYPE_NONE) {
          view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        view.visibility = hiddenVisibility
        view.alpha = 1f
        view.translationY = 0f
        onEnd?.invoke()
      }
    }
  }

  fun snapPendingVisibility(view: View, hiddenVisibility: Int = View.GONE) {
    val animGen = ((view.getTag(R.id.hybrid_anim_generation) as? Int) ?: 0) + 1
    view.setTag(R.id.hybrid_anim_generation, animGen)
    view.animate().setListener(null)
    view.animate().cancel()
    if (view.layerType != View.LAYER_TYPE_NONE) {
      view.setLayerType(View.LAYER_TYPE_NONE, null)
    }
    if (view.getTag(R.id.hybrid_anim_target_visible) == false) {
      view.visibility = hiddenVisibility
    }
    view.alpha = 1f
    view.translationY = 0f
  }
}

/**
 * Rate-limited off-main post-destroy memory reclaimer for finishing `SearchActivity` and
 * `PagerActivity` instances so dead `ViewRootImpl`/`RenderProxy` wrappers and EGL handles
 * are collected promptly without blocking UI-thread transitions or active flings.
 */
object PostDestroyMemoryReclaimer {
  internal const val DEFAULT_DELAY_MS = 160L
  internal const val MIN_GC_INTERVAL_MS = 450L

  private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val lastGcUptimeMs = AtomicLong(0L)
  @Volatile
  private var pendingJob: Job? = null

  fun schedulePostDestroyCleanup(
    delayMs: Long = DEFAULT_DELAY_MS,
    nowProvider: () -> Long = { SystemClock.uptimeMillis() },
    gcAction: () -> Unit = { Runtime.getRuntime().gc() }
  ) {
    pendingJob?.cancel()
    pendingJob = cleanupScope.launch {
      if (delayMs > 0L) {
        delay(delayMs)
      }
      if (!isActive) return@launch
      val now = nowProvider()
      val prev = lastGcUptimeMs.get()
      if (now - prev >= MIN_GC_INTERVAL_MS && lastGcUptimeMs.compareAndSet(prev, now)) {
        gcAction()
      }
    }
  }

  internal fun cancelPending() {
    pendingJob?.cancel()
    pendingJob = null
  }

  internal fun resetForTest() {
    cancelPending()
    lastGcUptimeMs.set(0L)
  }
}

/**
 * Immutable vector layer specification inside a 24x24 SVG viewport for [VectorPathDrawable].
 * Supports filled glyphs, uniform `1.65dp` monochrome architectural strokes (ROUND cap & join),
 * and hybrid fill+stroke icons (e.g. overlapping Appearance circles, filled Bookmark, Play/Skip).
 */
data class VectorLayerSpec(
  val pathData: String,
  val fillAlphaFraction: Float = 0f,
  val strokeWidthViewport: Float = 0f,
  val strokeAlphaFraction: Float = 1f
)

/**
 * Monochrome architectural stroke icon specification composed of one or more [VectorLayerSpec]s.
 */
data class ArchitecturalIconSpec(
  val layers: List<VectorLayerSpec>,
  val viewportSize: Float = 24f
)

/**
 * Lightweight 24x24 vector path drawable using AndroidX [PathParser], avoiding XML vector
 * inflation overhead and Compose ImageVector dependencies across Native Views.
 * Supports both legacy filled paths and uniform `1.65dp` monochrome architectural stroke specs.
 */
class VectorPathDrawable private constructor(
  private val layerList: List<VectorLayerSpec>,
  @ColorInt private var tintColor: Int,
  private val intrinsicPx: Int,
  private val viewportSize: Float,
  @Suppress("UNUSED_PARAMETER") internalMarker: Byte
) : Drawable() {

  constructor(
    pathDataList: List<String>,
    @ColorInt tintColor: Int,
    intrinsicPx: Int,
    viewportSize: Float = 24f
  ) : this(
    layerList = pathDataList.map { VectorLayerSpec(pathData = it, fillAlphaFraction = 1f) },
    tintColor = tintColor,
    intrinsicPx = intrinsicPx,
    viewportSize = viewportSize,
    internalMarker = 0
  )

  constructor(
    pathData: String,
    @ColorInt tintColor: Int,
    intrinsicPx: Int,
    viewportSize: Float = 24f
  ) : this(
    layerList = listOf(VectorLayerSpec(pathData = pathData, fillAlphaFraction = 1f)),
    tintColor = tintColor,
    intrinsicPx = intrinsicPx,
    viewportSize = viewportSize,
    internalMarker = 0
  )

  constructor(
    spec: ArchitecturalIconSpec,
    @ColorInt tintColor: Int,
    intrinsicPx: Int
  ) : this(
    layerList = spec.layers,
    tintColor = tintColor,
    intrinsicPx = intrinsicPx,
    viewportSize = spec.viewportSize,
    internalMarker = 0
  )

  private data class ParsedLayer(
    val spec: VectorLayerSpec,
    val basePath: Path
  )

  private val parsedLayers: List<ParsedLayer> = layerList.mapNotNull { spec ->
    getOrParsePath(spec.pathData)?.let { ParsedLayer(spec, it) }
  }
  internal val parsedLayerCount: Int get() = parsedLayers.size
  private val drawPaths: List<Path> = List(parsedLayers.size) { Path() }
  private val scaleMatrix = Matrix()
  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = tintColor
  }
  private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    color = tintColor
  }
  private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
  private var alphaByte = 255
  private var cachedWidth = -1
  private var cachedHeight = -1

  private data class RasterKey(
    val layers: List<VectorLayerSpec>,
    val width: Int,
    val height: Int,
    val color: Int,
    val viewportSize: Float
  )

  companion object {
    private const val MAX_RASTER_DIM_PX = 128
    private val parsedPathCache = ConcurrentHashMap<String, Path>()
    private val rasterBitmapCache = LruCache<RasterKey, Bitmap>(96)

    init {
      BitmapPoolTrimRegistry.register { clearRasterCache() }
    }

    fun clearRasterCache() {
      synchronized(rasterBitmapCache) {
        rasterBitmapCache.evictAll()
      }
    }

    private fun getOrParsePath(data: String): Path? {
      parsedPathCache[data]?.let { return it }
      val parsed = runCatching { PathParser.createPathFromPathData(data) }.getOrNull() ?: return null
      return parsedPathCache.putIfAbsent(data, parsed) ?: parsed
    }

    @ColorInt
    private fun scaleAlpha(@ColorInt color: Int, fraction: Float): Int {
      val a = (Color.alpha(color) * fraction.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
      return (color and 0x00FFFFFF) or (a shl 24)
    }

    fun architectural(
      spec: ArchitecturalIconSpec,
      @ColorInt tintColor: Int,
      intrinsicPx: Int
    ): VectorPathDrawable = VectorPathDrawable(spec, tintColor, intrinsicPx)

    fun prewarmHubPaths() {
      getOrParsePath(HybridVectorPaths.PALETTE)
      getOrParsePath(HybridVectorPaths.SETTINGS)
      getOrParsePath(HybridVectorPaths.MENU_BOOK)
      getOrParsePath(HybridVectorPaths.LIST_INDEX)
      getOrParsePath(HybridVectorPaths.SEARCH)
      getOrParsePath(HybridVectorPaths.BOOKMARKS)
      getOrParsePath(HybridVectorPaths.ARROW_BACK_RTL)
      for (spec in listOf(
        HybridArchitecturalIcons.APPEARANCE_FILLED,
        HybridArchitecturalIcons.SETTINGS,
        HybridArchitecturalIcons.BOOK,
        HybridArchitecturalIcons.INDEX,
        HybridArchitecturalIcons.SEARCH,
        HybridArchitecturalIcons.BOOKMARK,
        HybridArchitecturalIcons.CHEVRON_LEFT
      )) {
        for (layer in spec.layers) {
          getOrParsePath(layer.pathData)
        }
      }
    }

    fun prewarmBitmap(
      pathData: String,
      @ColorInt color: Int,
      sizePx: Int,
      viewportSize: Float = 24f
    ) {
      if (sizePx <= 0 || sizePx > MAX_RASTER_DIM_PX) return
      val parsed = getOrParsePath(pathData) ?: return
      val spec = VectorLayerSpec(pathData = pathData, fillAlphaFraction = 1f)
      getOrRenderBitmap(
        listOf(spec),
        listOf(ParsedLayer(spec, parsed)),
        sizePx,
        sizePx,
        color,
        viewportSize
      )
    }

    fun prewarmBitmap(
      spec: ArchitecturalIconSpec,
      @ColorInt color: Int,
      sizePx: Int
    ) {
      if (sizePx <= 0 || sizePx > MAX_RASTER_DIM_PX) return
      val parsed = spec.layers.mapNotNull { layer ->
        getOrParsePath(layer.pathData)?.let { ParsedLayer(layer, it) }
      }
      if (parsed.isEmpty()) return
      getOrRenderBitmap(spec.layers, parsed, sizePx, sizePx, color, spec.viewportSize)
    }

    private fun getOrRenderBitmap(
      layers: List<VectorLayerSpec>,
      parsedLayers: List<ParsedLayer>,
      width: Int,
      height: Int,
      @ColorInt color: Int,
      viewportSize: Float
    ): Bitmap? {
      if (parsedLayers.isEmpty()) return null
      val key = RasterKey(layers, width, height, color, viewportSize)
      synchronized(rasterBitmapCache) {
        rasterBitmapCache.get(key)?.takeIf { !it.isRecycled }?.let { return it }
      }
      val bmp = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
        ?: return null
      val c = Canvas(bmp)
      val scaleX = width / viewportSize
      val scaleY = height / viewportSize
      val strokeScale = minOf(scaleX, scaleY)
      val m = Matrix().apply { setScale(scaleX, scaleY) }
      val fPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
      }
      val sPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
      }
      val tempPath = Path()
      for (i in parsedLayers.indices) {
        val layer = parsedLayers[i]
        tempPath.set(layer.basePath)
        tempPath.transform(m)
        if (layer.spec.fillAlphaFraction > 0f) {
          fPaint.color = scaleAlpha(color, layer.spec.fillAlphaFraction)
          c.drawPath(tempPath, fPaint)
        }
        if (layer.spec.strokeWidthViewport > 0f) {
          sPaint.color = scaleAlpha(color, layer.spec.strokeAlphaFraction)
          sPaint.strokeWidth = layer.spec.strokeWidthViewport * strokeScale
          c.drawPath(tempPath, sPaint)
        }
      }
      synchronized(rasterBitmapCache) {
        val existing = rasterBitmapCache.get(key)
        if (existing != null && !existing.isRecycled) {
          bmp.recycle()
          return existing
        }
        rasterBitmapCache.put(key, bmp)
      }
      return bmp
    }
  }

  fun setTintColor(@ColorInt color: Int) {
    if (tintColor != color) {
      tintColor = color
      fillPaint.color = color
      strokePaint.color = color
      invalidateSelf()
    }
  }

  fun prewarmBitmap() {
    if (intrinsicPx > 0 && intrinsicPx <= MAX_RASTER_DIM_PX) {
      getOrRenderBitmap(layerList, parsedLayers, intrinsicPx, intrinsicPx, tintColor, viewportSize)
    }
  }

  override fun getIntrinsicWidth(): Int = intrinsicPx
  override fun getIntrinsicHeight(): Int = intrinsicPx

  override fun draw(canvas: Canvas) {
    val b = bounds
    val w = b.width()
    val h = b.height()
    if (w <= 0 || h <= 0) return

    if (w <= MAX_RASTER_DIM_PX && h <= MAX_RASTER_DIM_PX) {
      val bmp = getOrRenderBitmap(layerList, parsedLayers, w, h, tintColor, viewportSize)
      if (bmp != null && !bmp.isRecycled) {
        canvas.drawBitmap(bmp, b.left.toFloat(), b.top.toFloat(), bitmapPaint)
        return
      }
    }

    val scaleX = w / viewportSize
    val scaleY = h / viewportSize
    if (w != cachedWidth || h != cachedHeight) {
      cachedWidth = w
      cachedHeight = h
      scaleMatrix.reset()
      scaleMatrix.setScale(scaleX, scaleY)
      scaleMatrix.postTranslate(b.left.toFloat(), b.top.toFloat())
      for (i in parsedLayers.indices) {
        drawPaths[i].set(parsedLayers[i].basePath)
        drawPaths[i].transform(scaleMatrix)
      }
    }

    val strokeScale = minOf(scaleX, scaleY)
    val alphaFraction = alphaByte / 255f
    for (i in parsedLayers.indices) {
      val spec = parsedLayers[i].spec
      val path = drawPaths[i]
      if (spec.fillAlphaFraction > 0f) {
        fillPaint.color = scaleAlpha(tintColor, spec.fillAlphaFraction * alphaFraction)
        canvas.drawPath(path, fillPaint)
      }
      if (spec.strokeWidthViewport > 0f) {
        strokePaint.color = scaleAlpha(tintColor, spec.strokeAlphaFraction * alphaFraction)
        strokePaint.strokeWidth = spec.strokeWidthViewport * strokeScale
        canvas.drawPath(path, strokePaint)
      }
    }
  }

  override fun setAlpha(alpha: Int) {
    alphaByte = alpha.coerceIn(0, 255)
    bitmapPaint.alpha = alphaByte
    invalidateSelf()
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
    fillPaint.colorFilter = colorFilter
    strokePaint.colorFilter = colorFilter
    bitmapPaint.colorFilter = colorFilter
    invalidateSelf()
  }

  @Deprecated("Deprecated in Java")
  override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Strictly monochrome SF-Symbols-inspired architectural stroke icons (`1.65dp` in a 24x24 viewport,
 * ROUND cap & join) matching `floating_layers_theme_proposal.html` lines 253-275.
 */
object HybridArchitecturalIcons {
  const val STROKE_WIDTH = 1.65f

  private const val APPEARANCE_LEFT_CIRCLE =
    "M4,12 A5.5,5.5 0 1,0 15,12 A5.5,5.5 0 1,0 4,12 Z"
  private const val APPEARANCE_RIGHT_CIRCLE =
    "M9,12 A5.5,5.5 0 1,0 20,12 A5.5,5.5 0 1,0 9,12 Z"

  val APPEARANCE = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(APPEARANCE_LEFT_CIRCLE, strokeWidthViewport = STROKE_WIDTH),
      VectorLayerSpec(APPEARANCE_RIGHT_CIRCLE, strokeWidthViewport = STROKE_WIDTH)
    )
  )

  val APPEARANCE_FILLED = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(APPEARANCE_LEFT_CIRCLE, strokeWidthViewport = STROKE_WIDTH),
      VectorLayerSpec(
        APPEARANCE_RIGHT_CIRCLE,
        fillAlphaFraction = 0.22f,
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  val SETTINGS = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(
        "M9,12 A3,3 0 1,0 15,12 A3,3 0 1,0 9,12 Z",
        strokeWidthViewport = STROKE_WIDTH
      ),
      VectorLayerSpec(
        "M19.4,15 a1.65,1.65 0 0,0 0.33,1.82 l0.06,0.06 a2,2 0 1,1 -2.83,2.83 " +
          "l-0.06,-0.06 a1.65,1.65 0 0,0 -1.82,-0.33 a1.65,1.65 0 0,0 -1,1.51 V21 " +
          "a2,2 0 1,1 -4,0 v-0.09 a1.65,1.65 0 0,0 -1,-1.51 a1.65,1.65 0 0,0 -1.82,0.33 " +
          "l-0.06,0.06 a2,2 0 1,1 -2.83,-2.83 l0.06,-0.06 a1.65,1.65 0 0,0 0.33,-1.82 " +
          "a1.65,1.65 0 0,0 -1.51,-1 H3 a2,2 0 1,1 0,-4 h0.09 a1.65,1.65 0 0,0 1.51,-1 " +
          "a1.65,1.65 0 0,0 -0.33,-1.82 l-0.06,-0.06 a2,2 0 1,1 2.83,-2.83 l0.06,0.06 " +
          "a1.65,1.65 0 0,0 1.82,0.33 h0.08 a1.65,1.65 0 0,0 0.92,-1.51 V3 a2,2 0 1,1 4,0 " +
          "v0.09 a1.65,1.65 0 0,0 1,1.51 a1.65,1.65 0 0,0 1.82,-0.33 l0.06,-0.06 " +
          "a2,2 0 1,1 2.83,2.83 l-0.06,0.06 a1.65,1.65 0 0,0 -0.33,1.82 v0.08 " +
          "a1.65,1.65 0 0,0 1.51,0.92 H21 a2,2 0 1,1 0,4 h-0.09 a1.65,1.65 0 0,0 -1.51,1 Z",
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  val BOOK = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(
        "M2,5.5 A2.5,2.5 0 0,1 4.5,3 H9 A3,3 0 0,1 12,6 V20.5 " +
          "A2.2,2.2 0 0,0 9.8,18.3 H4.5 A2.5,2.5 0 0,0 2,20.5 Z",
        strokeWidthViewport = STROKE_WIDTH
      ),
      VectorLayerSpec(
        "M22,5.5 A2.5,2.5 0 0,0 19.5,3 H15 A3,3 0 0,0 12,6 V20.5 " +
          "A2.2,2.2 0 0,1 14.2,18.3 H19.5 A2.5,2.5 0 0,1 22,20.5 Z",
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  val INDEX = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(
        "M8,6.5 L20,6.5 M8,12 L20,12 M8,17.5 L20,17.5",
        strokeWidthViewport = STROKE_WIDTH
      ),
      VectorLayerSpec(
        "M3.5,6.5 A1,1 0 1,0 5.5,6.5 A1,1 0 1,0 3.5,6.5 Z " +
          "M3.5,12 A1,1 0 1,0 5.5,12 A1,1 0 1,0 3.5,12 Z " +
          "M3.5,17.5 A1,1 0 1,0 5.5,17.5 A1,1 0 1,0 3.5,17.5 Z",
        fillAlphaFraction = 1f
      )
    )
  )

  val SEARCH = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(
        "M4.5,11 A6.5,6.5 0 1,0 17.5,11 A6.5,6.5 0 1,0 4.5,11 Z M20,20 L15.8,15.8",
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  private const val BOOKMARK_PATH =
    "M6.5,4.5 A1.5,1.5 0 0,1 8,3 H16 A1.5,1.5 0 0,1 17.5,4.5 V21 L12,17.4 L6.5,21 Z"

  val BOOKMARK = ArchitecturalIconSpec(
    listOf(VectorLayerSpec(BOOKMARK_PATH, strokeWidthViewport = STROKE_WIDTH))
  )

  val BOOKMARK_FILLED = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(
        BOOKMARK_PATH,
        fillAlphaFraction = 1f,
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  val PLAY = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(
        "M8,5.5 L19,12 L8,18.5 Z",
        fillAlphaFraction = 1f,
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  val PAUSE = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(
        "M7.5,5.5 H9.5 A1,1 0 0,1 10.5,6.5 V17.5 A1,1 0 0,1 9.5,18.5 " +
          "H7.5 A1,1 0 0,1 6.5,17.5 V6.5 A1,1 0 0,1 7.5,5.5 Z " +
          "M14.5,5.5 H16.5 A1,1 0 0,1 17.5,6.5 V17.5 A1,1 0 0,1 16.5,18.5 " +
          "H14.5 A1,1 0 0,1 13.5,17.5 V6.5 A1,1 0 0,1 14.5,5.5 Z",
        fillAlphaFraction = 1f,
        strokeWidthViewport = 1.2f
      )
    )
  )

  val PREV = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec("M6,6 L6,18", strokeWidthViewport = STROKE_WIDTH),
      VectorLayerSpec(
        "M18,6.5 L9.5,12 L18,17.5 Z",
        fillAlphaFraction = 1f,
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  val NEXT = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec("M18,6 L18,18", strokeWidthViewport = STROKE_WIDTH),
      VectorLayerSpec(
        "M6,6.5 L14.5,12 L6,17.5 Z",
        fillAlphaFraction = 1f,
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  val COPY = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(
        "M11,9 H18 A2,2 0 0,1 20,11 V18 A2,2 0 0,1 18,20 H11 A2,2 0 0,1 9,18 V11 A2,2 0 0,1 11,9 Z",
        strokeWidthViewport = STROKE_WIDTH
      ),
      VectorLayerSpec(
        "M5,15 H4 A2,2 0 0,1 2,13 V4 A2,2 0 0,1 4,2 H13 A2,2 0 0,1 15,4 V5",
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  val SHARE = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(
        "M4,12 V19 A2,2 0 0,0 6,21 H18 A2,2 0 0,0 20,19 V12 M16,6 L12,2 L8,6 M12,2 L12,15",
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  val TAFSIR = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(
        "M4,4.5 A2.5,2.5 0 0,1 6.5,2 H19 A1,1 0 0,1 20,3 V20 A1,1 0 0,1 19,21 " +
          "H6.5 A2.5,2.5 0 0,1 4,18.5 Z M8.5,7.5 L15.5,7.5 M8.5,11.5 L15.5,11.5 M8.5,15.5 L12.5,15.5",
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  val ARROW_RIGHT = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec("M5,12 L19,12 M13,6 L19,12 L13,18", strokeWidthViewport = STROKE_WIDTH)
    )
  )

  val CHEVRON_LEFT = ArchitecturalIconSpec(
    listOf(VectorLayerSpec("M15,18 L9,12 L15,6", strokeWidthViewport = STROKE_WIDTH))
  )

  val CHEVRON_UP = ArchitecturalIconSpec(
    listOf(VectorLayerSpec("M6,15 L12,9 L18,15", strokeWidthViewport = STROKE_WIDTH))
  )

  val CHEVRON_DOWN = ArchitecturalIconSpec(
    listOf(VectorLayerSpec("M6,9 L12,15 L18,9", strokeWidthViewport = STROKE_WIDTH))
  )

  val CLOSE = ArchitecturalIconSpec(
    listOf(VectorLayerSpec("M17,7 L7,17 M7,7 L17,17", strokeWidthViewport = STROKE_WIDTH))
  )

  val CLOCK = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(
        "M3.5,12 A8.5,8.5 0 1,0 20.5,12 A8.5,8.5 0 1,0 3.5,12 Z M12,7.5 L12,12 L15,14",
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  val STOP = ArchitecturalIconSpec(
    listOf(
      VectorLayerSpec(
        "M8.5,6.5 H15.5 A2,2 0 0,1 17.5,8.5 V15.5 A2,2 0 0,1 15.5,17.5 " +
          "H8.5 A2,2 0 0,1 6.5,15.5 V8.5 A2,2 0 0,1 8.5,6.5 Z",
        fillAlphaFraction = 1f,
        strokeWidthViewport = STROKE_WIDTH
      )
    )
  )

  val CHECK = ArchitecturalIconSpec(
    listOf(VectorLayerSpec("M4.5,12.5 L9.5,17.5 L19.5,7.5", strokeWidthViewport = STROKE_WIDTH))
  )

  fun drawable(
    spec: ArchitecturalIconSpec,
    @ColorInt tintColor: Int,
    intrinsicPx: Int
  ): VectorPathDrawable = VectorPathDrawable(spec, tintColor, intrinsicPx)

  val ALL: Map<String, ArchitecturalIconSpec> = mapOf(
    "APPEARANCE" to APPEARANCE,
    "APPEARANCE_FILLED" to APPEARANCE_FILLED,
    "SETTINGS" to SETTINGS,
    "BOOK" to BOOK,
    "INDEX" to INDEX,
    "SEARCH" to SEARCH,
    "BOOKMARK" to BOOKMARK,
    "BOOKMARK_FILLED" to BOOKMARK_FILLED,
    "PLAY" to PLAY,
    "PAUSE" to PAUSE,
    "PREV" to PREV,
    "NEXT" to NEXT,
    "COPY" to COPY,
    "SHARE" to SHARE,
    "TAFSIR" to TAFSIR,
    "ARROW_RIGHT" to ARROW_RIGHT,
    "CHEVRON_LEFT" to CHEVRON_LEFT,
    "CHEVRON_UP" to CHEVRON_UP,
    "CHEVRON_DOWN" to CHEVRON_DOWN,
    "CLOSE" to CLOSE,
    "CLOCK" to CLOCK,
    "STOP" to STOP,
    "CHECK" to CHECK
  )
}

/**
 * Standard 24dp Material icon vector path strings shared across Main Hub, Search, and Reader.
 */
object HybridVectorPaths {
  const val PALETTE =
    "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.83,0 1.5,-0.67 1.5,-1.5 0,-0.39 -0.15,-0.74 -0.39,-1.01 -0.23,-0.26 -0.38,-0.61 -0.38,-0.99 0,-0.83 0.67,-1.5 1.5,-1.5L16,16c2.76,0 5,-2.24 5,-5 0,-4.42 -4.03,-8 -9,-8zM6.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,9 6.5,9 8,9.67 8,10.5 7.33,12 6.5,12zM9.5,8C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5s1.5,0.67 1.5,1.5S10.33,8 9.5,8zM14.5,8c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.67,5 14.5,5s1.5,0.67 1.5,1.5S15.33,8 14.5,8zM17.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.67,9 17.5,9s1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z"
  const val SETTINGS =
    "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94 0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6 3.6,1.62 3.6,3.6 -1.62,3.6 -3.6,3.6z"
  const val MENU_BOOK =
    "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5 -1.95,0 -4.05,0.4 -5.5,1.5 -1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5 0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5 1.35,-0.85 3.8,-1.5 5.5,-1.5 1.65,0 3.35,0.3 4.75,1.05 0.1,0.05 0.15,0.05 0.25,0.05 0.25,0 0.5,-0.25 0.5,-0.5V6c-0.6,-0.45 -1.25,-0.75 -2,-1zM21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5 -1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5 1.2,0 2.4,0.15 3.5,0.5v11.5z"
  const val LIST_INDEX =
    "M3,13h2v-2H3v2zm0,4h2v-2H3v2zm0,-8h2V7H3v2zm4,4h14v-2H7v2zm0,4h14v-2H7v2zM7,7v2h14V7H7z"
  const val SEARCH =
    "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"
  const val BOOKMARKS =
    "M19,18l2,1V3c0,-1.1 -0.9,-2 -2,-2H8.99C7.89,1 7,1.9 7,3h10c1.1,0 2,0.9 2,2v13zM15,5H5c-1.1,0 -2,0.9 -2,2v16l7,-3 7,3V7c0,-1.1 -0.9,-2 -2,-2z"
  const val BOOKMARK_FILLED =
    "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z"
  const val BOOKMARK_BORDER =
    "M17,3H7c-1.1,0 -2,0.9 -2,2v16l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z"
  const val CLOSE =
    "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"
  const val PLAY_ARROW =
    "M8,5v14l11,-7z"
  const val PAUSE =
    "M6,19h4V5H6v14zm8,-14v14h4V5h-4z"
  const val ARROW_BACK_RTL =
    "M11.67,3.87L9.9,2.1 0,12l9.9,9.9 1.77,-1.77L3.54,12z"
  const val ARROW_FORWARD =
    "M12,4l-1.41,1.41L16.17,11H4v2h12.17l-5.58,5.59L12,20l8,-8z"
  const val HISTORY =
    "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z"
  const val DELETE_OUTLINE =
    "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM8,9h8v10H8V9zm7.5,-5l-1,-1h-5l-1,1H5v2h14V4z"
  const val COPY =
    "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z"
  const val SHARE =
    "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z"
  const val EXPAND_LESS =
    "M12,8l-6,6 1.41,1.41L12,10.83l4.59,4.58L18,14z"
  const val EXPAND_MORE =
    "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z"
  const val PREVIOUS =
    "M6,6h2v12H6zm3.5,6l8.5,6V6z"
  const val NEXT =
    "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z"
  const val STOP =
    "M6,6h12v12H6z"
  const val HEADSET =
    "M12,1c-4.97,0 -9,4.03 -9,9v7c0,1.66 1.34,3 3,3h3v-8H5v-2c0,-3.87 3.13,-7 7,-7s7,3.13 7,7v2h-4v8h3c1.66,0 3,-1.34 3,-3v-7c0,-4.97 -4.03,-9 -9,-9z"
  const val CHECK =
    "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"
  const val NOTE_ADD =
    "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM16,16h-3v3h-2v-3H8v-2h3v-3h2v3h3v2zM13,9V3.5L18.5,9H13z"
  const val SPARKLES =
    "M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9zM11.5,9.5L9,4 6.5,9.5 1,12l5.5,2.5L9,20l2.5,-5.5L17,12l-5.5,-2.5z"
}
