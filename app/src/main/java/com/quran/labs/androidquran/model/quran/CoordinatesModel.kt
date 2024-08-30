package com.quran.labs.androidquran.model.quran

import android.graphics.RectF
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.data.AyahInfoDatabaseProvider
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@ActivityScope
class CoordinatesModel @Inject internal constructor(private val ayahInfoDatabaseProvider: AyahInfoDatabaseProvider) {
  fun getPageCoordinates(wantPageBounds: Boolean, vararg pages: Int): Observable<PageCoordinates> {
    val database = ayahInfoDatabaseProvider.getAyahInfoHandler()
      ?: return Observable.error(NoSuchElementException("No AyahInfoDatabaseHandler found!"))

    return Observable.fromArray(*pages.toTypedArray())
      .map { page: Int? ->
        database.getPageInfo(
          page!!, wantPageBounds
        )
      }
      .subscribeOn(Schedulers.computation())
  }

  fun getAyahCoordinates(vararg pages: Int): Observable<AyahCoordinates> {
    val database = ayahInfoDatabaseProvider.getAyahInfoHandler()
      ?: return Observable.error(NoSuchElementException("No AyahInfoDatabaseHandler found!"))

    return Observable.fromArray(*pages.toTypedArray())
      .map { page: Int? ->
        database.getVersesBoundsForPage(
          page!!
        )
      }
      .map { ayahCoordinates: AyahCoordinates ->
        this.normalizePageAyahs(
          ayahCoordinates
        )
      }
      .subscribeOn(Schedulers.computation())
  }

  private fun normalizePageAyahs(ayahCoordinates: AyahCoordinates): AyahCoordinates {
    val original = ayahCoordinates.ayahCoordinates
    val normalizedMap: MutableMap<String, List<AyahBounds>> = HashMap()
    val keys = original.keys
    for (key in keys) {
      val normalBounds = original[key]
      if (normalBounds != null) {
        normalizedMap[key] = normalizeAyahBounds(normalBounds)
      }
    }
    return AyahCoordinates(ayahCoordinates.page, normalizedMap, ayahCoordinates.glyphCoordinates)
  }

  private fun normalizeAyahBounds(ayahBounds: List<AyahBounds>): List<AyahBounds> {
    val total = ayahBounds.size
    if (total < 2) {
      return ayahBounds
    } else if (total < 3) {
      return consolidate(ayahBounds[0], ayahBounds[1])
    } else {
      val middle = ayahBounds[1]
      for (i in 2 until total - 1) {
        middle.engulf(ayahBounds[i])
      }

      var top = consolidate(ayahBounds[0], middle)
      val topSize = top.size
      // the first parameter is essentially middle (after its consolidation with the top line)
      val bottom = consolidate(top[topSize - 1], ayahBounds[total - 1])

      val result: MutableList<AyahBounds> = ArrayList()
      if (topSize == 1) {
        return bottom
      } else if (topSize + bottom.size > 4) {
        // this happens when a verse spans 3 incomplete lines (i.e. starts towards the end of
        // one line, takes one or more whole lines, and ends early on in the line). in this case,
        // just remove the duplicates.

        // add the first parts of top

        for (i in 0 until topSize - 1) {
          result.add(top[i])
        }

        // resolve the middle part which may overlap with bottom
        val lastTop = top[topSize - 1]
        val firstBottom = bottom[0]
        if (lastTop == firstBottom) {
          // only add one if they're both the same
          result.add(lastTop)
        } else {
          // if one contains the other, add the larger one
          val topRect = lastTop.bounds
          val bottomRect = firstBottom.bounds
          if (topRect.contains(bottomRect)) {
            result.add(lastTop)
          } else if (bottomRect.contains(topRect)) {
            result.add(firstBottom)
          } else {
            // otherwise add both
            result.add(lastTop)
            result.add(firstBottom)
          }
        }

        // add everything except the first bottom entry
        var i = 1
        val size = bottom.size
        while (i < size) {
          result.add(bottom[i])
          i++
        }
        return result
      } else {
        // re-consolidate top and middle again, since middle may have changed
        top = consolidate(top[0], bottom[0])
        result.addAll(top)
        if (bottom.size > 1) {
          result.add(bottom[1])
        }
        return result
      }
    }
  }

  private fun consolidate(top: AyahBounds, bottom: AyahBounds): List<AyahBounds> {
    var top = top
    var bottom = bottom
    val firstRect = top.bounds
    val lastRect = bottom.bounds

    var middle: AyahBounds? = null

    // only 2 lines - let's see if any of them are full lines
    val pageWidth = ayahInfoDatabaseProvider.getPageWidth()
    val threshold = (THRESHOLD_PERCENTAGE * pageWidth).toInt()

    val firstIsFullLine = abs((firstRect.right - lastRect.right).toDouble()) < threshold
    val secondIsFullLine = abs((firstRect.left - lastRect.left).toDouble()) < threshold
    if (firstIsFullLine && secondIsFullLine) {
      top.engulf(bottom)
      return listOf(top)
    } else if (firstIsFullLine) {
      lastRect.top = firstRect.bottom
      val bestStartOfLine =
        max(firstRect.right.toDouble(), lastRect.right.toDouble()).toFloat()
      firstRect.right = bestStartOfLine
      lastRect.right = bestStartOfLine

      top = top.withBounds(firstRect)
      bottom = bottom.withBounds(lastRect)
    } else if (secondIsFullLine) {
      firstRect.bottom = lastRect.top
      val bestEndOfLine =
        min(firstRect.left.toDouble(), lastRect.left.toDouble()).toFloat()
      firstRect.left = bestEndOfLine
      lastRect.left = bestEndOfLine

      top = top.withBounds(firstRect)
      bottom = bottom.withBounds(lastRect)
    } else {
      // neither one is a full line, let's generate a middle entry to join them if they have
      // anything in common (i.e. any part of them intersects)
      if (lastRect.left < firstRect.right) {
        val middleBounds = RectF(
          lastRect.left,  /* top= */
          firstRect.bottom,
          min(firstRect.right.toDouble(), lastRect.right.toDouble()).toFloat(),  /* bottom= */
          lastRect.top
        )
        middle = AyahBounds(top.line, top.position, middleBounds)
      }
    }

    val result: MutableList<AyahBounds> = ArrayList()
    result.add(top)
    if (middle != null) {
      result.add(middle)
    }
    result.add(bottom)
    return result
  }

  companion object {
    private const val THRESHOLD_PERCENTAGE = 0.015f
  }
}
