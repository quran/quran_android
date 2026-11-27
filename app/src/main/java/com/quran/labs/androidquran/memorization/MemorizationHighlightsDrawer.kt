package com.quran.labs.androidquran.memorization

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Region
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.view.NightModeColorMatrix
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.data.coordinates.GlyphCoords
import com.quran.page.common.data.coordinates.PageGlyphsCoords
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws hidden words (colored with the Mushaf paper background color) and animates
 * revealed words or full ayahs by transitioning their text color in-place from the
 * Mushaf paper background color to the visible text color, while preserving
 * end-of-ayah medallions (﴿١﴾) visible in place.
 */
class MemorizationHighlightsDrawer(
  private val ayahCoordinatesProvider: () -> AyahCoordinates?,
  private val superOnDraw: (Canvas) -> Unit,
  private val isNightModeProvider: () -> Boolean,
  private val nightTextBrightnessProvider: () -> Int,
  private val nightBgBrightnessProvider: () -> Int,
  private val wordAnimationFractionsProvider: () -> Map<String, Float>
) {

  private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val spotlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }
  private val spotlightBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
  }
  private val spotlightAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }
  private val skippedHintDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }
  private val skippedHintHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }
  private val opaquePaperMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }
  private val scaledRect = RectF()
  private val pillRect = RectF()
  private val accentBarRect = RectF()
  private val tempWordRect = RectF()
  private val unrevealedWordsPath = Path()
  private val plainMatrixBuffer = FloatArray(20)
  private val animMatrixBuffer = FloatArray(20)

  fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    val page = pageCoordinates.page
    val wordFractions = wordAnimationFractionsProvider()
    val isHiddenMode = QuranMemorizationManager.isHiddenModeEnabled()

    // Nothing to draw if hidden mode is off and no reveal animations are active
    if (!isHiddenMode && wordFractions.isEmpty()) {
      return
    }

    val ayahCoords = ayahCoordinatesProvider() ?: return
    val glyphsCoords = ayahCoords.glyphCoordinates
      ?: AyahGlyphSynthesizer.synthesize(page, ayahCoords.ayahCoordinates)
      ?: return

    val context = image.context
    val quranSettings = QuranSettings.getInstance(context)
    val isNightMode = isNightModeProvider()

    val paperBackgroundColor = when {
      isNightMode -> {
        val bg = nightBgBrightnessProvider()
        Color.rgb(bg, bg, bg)
      }
      !quranSettings.isLightPage -> quranSettings.pageThemeBackgroundColor
      else -> androidx.core.content.ContextCompat.getColor(
        context,
        com.quran.mobile.feature.pagetheme.R.color.page_background
      )
    }

    val hasTransparentBackground = hasTransparentPaper(image)
    val effectiveMaskPaperColor = if (hasTransparentBackground) {
      paperBackgroundColor
    } else {
      resolveOpaqueBitmapPaperColor(
        image = image,
        isNightMode = isNightMode,
        nightBgBrightness = nightBgBrightnessProvider(),
        fallbackColor = paperBackgroundColor
      )
    }

    // Collect all end-of-ayah medallion rects (﴿١﴾) and Hizb markers (۞) so we NEVER clip or hide them
    val medallionRects = mutableListOf<RectF>()
    for ((_, glyphList) in glyphsCoords.glyphsByAyah) {
      for (glyphCoord in glyphList) {
        if (glyphCoord.glyph is AyahEndGlyph ||
            glyphCoord.glyph is com.quran.data.model.AyahGlyph.HizbGlyph
        ) {
          medallionRects.add(glyphCoord.bounds)
        }
      }
    }
    for (marker in pageCoordinates.ayahMarkers) {
      val markerAyah = SuraAyah(marker.sura, marker.ayah)
      val hasExplicitEndGlyph = glyphsCoords.glyphsByAyah[markerAyah]?.any {
        it.glyph is AyahEndGlyph
      } == true
      if (!hasExplicitEndGlyph) {
        val r = 22f
        medallionRects.add(
          RectF(
            marker.x - r,
            marker.y - r,
            marker.x + r,
            marker.y + r
          )
        )
      }
    }

    val revealMode = QuranMemorizationManager.getRevealMode()
    if (revealMode == RevealMode.SINGLE_WORD_TAJWEED) {
      drawSingleWordTajweedMode(
        page = page,
        glyphsCoords = glyphsCoords,
        medallionRects = medallionRects,
        wordFractions = wordFractions,
        isNightMode = isNightMode,
        nightTextBrightness = nightTextBrightnessProvider(),
        nightBgBrightness = nightBgBrightnessProvider(),
        paperBackgroundColor = paperBackgroundColor,
        canvas = canvas,
        image = image
      )
      return
    }

    for ((suraAyah, glyphList) in glyphsCoords.glyphsByAyah) {
      val wordPositions = glyphList
        .mapNotNull { (it.glyph as? WordGlyph)?.wordPosition }
        .distinct()

      for (wordPos in wordPositions) {
        val wordKey = "${suraAyah.sura}:${suraAyah.ayah}:$wordPos"
        val isRevealed = QuranMemorizationManager.isWordRevealed(page, suraAyah, wordPos)
        val animFraction = wordFractions[wordKey]

        val revealFraction: Float = when {
          animFraction != null -> animFraction.coerceIn(0f, 1f)
          isRevealed -> 1f
          else -> 0f
        }

        // If fully revealed (1f), let standard super.onDraw render the word in normal ink color
        if (revealFraction >= 0.999f) {
          continue
        }

        val wordBoundsList = glyphsCoords.getBoundsForWord(
          suraAyah,
          wordPos,
          expandBounds = true
        ) ?: continue

        for (rawBounds in wordBoundsList) {
          tempWordRect.set(rawBounds)
          trimAwayMedallions(tempWordRect, medallionRects)
          if (tempWordRect.width() <= 0.5f || tempWordRect.height() <= 0.5f) {
            continue
          }

          image.imageMatrix.mapRect(scaledRect, tempWordRect)
          scaledRect.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())

          if (revealFraction <= 0.01f) {
            // On opaque-background Mushaf bitmaps (e.g. bundled Tajweed PNGs with #FFFFFF inner paper),
            // paint the exact inner paper color before clipping out so the outer frame gradient does not bleed through.
            if (!hasTransparentBackground) {
              opaquePaperMaskPaint.color = effectiveMaskPaperColor
              canvas.drawRect(scaledRect, opaquePaperMaskPaint)
            }
            if (QuranMemorizationManager.isWordSkippedHint(page, suraAyah, wordPos)) {
              drawSkippedWordHintDot(canvas, scaledRect, isNightMode, image.resources.displayMetrics.density)
            }
            // Word is hidden: clip out its glyph bounds before super.onDraw so the Mushaf paper
            // background shows through cleanly in-place with zero rectangular cut-out artifacts.
            clipOutRect(canvas, scaledRect)
          } else {
            // Word is revealing (0f < revealFraction < 1f): fade effectiveMaskPaperColor overlay alpha
            // from 255 -> 0 using PorterDuff.Mode.SRC_ATOP so both classic ink and color-coded
            // Tajweed letters transition smoothly in-place to their natural colors.
            val overlayAlpha = ((1f - revealFraction) * 255f).roundToInt().coerceIn(0, 255)
            val currentWordColor = ColorUtils.setAlphaComponent(effectiveMaskPaperColor, overlayAlpha)
            glyphPaint.colorFilter = PorterDuffColorFilter(currentWordColor, PorterDuff.Mode.SRC_ATOP)

            canvas.save()
            canvas.clipRect(scaledRect)
            val previousFilter = image.colorFilter
            image.colorFilter = glyphPaint.colorFilter
            superOnDraw(canvas)
            image.colorFilter = previousFilter
            canvas.restore()

            clipOutRect(canvas, scaledRect)
          }
        }
      }
    }
  }

  /**
   * Renders [RevealMode.SINGLE_WORD_TAJWEED]:
   * - Hides Tajweed colors across the page so all unpressed words appear in crisp black Madani ink
   *   (or plain Night Mode text brightness when Night Mode is active).
   * - When the user presses on the screen, ONLY the pressed word receives:
   *   1) A high-contrast spotlight background highlight pill (with border + bottom tracking bar)
   *      so Tajweed colors look stronger against the page AND words with no Tajweed rules (pure black)
   *      remain effortless to follow word-by-word.
   *   2) Smooth transition from black Madani ink (0f) into boosted, vivid Tajweed colors (1f).
   */
  private fun drawSingleWordTajweedMode(
    page: Int,
    glyphsCoords: PageGlyphsCoords,
    medallionRects: List<RectF>,
    wordFractions: Map<String, Float>,
    isNightMode: Boolean,
    nightTextBrightness: Int,
    nightBgBrightness: Int,
    paperBackgroundColor: Int,
    canvas: Canvas,
    image: ImageView
  ) {
    val hasTransparentBackground = hasTransparentPaper(image)
    val density = image.resources.displayMetrics.density.coerceAtLeast(1f)
    val plainMadaniFilter = ColorMatrixColorFilter(
      TajweedRevealColorMatrix.create(
        revealFraction = 0f,
        isNightMode = isNightMode,
        nightTextBrightness = nightTextBrightness,
        nightBgBrightness = nightBgBrightness,
        paperBackgroundColor = paperBackgroundColor,
        hasTransparentBackground = hasTransparentBackground,
        boostTajweedStrength = false,
        outMatrix = plainMatrixBuffer
      )
    )

    unrevealedWordsPath.reset()

    for ((suraAyah, glyphList) in glyphsCoords.glyphsByAyah) {
      val wordPositions = glyphList
        .mapNotNull { (it.glyph as? WordGlyph)?.wordPosition }
        .distinct()

      for (wordPos in wordPositions) {
        val wordKey = "${suraAyah.sura}:${suraAyah.ayah}:$wordPos"
        val isRevealed = QuranMemorizationManager.isWordRevealed(page, suraAyah, wordPos)
        val animFraction = wordFractions[wordKey]

        val revealFraction: Float = when {
          animFraction != null -> animFraction.coerceIn(0f, 1f)
          isRevealed -> 1f
          else -> 0f
        }

        val wordBoundsList = glyphsCoords.getBoundsForWord(
          suraAyah,
          wordPos,
          expandBounds = true
        ) ?: continue

        if (revealFraction <= 0.01f) {
          for (rawBounds in wordBoundsList) {
            tempWordRect.set(rawBounds)
            trimAwayMedallions(tempWordRect, medallionRects)
            if (tempWordRect.width() <= 0.5f || tempWordRect.height() <= 0.5f) {
              continue
            }
            image.imageMatrix.mapRect(scaledRect, tempWordRect)
            scaledRect.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
            unrevealedWordsPath.addRect(scaledRect, Path.Direction.CW)
          }
          continue
        }

        // Merge same-line word + pause glyph bounds so the active word gets one unified spotlight pill
        val mappedRects = mutableListOf<RectF>()
        for (rawBounds in wordBoundsList) {
          tempWordRect.set(rawBounds)
          trimAwayMedallions(tempWordRect, medallionRects)
          if (tempWordRect.width() <= 0.5f || tempWordRect.height() <= 0.5f) {
            continue
          }
          val mapped = RectF()
          image.imageMatrix.mapRect(mapped, tempWordRect)
          mapped.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
          mergeIntoSameLineRects(mappedRects, mapped)
        }

        if (mappedRects.isEmpty()) {
          continue
        }

        val activeTajweedFilter = ColorMatrixColorFilter(
          TajweedRevealColorMatrix.create(
            revealFraction = revealFraction,
            isNightMode = isNightMode,
            nightTextBrightness = nightTextBrightness,
            nightBgBrightness = nightBgBrightness,
            paperBackgroundColor = paperBackgroundColor,
            hasTransparentBackground = hasTransparentBackground,
            boostTajweedStrength = true,
            outMatrix = animMatrixBuffer
          )
        )

        for (activeRect in mappedRects) {
          val insetX = (1.5f * density).coerceAtMost(activeRect.width() * 0.04f)
          val insetY = (1.5f * density).coerceAtMost(activeRect.height() * 0.05f)
          pillRect.set(activeRect)
          pillRect.inset(insetX, insetY)
          val cornerRadius = (6f * density).coerceAtMost(pillRect.height() * 0.28f)

          configureSpotlightPaints(
            revealFraction = revealFraction,
            isNightMode = isNightMode,
            nightBgBrightness = nightBgBrightness,
            paperBackgroundColor = paperBackgroundColor,
            hasTransparentBackground = hasTransparentBackground,
            density = density
          )

          canvas.save()
          canvas.clipRect(activeRect)

          // 1. High-contrast background highlight pill under the pressed word's glyphs
          if (hasTransparentBackground) {
            canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, spotlightFillPaint)
          }

          // 2. Render pressed word glyphs with boosted Tajweed color vibrancy
          val previousFilter = image.colorFilter
          image.colorFilter = activeTajweedFilter
          superOnDraw(canvas)
          image.colorFilter = previousFilter

          // 3. For opaque-background bitmaps, apply a subtle translucent spotlight tint above paper
          if (!hasTransparentBackground) {
            canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, spotlightFillPaint)
          }

          // 4. Crisp pill border + bottom tracking bar so even non-Tajweed black words are unmistakable
          canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, spotlightBorderPaint)
          val barHeight = (2.2f * density).coerceAtMost(pillRect.height() * 0.12f)
          val barHorizInset = (cornerRadius * 0.55f).coerceAtMost(pillRect.width() * 0.18f)
          if (pillRect.width() > barHorizInset * 2f + 2f && barHeight >= 1f) {
            accentBarRect.set(
              pillRect.left + barHorizInset,
              pillRect.bottom - barHeight,
              pillRect.right - barHorizInset,
              pillRect.bottom
            )
            val barRadius = barHeight * 0.5f
            canvas.drawRoundRect(accentBarRect, barRadius, barRadius, spotlightAccentPaint)
          }

          canvas.restore()
          clipOutRect(canvas, activeRect)
        }
      }
    }

    if (!unrevealedWordsPath.isEmpty) {
      canvas.save()
      canvas.clipPath(unrevealedWordsPath)
      val previousFilter = image.colorFilter
      image.colorFilter = plainMadaniFilter
      superOnDraw(canvas)
      image.colorFilter = previousFilter
      canvas.restore()

      clipOutPath(canvas, unrevealedWordsPath)
    }
  }

  private fun mergeIntoSameLineRects(targetList: MutableList<RectF>, candidate: RectF) {
    for (existing in targetList) {
      val maxHeight = max(existing.height(), candidate.height())
      if (kotlin.math.abs(existing.centerY() - candidate.centerY()) <= maxHeight * 0.65f) {
        existing.union(candidate)
        return
      }
    }
    targetList.add(candidate)
  }

  private fun configureSpotlightPaints(
    revealFraction: Float,
    isNightMode: Boolean,
    nightBgBrightness: Int,
    paperBackgroundColor: Int,
    hasTransparentBackground: Boolean,
    density: Float
  ) {
    val f = revealFraction.coerceIn(0f, 1f)
    spotlightBorderPaint.strokeWidth = 1.35f * density

    if (isNightMode) {
      val bg = nightBgBrightness.coerceIn(0, 200)
      val fillAlpha = ((if (hasTransparentBackground) 238f else 56f) * f).roundToInt().coerceIn(0, 255)
      spotlightFillPaint.color = Color.argb(
        fillAlpha,
        (bg + 36).coerceAtMost(84),
        (bg + 48).coerceAtMost(96),
        (bg + 64).coerceAtMost(116)
      )
      spotlightBorderPaint.color = Color.argb(
        (150f * f).roundToInt().coerceIn(0, 255),
        45,
        212,
        191
      )
      spotlightAccentPaint.color = Color.argb(
        (205f * f).roundToInt().coerceIn(0, 255),
        45,
        212,
        191
      )
    } else {
      val isNearPureWhitePaper =
        Color.red(paperBackgroundColor) >= 250 &&
          Color.green(paperBackgroundColor) >= 250 &&
          Color.blue(paperBackgroundColor) >= 246

      if (!hasTransparentBackground) {
        spotlightFillPaint.color = Color.argb(
          (42f * f).roundToInt().coerceIn(0, 255),
          14,
          148,
          136
        )
      } else if (isNearPureWhitePaper) {
        // Cool ice-pearl spotlight against pure white paper — high luminance for Tajweed contrast
        // while clearly distinguishing the pressed word from white surrounding paper
        spotlightFillPaint.color = Color.argb(
          (242f * f).roundToInt().coerceIn(0, 255),
          230,
          241,
          252
        )
      } else {
        // Crisp pure-white spotlight card against warm cream (#FFF4CB) or sepia (#F8EBD5) paper —
        // maximizes Tajweed color vibrancy (especially golds, oranges, greens, blues) and clearly
        // spotlights plain black non-Tajweed words
        spotlightFillPaint.color = Color.argb(
          (248f * f).roundToInt().coerceIn(0, 255),
          255,
          255,
          255
        )
      }

      spotlightBorderPaint.color = Color.argb(
        (125f * f).roundToInt().coerceIn(0, 255),
        13,
        148,
        136
      )
      spotlightAccentPaint.color = Color.argb(
        (195f * f).roundToInt().coerceIn(0, 255),
        13,
        148,
        136
      )
    }
  }

  private fun hasTransparentPaper(image: ImageView): Boolean {
    val bmp = (image.drawable as? BitmapDrawable)?.bitmap ?: return true
    if (bmp.width <= 4 || bmp.height <= 4 || !bmp.hasAlpha()) {
      return false
    }
    return try {
      Color.alpha(bmp.getPixel(2, 2)) < 32
    } catch (_: Exception) {
      true
    }
  }

  private fun resolveOpaqueBitmapPaperColor(
    image: ImageView,
    isNightMode: Boolean,
    nightBgBrightness: Int,
    fallbackColor: Int
  ): Int {
    if (isNightMode) {
      val bg = nightBgBrightness.coerceIn(0, 255)
      return Color.rgb(bg, bg, bg)
    }
    val bmp = (image.drawable as? BitmapDrawable)?.bitmap ?: return fallbackColor
    if (bmp.width <= 8 || bmp.height <= 8) return Color.WHITE
    return try {
      // Sample bright paper margin pixels on opaque Mushaf/Tajweed bitmaps (usually pure #FFFFFF)
      val candidates = intArrayOf(
        bmp.getPixel(2, 2),
        bmp.getPixel(bmp.width / 2, 2),
        bmp.getPixel(2, bmp.height / 2)
      )
      var brightest = Color.WHITE
      var bestLum = -1
      for (px in candidates) {
        if (Color.alpha(px) >= 240) {
          val lum = Color.red(px) + Color.green(px) + Color.blue(px)
          if (lum > bestLum) {
            bestLum = lum
            brightest = Color.rgb(Color.red(px), Color.green(px), Color.blue(px))
          }
        }
      }
      if (bestLum >= 660) brightest else Color.WHITE
    } catch (_: Exception) {
      Color.WHITE
    }
  }

  private fun trimAwayMedallions(wordRect: RectF, medallionRects: List<RectF>) {
    for (medallion in medallionRects) {
      // Only trim against medallions on the same horizontal text line
      val medallionCenterY = medallion.centerY()
      if (medallionCenterY >= wordRect.top && medallionCenterY <= wordRect.bottom &&
          RectF.intersects(wordRect, medallion)
      ) {
        if (medallion.centerX() <= wordRect.centerX()) {
          wordRect.left = max(wordRect.left, medallion.right + 1f)
        } else {
          wordRect.right = kotlin.math.min(wordRect.right, medallion.left - 1f)
        }
      }
    }
  }

  private fun clipOutRect(canvas: Canvas, rect: RectF) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      canvas.clipOutRect(rect)
    } else {
      @Suppress("DEPRECATION")
      canvas.clipRect(rect, Region.Op.DIFFERENCE)
    }
  }

  private fun drawSkippedWordHintDot(
    canvas: Canvas,
    wordRect: RectF,
    isNightMode: Boolean,
    rawDensity: Float
  ) {
    val density = rawDensity.coerceAtLeast(1f)
    val radius = (2.8f * density).coerceAtMost(wordRect.height() * 0.16f).coerceAtLeast(2f)
    val haloRadius = radius * 1.9f
    val cx = wordRect.centerX()
    val cy = wordRect.centerY()

    if (isNightMode) {
      skippedHintHaloPaint.color = Color.argb(55, 251, 191, 36)
      skippedHintDotPaint.color = Color.argb(205, 251, 191, 36)
    } else {
      skippedHintHaloPaint.color = Color.argb(48, 217, 119, 6)
      skippedHintDotPaint.color = Color.argb(195, 217, 119, 6)
    }
    canvas.drawCircle(cx, cy, haloRadius, skippedHintHaloPaint)
    canvas.drawCircle(cx, cy, radius, skippedHintDotPaint)
  }

  private fun clipOutPath(canvas: Canvas, path: Path) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      canvas.clipOutPath(path)
    } else {
      @Suppress("DEPRECATION")
      canvas.clipPath(path, Region.Op.DIFFERENCE)
    }
  }
}

