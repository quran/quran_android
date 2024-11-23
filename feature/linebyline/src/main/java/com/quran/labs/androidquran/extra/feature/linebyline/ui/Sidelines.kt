package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
  Canvas(modifier = Modifier.fillMaxSize()) {
    val totalWidth = this.size.width
    val totalHeight = this.size.height

    val sortedSidelines = sidelines.sortedBy { it.targetLine }

    val lineHeight = totalHeight / 15f
    val locations = sortedSidelines.map {
      val targetLineTop = lineHeight * (it.targetLine - 1)
      val y = if (it.direction == SidelineDirection.UP) {
        val potentialY = targetLineTop + lineHeight
        max(0f, potentialY - it.image.height)
      } else {
        targetLineTop
      }
      y to y + it.image.height
    }

    sortedSidelines.forEachIndexed { index, model ->
      val whence = locations[index]
      val size =
        if (locations.size > index + 1 && locations[index + 1].first < locations[index].second) {
          val originalLinesSpanned = ceil(1f * model.image.height / (1.35 * quranImageLineHeight)).toInt()
          val nextUsedLine = if (model.direction == SidelineDirection.UP) {
            (sidelines.filter { it.targetLine < model.targetLine }
              .maxByOrNull { it.targetLine }?.targetLine ?: 1) - 1
          } else {
            (sidelines.filter { it.targetLine > model.targetLine }
              .minByOrNull { it.targetLine }?.targetLine ?: 16) - 1
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

      val y = if (locations.size > index + 1 && locations[index + 1].first < (locations[index].first + size.height)) {
        val updatedY = locations[index].first + size.height
        whence.first - (updatedY - locations[index + 1].first)
      } else if (whence.first + size.height > totalHeight) {
        whence.first - ((whence.first + size.height) - totalHeight.toInt())
      } else {
        whence.first
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
