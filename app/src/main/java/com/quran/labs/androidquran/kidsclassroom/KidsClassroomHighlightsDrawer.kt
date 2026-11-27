package com.quran.labs.androidquran.kidsclassroom

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.ImageView
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.AyahGlyph.HizbGlyph
import com.quran.labs.androidquran.memorization.AyahGlyphSynthesizer
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import kotlin.math.abs
import kotlin.math.max

/**
 * Renders **Children Learning Mode** word-level classroom highlights and Arabic feedback badges
 * (`✓`, `حركة`, `خطأ`, `نسيان`) **directly on the Tajweed Mushaf page** (`HighlightingImageView`)
 * after `super.onDraw(canvas)` so the authentic Tajweed letter colors remain 100% visible while
 * guiding and grading the child word-by-word.
 */
class KidsClassroomHighlightsDrawer(
  private val ayahCoordinatesProvider: () -> AyahCoordinates?,
  private val isNightModeProvider: () -> Boolean
) {

  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }
  private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
  }
  private val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }
  private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }
  private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
    isFakeBoldText = true
  }

  private val tempWordRect = RectF()
  private val pillRect = RectF()
  private val barRect = RectF()
  private val badgeRect = RectF()

  fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    val state = KidsClassroomManager.currentState()
    if (!state.isEnabled) return

    val ayahCoords = ayahCoordinatesProvider() ?: return
    val glyphsCoords = ayahCoords.glyphCoordinates
      ?: AyahGlyphSynthesizer.synthesize(pageCoordinates.page, ayahCoords.ayahCoordinates)
      ?: return

    val targetAyah = state.currentAyah
    if (!glyphsCoords.glyphsByAyah.containsKey(targetAyah)) {
      return
    }

    val density = image.resources.displayMetrics.density.coerceAtLeast(1f)
    val isNight = isNightModeProvider()

    // Collect medallion rects so word pills never cover verse end markers (﴿١﴾)
    val medallionRects = mutableListOf<RectF>()
    glyphsCoords.glyphsByAyah[targetAyah]?.forEach { glyphCoord ->
      if (glyphCoord.glyph is AyahEndGlyph || glyphCoord.glyph is HizbGlyph) {
        medallionRects.add(glyphCoord.bounds)
      }
    }

    if (state.practiceMode == KidsPracticeMode.RECITE_AND_REVEAL) {
      val totalWords = state.ayahWords.size.coerceAtLeast(1)
      val revealedCount = state.revealedCount.coerceIn(0, totalWords)
      for (wordIdx in 0 until totalWords) {
        val mushafWordPos = wordIdx + 1
        val wordBoundsList = glyphsCoords.getBoundsForWord(
          targetAyah,
          mushafWordPos,
          expandBounds = true
        ) ?: continue

        val mappedRects = mutableListOf<RectF>()
        for (rawBounds in wordBoundsList) {
          tempWordRect.set(rawBounds)
          trimAwayMedallions(tempWordRect, medallionRects)
          if (tempWordRect.width() <= 0.5f || tempWordRect.height() <= 0.5f) continue
          val mapped = RectF()
          image.imageMatrix.mapRect(mapped, tempWordRect)
          mapped.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
          mergeIntoSameLineRects(mappedRects, mapped)
        }
        if (mappedRects.isEmpty()) continue

        val isRevealed = wordIdx < revealedCount
        val isCurrentTarget = wordIdx == revealedCount

        fillPaint.color = when {
          isRevealed -> if (isNight) Color.argb(46, 16, 185, 129) else Color.argb(30, 16, 185, 129)
          isNight -> Color.rgb(24, 28, 36)
          else -> Color.rgb(248, 244, 234)
        }
        borderPaint.color = when {
          isCurrentTarget -> Color.rgb(2, 132, 199)
          isRevealed -> Color.argb(175, 5, 150, 105)
          else -> Color.argb(110, 148, 163, 184)
        }
        borderPaint.strokeWidth = (if (isCurrentTarget) 2.2f else 1.2f) * density

        for (rect in mappedRects) {
          pillRect.set(rect)
          pillRect.inset(1.0f * density, 1.0f * density)
          val radius = (6f * density).coerceAtMost(pillRect.height() * 0.26f)
          canvas.drawRoundRect(pillRect, radius, radius, fillPaint)
          canvas.drawRoundRect(pillRect, radius, radius, borderPaint)
        }
      }
      return
    }

    val turn = state.currentTurn ?: return
    val startMushafPos = turn.wordStartIdx + 1
    val endMushafPos = (turn.wordStartIdx + turn.uthmaniWords.size).coerceAtLeast(startMushafPos)

    for (mushafWordPos in startMushafPos..endMushafPos) {
      val wordBoundsList = glyphsCoords.getBoundsForWord(
        targetAyah,
        mushafWordPos,
        expandBounds = true
      ) ?: continue

      val mappedRects = mutableListOf<RectF>()
      for (rawBounds in wordBoundsList) {
        tempWordRect.set(rawBounds)
        trimAwayMedallions(tempWordRect, medallionRects)
        if (tempWordRect.width() <= 0.5f || tempWordRect.height() <= 0.5f) continue

        val mapped = RectF()
        image.imageMatrix.mapRect(mapped, tempWordRect)
        mapped.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
        mergeIntoSameLineRects(mappedRects, mapped)
      }
      if (mappedRects.isEmpty()) continue

      val isActivePlaybackWord = (state.stage == ClassroomStage.QARI_RECITING ||
        state.stage == ClassroomStage.KIDS_REPEATING) &&
        state.activePlaybackWordPosition == mushafWordPos

      val grade = state.wordGradesByMushafPos[mushafWordPos]
      val effectiveStatus = when {
        isActivePlaybackWord -> WordGradeStatus.ACTIVE_PLAYBACK
        grade != null -> grade.status
        else -> WordGradeStatus.PENDING
      }

      val style = resolveVisualStyle(
        status = effectiveStatus,
        stage = state.stage,
        isNight = isNight
      )

      borderPaint.strokeWidth = style.borderWidthDp * density
      borderPaint.color = style.borderColor
      fillPaint.color = style.fillColor
      accentBarPaint.color = style.accentColor

      for ((lineIdx, rect) in mappedRects.withIndex()) {
        val insetX = (1.5f * density).coerceAtMost(rect.width() * 0.04f)
        val insetY = (1.5f * density).coerceAtMost(rect.height() * 0.05f)
        pillRect.set(rect)
        pillRect.inset(insetX, insetY)
        val radius = (6f * density).coerceAtMost(pillRect.height() * 0.26f)

        canvas.drawRoundRect(pillRect, radius, radius, fillPaint)
        canvas.drawRoundRect(pillRect, radius, radius, borderPaint)

        val barH = (2.4f * density).coerceAtMost(pillRect.height() * 0.14f)
        val barInset = (radius * 0.5f).coerceAtMost(pillRect.width() * 0.18f)
        if (pillRect.width() > barInset * 2f + 2f && barH >= 1f) {
          barRect.set(
            pillRect.left + barInset,
            pillRect.bottom - barH,
            pillRect.right - barInset,
            pillRect.bottom
          )
          canvas.drawRoundRect(barRect, barH * 0.5f, barH * 0.5f, accentBarPaint)
        }

        // Draw compact floating badge on the first line segment of graded words
        if (lineIdx == 0 && style.badgeLabel != null) {
          badgeTextPaint.textSize = (9.5f * density).coerceAtMost(pillRect.height() * 0.34f)
          badgeTextPaint.color = Color.WHITE
          badgeBgPaint.color = style.badgeBgColor

          val textW = badgeTextPaint.measureText(style.badgeLabel)
          val padH = 4.5f * density
          val badgeW = (textW + padH * 2f).coerceAtLeast(14f * density)
          val badgeH = (13.5f * density).coerceAtMost(pillRect.height() * 0.45f)
          val cx = pillRect.centerX()
          val badgeTop = (pillRect.top - badgeH * 0.55f).coerceAtLeast(2f * density)

          badgeRect.set(
            cx - badgeW * 0.5f,
            badgeTop,
            cx + badgeW * 0.5f,
            badgeTop + badgeH
          )
          val badgeR = badgeH * 0.5f
          canvas.drawRoundRect(badgeRect, badgeR, badgeR, badgeBgPaint)
          val fm = badgeTextPaint.fontMetrics
          val textBaseline = badgeRect.centerY() - (fm.ascent + fm.descent) * 0.5f
          canvas.drawText(style.badgeLabel, badgeRect.centerX(), textBaseline, badgeTextPaint)
        }
      }
    }
  }

  private data class WordVisualStyle(
    val fillColor: Int,
    val borderColor: Int,
    val accentColor: Int,
    val borderWidthDp: Float,
    val badgeLabel: String?,
    val badgeBgColor: Int
  )

  private fun resolveVisualStyle(
    status: WordGradeStatus,
    stage: ClassroomStage,
    isNight: Boolean
  ): WordVisualStyle {
    return when (status) {
      WordGradeStatus.ACTIVE_PLAYBACK -> {
        if (stage == ClassroomStage.KIDS_REPEATING) {
          // Warm Gold / Amber spotlight during Kids Repeat
          WordVisualStyle(
            fillColor = if (isNight) Color.argb(64, 245, 158, 11) else Color.argb(46, 245, 158, 11),
            borderColor = Color.argb(215, 217, 119, 6),
            accentColor = Color.argb(240, 217, 119, 6),
            borderWidthDp = 1.9f,
            badgeLabel = "🧒",
            badgeBgColor = Color.rgb(217, 119, 6)
          )
        } else {
          // Teal spotlight during Sheikh Al-Minshawi Recites
          WordVisualStyle(
            fillColor = if (isNight) Color.argb(64, 13, 148, 136) else Color.argb(44, 13, 148, 136),
            borderColor = Color.argb(215, 13, 148, 136),
            accentColor = Color.argb(240, 13, 148, 136),
            borderWidthDp = 1.9f,
            badgeLabel = null,
            badgeBgColor = Color.rgb(13, 148, 136)
          )
        }
      }
      WordGradeStatus.CORRECT -> WordVisualStyle(
        fillColor = if (isNight) Color.argb(62, 16, 185, 129) else Color.argb(42, 16, 185, 129),
        borderColor = Color.argb(215, 5, 150, 105),
        accentColor = Color.argb(245, 5, 150, 105),
        borderWidthDp = 1.7f,
        badgeLabel = "✓",
        badgeBgColor = Color.rgb(5, 150, 105)
      )
      WordGradeStatus.TASHKEEL_ERROR -> WordVisualStyle(
        fillColor = if (isNight) Color.argb(72, 245, 158, 11) else Color.argb(52, 245, 158, 11),
        borderColor = Color.argb(230, 217, 119, 6),
        accentColor = Color.argb(250, 217, 119, 6),
        borderWidthDp = 2.0f,
        badgeLabel = "حركة",
        badgeBgColor = Color.rgb(217, 119, 6)
      )
      WordGradeStatus.WORD_ERROR -> WordVisualStyle(
        fillColor = if (isNight) Color.argb(74, 239, 68, 68) else Color.argb(50, 239, 68, 68),
        borderColor = Color.argb(230, 220, 38, 38),
        accentColor = Color.argb(250, 220, 38, 38),
        borderWidthDp = 2.0f,
        badgeLabel = "خطأ",
        badgeBgColor = Color.rgb(220, 38, 38)
      )
      WordGradeStatus.MISSING -> WordVisualStyle(
        fillColor = if (isNight) Color.argb(68, 185, 28, 28) else Color.argb(44, 185, 28, 28),
        borderColor = Color.argb(210, 153, 27, 27),
        accentColor = Color.argb(235, 153, 27, 27),
        borderWidthDp = 1.8f,
        badgeLabel = "نسيان",
        badgeBgColor = Color.rgb(153, 27, 27)
      )
      WordGradeStatus.PENDING -> {
        val isChildReciting = stage == ClassroomStage.CHILD_RECITING
        WordVisualStyle(
          fillColor = if (isChildReciting) {
            if (isNight) Color.argb(38, 56, 189, 248) else Color.argb(26, 14, 165, 233)
          } else {
            if (isNight) Color.argb(24, 148, 163, 184) else Color.argb(18, 13, 148, 136)
          },
          borderColor = if (isChildReciting) {
            Color.argb(150, 2, 132, 199)
          } else {
            Color.argb(95, 13, 148, 136)
          },
          accentColor = if (isChildReciting) {
            Color.argb(180, 2, 132, 199)
          } else {
            Color.argb(115, 13, 148, 136)
          },
          borderWidthDp = 1.2f,
          badgeLabel = null,
          badgeBgColor = Color.TRANSPARENT
        )
      }
    }
  }

  private fun mergeIntoSameLineRects(targetList: MutableList<RectF>, candidate: RectF) {
    for (existing in targetList) {
      val maxH = max(existing.height(), candidate.height())
      if (abs(existing.centerY() - candidate.centerY()) <= maxH * 0.65f) {
        existing.union(candidate)
        return
      }
    }
    targetList.add(candidate)
  }

  private fun trimAwayMedallions(wordRect: RectF, medallionRects: List<RectF>) {
    for (medallion in medallionRects) {
      val cy = medallion.centerY()
      if (cy >= wordRect.top && cy <= wordRect.bottom && RectF.intersects(wordRect, medallion)) {
        if (medallion.centerX() <= wordRect.centerX()) {
          wordRect.left = max(wordRect.left, medallion.right + 1f)
        } else {
          wordRect.right = kotlin.math.min(wordRect.right, medallion.left - 1f)
        }
      }
    }
  }
}