/**
 * Builds a 4x5 [android.graphics.ColorMatrix] array that smoothly interpolates a Tajweed word
 * between plain Madani black ink (`revealFraction = 0f`) and full Tajweed colors (`revealFraction = 1f`),
 * with optional saturation/contrast strengthening (`boostTajweedStrength = true`) so Tajweed rules
 * pop vividly on top of the active word's spotlight highlight while keeping plain black ink pure black.
 */
object TajweedRevealColorMatrix {

  private const val BOOSTED_SATURATION = 1.32f
  private const val LIGHT_TRANSPARENT_INK_GAIN = 0.94f
  private const val LUM_R = 0.2126f
  private const val LUM_G = 0.7152f
  private const val LUM_B = 0.0722f

  private val IDENTITY = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f
  )

  private val BOOSTED_SATURATION_ONLY: FloatArray = buildSaturationMatrix(
    saturation = BOOSTED_SATURATION,
    gain = 1f
  )

  private val BOOSTED_LIGHT_TRANSPARENT: FloatArray = buildSaturationMatrix(
    saturation = BOOSTED_SATURATION,
    gain = LIGHT_TRANSPARENT_INK_GAIN
  )

  private fun buildSaturationMatrix(saturation: Float, gain: Float): FloatArray {
    val sr = (1f - saturation) * LUM_R * gain
    val sg = (1f - saturation) * LUM_G * gain
    val sb = (1f - saturation) * LUM_B * gain
    val diagAdd = saturation * gain
    return floatArrayOf(
      sr + diagAdd, sg,           sb,           0f, 0f,
      sr,           sg + diagAdd, sb,           0f, 0f,
      sr,           sg,           sb + diagAdd, 0f, 0f,
      0f,           0f,           0f,           1f, 0f
    )
  }

  private fun concat4x5(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (row in 0 until 4) {
      val rOff = row * 5
      for (col in 0 until 4) {
        out[rOff + col] =
          a[rOff] * b[col] +
          a[rOff + 1] * b[5 + col] +
          a[rOff + 2] * b[10 + col] +
          a[rOff + 3] * b[15 + col]
      }
      out[rOff + 4] =
        a[rOff] * b[4] +
        a[rOff + 1] * b[9] +
        a[rOff + 2] * b[14] +
        a[rOff + 3] * b[19] +
        a[rOff + 4]
    }
    return out
  }

  @JvmStatic
  @JvmOverloads
  fun create(
    revealFraction: Float,
    isNightMode: Boolean = false,
    nightTextBrightness: Int = 255,
    nightBgBrightness: Int = 0,
    paperBackgroundColor: Int = Color.WHITE,
    hasTransparentBackground: Boolean = true,
    boostTajweedStrength: Boolean = false,
    outMatrix: FloatArray = FloatArray(20)
  ): FloatArray {
    val f = revealFraction.coerceIn(0f, 1f)

    val targetMatrix = when {
      isNightMode -> {
        val nightMatrix = NightModeColorMatrix.create(nightTextBrightness, nightBgBrightness)
        if (boostTajweedStrength) {
          concat4x5(nightMatrix, BOOSTED_SATURATION_ONLY)
        } else {
          nightMatrix
        }
      }
      boostTajweedStrength && hasTransparentBackground -> BOOSTED_LIGHT_TRANSPARENT
      boostTajweedStrength -> BOOSTED_SATURATION_ONLY
      else -> IDENTITY
    }

    val plainMatrix = if (hasTransparentBackground) {
      val txt = if (isNightMode) nightTextBrightness.coerceIn(0, 255).toFloat() else 0f
      floatArrayOf(
        0f, 0f, 0f, 0f, txt,
        0f, 0f, 0f, 0f, txt,
        0f, 0f, 0f, 0f, txt,
        0f, 0f, 0f, 1f, 0f
      )
    } else if (isNightMode) {
      val txt = nightTextBrightness.coerceIn(0, 255).toFloat()
      val bg = nightBgBrightness.coerceIn(0, 255).toFloat()
      val slope = -(txt - bg) / 98f
      val offset = bg - slope * 255f
      val rW = 0.3f * slope
      val gW = 0.4f * slope
      val bW = 0.3f * slope
      floatArrayOf(
        rW, gW, bW, 0f, offset,
        rW, gW, bW, 0f, offset,
        rW, gW, bW, 0f, offset,
        0f, 0f, 0f, 1f, 0f
      )
    } else {
      val bgR = Color.red(paperBackgroundColor) / 255f
      val bgG = Color.green(paperBackgroundColor) / 255f
      val bgB = Color.blue(paperBackgroundColor) / 255f
      val scale = 2.6f
      val offset = -408f
      val rW = 0.3f * scale
      val gW = 0.4f * scale
      val bW = 0.3f * scale
      floatArrayOf(
        rW * bgR, gW * bgR, bW * bgR, 0f, offset * bgR,
        rW * bgG, gW * bgG, bW * bgG, 0f, offset * bgG,
        rW * bgB, gW * bgB, bW * bgB, 0f, offset * bgB,
        0f, 0f, 0f, 1f, 0f
      )
    }

    val inv = 1f - f
    for (i in 0 until 20) {
      outMatrix[i] = inv * plainMatrix[i] + f * targetMatrix[i]
    }
    return outMatrix
  }
}

