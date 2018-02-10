package com.quran.data.page.provider.impl

import android.graphics.Point
import android.os.Build
import android.view.Display
import com.quran.data.page.provider.PageProvider

internal class NaskhPageProvider(display: Display) : PageProvider {
  private val baseUrl = "http://android.quran.com/data"
  private val baseNakshUrl = "$baseUrl/naskh"
  private val screenRatios = doubleArrayOf(4.0 / 3.0, 16.0 / 10.0, 5.0 / 3.0, 16.0 / 9.0)

  private val ratioIndex: Int

  init {
    val point = Point()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      display.getRealSize(point)
    } else {
      try {
        // getRealSize was actually present since 4.0, but was annotated @hide.
        Display::class.java.getMethod("getRealSize", Point::class.java).invoke(display, point)
      } catch (e: Exception) {
        display.getSize(point)
      }
    }
    ratioIndex = getScreenRatioIndex(point.x, point.y)
  }

  override fun getImageVersion() = 1

  override fun getImagesBaseUrl() = "$baseNakshUrl/"

  override fun getImagesZipBaseUrl() = "$baseNakshUrl/zips/"

  override fun getPatchBaseUrl() = "$baseNakshUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$baseNakshUrl/databases/ayahinfo/"

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() = getDatabasesBaseUrl() + "audio/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "naskh/" + getDatabaseDirectoryName()

  override fun getImagesDirectoryName() = "naskh"

  private fun getScreenRatioIndex(width: Int, height: Int): Int {
    var aspectRatio = height.toDouble() / width
    if (aspectRatio < 1) {
      // getRealSize "size is adjusted based on the current rotation of the display"
      aspectRatio = 1.0 / aspectRatio
    }

    // pick the closest
    var closest = 0 //keeps the id of the array
    var minDelta = aspectRatio

    for (i in screenRatios.indices) {
      val difference = Math.abs(aspectRatio - screenRatios[i])
      if (difference < minDelta) {
        closest = i
        minDelta = difference
      } else {
        // once minDelta > difference, the difference will only grow since
        // screen ratios is incremental.
        break
      }
    }
    return closest
  }

  override fun getWidthParameter(): String {
    return when (ratioIndex) {
      0 -> { "1536" } // 4:3
      1 -> { "1280" } // 16:10
      2 -> { "1227" } // 5:3
      3 -> { "1152" } // 16:9 and fallback
      else -> { "1152" }
    }
  }

  override fun getTabletWidthParameter(): String {
    // use the same size for tablet landscape
    return getWidthParameter()
  }

  override fun setOverrideParameter(parameter: String) {
    // override parameter is irrelevant for naskh pages
  }
}
