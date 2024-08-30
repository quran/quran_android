package com.quran.labs.androidquran.util

import android.content.Context
import android.graphics.Point
import android.view.Display
import com.quran.data.source.PageSizeCalculator
import com.quran.mobile.di.qualifier.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.max

class QuranScreenInfo @Inject constructor(
  @ApplicationContext appContext: Context,
  display: Display,
  pageSizeCalculator: PageSizeCalculator
) {
  private val height: Int
  private val altDimension: Int
  private val maxWidth: Int
  private val orientation: Int
  private val context: Context
  private val pageSizeCalculator: PageSizeCalculator

  init {
    val point = Point()
    display.getSize(point)

    height = point.y
    altDimension = point.x
    maxWidth = max(point.x.toDouble(), point.y.toDouble()).toInt()
    orientation = appContext.resources.configuration.orientation

    this.context = appContext
    this.pageSizeCalculator = pageSizeCalculator
    Timber.d("initializing with %d and %d", point.y, point.x)
  }

  fun getHeight(): Int {
    return if (orientation == context.resources.configuration.orientation) {
      height
    } else {
      altDimension
    }
  }

  val widthParam: String
    get() {
      pageSizeCalculator.setOverrideParameter(
        QuranSettings.getInstance(context).defaultImagesDirectory
      )
      return "_" + pageSizeCalculator.getWidthParameter()
    }

  val tabletWidthParam: String
    get() {
      pageSizeCalculator.setOverrideParameter(
        QuranSettings.getInstance(context).defaultImagesDirectory
      )
      return "_" + pageSizeCalculator.getTabletWidthParameter()
    }

  val isDualPageMode: Boolean
    get() = maxWidth > 960
}