/**
 * Synthesizes [PageGlyphsCoords] with per-word [WordGlyph]s and an end-of-ayah [AyahEndGlyph]
 * medallion whenever an [AyahCoordinates] instance does not already contain [PageGlyphsCoords].
 */
object AyahGlyphSynthesizer {

  @JvmStatic
  fun synthesize(
    page: Int,
    ayahCoordinates: Map<String, List<AyahBounds>>
  ): PageGlyphsCoords? {
    if (ayahCoordinates.isEmpty()) return null

    val sortedEntries = ayahCoordinates.entries.mapNotNull { (key, bounds) ->
      val parts = key.split(":")
      if (parts.size >= 2) {
        val s = parts[0].toIntOrNull()
        val a = parts[1].toIntOrNull()
        if (s != null && a != null && bounds.isNotEmpty()) {
          SuraAyah(s, a) to bounds
        } else null
      } else null
    }.sortedBy { it.first }

    if (sortedEntries.isEmpty()) return null

    val allGlyphs = mutableListOf<GlyphCoords>()

    for ((suraAyah, boundsList) in sortedEntries) {
      val wordCount = QuranMemorizationManager.getWordCountForAyah(suraAyah).coerceAtLeast(1)

      // Check if boundsList already has individual glyph bounds per position on the same line
      val hasMultiplePerLine = boundsList.groupBy { it.line }.any { it.value.size > 1 }
      if (hasMultiplePerLine && boundsList.size >= 2) {
        var glyphPos = 1
        var wordPos = 1
        for (i in boundsList.indices) {
          val b = boundsList[i]
          val isLast = (i == boundsList.lastIndex)
          val glyph = if (isLast) {
            AyahEndGlyph(suraAyah, glyphPos++)
          } else {
            WordGlyph(suraAyah, glyphPos++, wordPos++)
          }
          allGlyphs.add(GlyphCoords(glyph, b.line, RectF(b.bounds)))
        }
        continue
      }

      // Otherwise boundsList contains line-level RectFs for this ayah.
      // Reserve the leftmost square of the final line for the end-of-ayah medallion (﴿١﴾),
      // and divide the remaining line width(s) from right to left into wordCount WordGlyphs.
      val orderedLines = boundsList.sortedBy { it.line }
      val lineRects = orderedLines.map { it.line to RectF(it.bounds) }.toMutableList()

      val (lastLineNum, lastLineRect) = lineRects.last()
      val lineHeight = lastLineRect.height().coerceAtLeast(16f)
      val medallionWidth = (lineHeight * 0.9f).coerceAtMost(lastLineRect.width() * 0.25f)

      val medallionRect = RectF(
        lastLineRect.left,
        lastLineRect.top,
        lastLineRect.left + medallionWidth,
        lastLineRect.bottom
      )
      val trimmedLastRect = RectF(
        lastLineRect.left + medallionWidth,
        lastLineRect.top,
        lastLineRect.right,
        lastLineRect.bottom
      )
      lineRects[lineRects.lastIndex] = lastLineNum to trimmedLastRect

      val totalWidth = lineRects.sumOf { it.second.width().coerceAtLeast(1f).toDouble() }.toFloat()
      var remainingWords = wordCount
      var glyphPos = 1
      var nextWordPos = 1

      for (lineIndex in lineRects.indices) {
        val (lineNum, rect) = lineRects[lineIndex]
        val wordsOnThisLine = if (lineIndex == lineRects.lastIndex) {
          remainingWords.coerceAtLeast(1)
        } else {
          val share = ((rect.width() / totalWidth) * wordCount).roundToInt().coerceAtLeast(1)
          share.coerceAtMost((remainingWords - (lineRects.size - 1 - lineIndex)).coerceAtLeast(1))
        }
        remainingWords = (remainingWords - wordsOnThisLine).coerceAtLeast(0)

        val segWidth = rect.width() / wordsOnThisLine.toFloat()
        // Words in Arabic flow from right (rect.right) to left (rect.left)
        for (w in 0 until wordsOnThisLine) {
          val segRight = rect.right - w * segWidth
          val segLeft = if (w == wordsOnThisLine - 1) rect.left else (rect.right - (w + 1) * segWidth)
          val wordRect = RectF(segLeft, rect.top, segRight, rect.bottom)
          allGlyphs.add(
            GlyphCoords(
              WordGlyph(suraAyah, glyphPos++, nextWordPos++),
              lineNum,
              wordRect
            )
          )
        }
      }

      // Append the end-of-ayah medallion (﴿١﴾) at the end of the ayah
      allGlyphs.add(
        GlyphCoords(
          AyahEndGlyph(suraAyah, glyphPos),
          lastLineNum,
          medallionRect
        )
      )
    }

    return PageGlyphsCoords(page, allGlyphs)
  }
}
