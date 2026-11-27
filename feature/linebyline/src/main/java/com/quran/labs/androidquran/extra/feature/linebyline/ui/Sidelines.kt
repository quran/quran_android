package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.quran.labs.androidquran.extra.feature.linebyline.model.SidelineDirection
import com.quran.labs.androidquran.extra.feature.linebyline.model.SidelineModel
import com.quran.labs.androidquran.extra.feature.linebyline.model.SidelinesPosition
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun Sidelines(
  sidelines: List<SidelineModel>,
  quranImageLineHeight: Int,
  colorFilter: ColorFilter,
  position: SidelinesPosition
) {
  val sortedSidelines = remember(sidelines) {
    var isSorted = true
    for (i in 1 until sidelines.size) {
      if (sidelines[i].targetLine < sidelines[i - 1].targetLine) {
        isSorted = false
        break
      }
    }
    if (isSorted) sidelines else sidelines.sortedBy { it.targetLine }
  }
  val locationsBuffer = remember(sortedSidelines) { FloatArray(sortedSidelines.size * 2) }

  Canvas(modifier = Modifier.fillMaxSize()) {
    val totalWidth = this.size.width
    val totalHeight = this.size.height

    val lineHeight = totalHeight / 15f
    val count = sortedSidelines.size
    for (i in 0 until count) {
      val item = sortedSidelines[i]
      val targetLineTop = lineHeight * (item.targetLine - 1)
      val y = if (item.direction == SidelineDirection.UP) {
        val potentialY = targetLineTop + lineHeight
        max(0f, potentialY - item.image.height)
      } else {
        targetLineTop
      }
      locationsBuffer[i * 2] = y
      locationsBuffer[i * 2 + 1] = y + item.image.height
    }

    for (index in 0 until count) {
      val model = sortedSidelines[index]
      val locStart = locationsBuffer[index * 2]
      val locEnd = locationsBuffer[index * 2 + 1]
      val hasNextOverlap = index + 1 < count && locationsBuffer[(index + 1) * 2] < locEnd

      val size =
        if (hasNextOverlap) {
          val originalLinesSpanned = ceil(1f * model.image.height / (1.35 * quranImageLineHeight)).toInt()
          val nextUsedLine = if (model.direction == SidelineDirection.UP) {
            var prev = index - 1
            while (prev >= 0 && sortedSidelines[prev].targetLine == model.targetLine) {
              prev--
            }
            (if (prev >= 0) sortedSidelines[prev].targetLine else 1) - 1
          } else {
            var next = index + 1
            while (next < count && sortedSidelines[next].targetLine == model.targetLine) {
              next++
            }
            (if (next < count) sortedSidelines[next].targetLine else 16) - 1
          }
          val targetLinesToSpan = max(originalLinesSpanned, abs(nextUsedLine - model.targetLine))
          val targetHeight = targetLinesToSpan * lineHeight
          if (model.image.height - targetHeight < 25) {
            // too small of a delta, keep it as it is and pad it some
            IntSize(model.image.width, model.image.height)
          } else {
            IntSize(
              ((targetHeight / model.image.height) * model.image.width).toInt(),
              targetHeight.toInt()
            )
          }
        } else if (model.image.width > totalWidth) {
          IntSize(totalWidth.toInt(), (totalWidth / model.image.width * model.image.height).toInt())
        } else {
          // instead of just using the original image size, let's take the average between
          // what the original size would have been and the size it wants to be now. this
          // fixes some issues with the sidelines being way too big (ex on 1280x720 screens).
          val originalLinesSpanned = ceil(1f * model.image.height / (1.35 * quranImageLineHeight)).toInt()
          val originalTargetHeight = originalLinesSpanned * lineHeight
          val targetHeight = abs(model.image.height + originalTargetHeight) / 2.0f
          IntSize(
            ((targetHeight / model.image.height) * model.image.width).toInt(),
            targetHeight.toInt()
          )
        }

      val y = if (index + 1 < count && locationsBuffer[(index + 1) * 2] < (locStart + size.height)) {
        val updatedY = locStart + size.height
        locStart - (updatedY - locationsBuffer[(index + 1) * 2])
      } else if (locStart + size.height > totalHeight) {
        locStart - ((locStart + size.height) - totalHeight.toInt())
      } else {
        locStart
      }

      // deltaX is intended to align sidelines to hug the page
      val deltaX = if (position == SidelinesPosition.LEFT) {
        totalWidth - size.width
      } else {
        0
      }

      drawImage(
        image = model.image,
        dstOffset = IntOffset(deltaX.toInt(), y.toInt()),
        dstSize = size,
        colorFilter = colorFilter
      )
    }
  }
}
