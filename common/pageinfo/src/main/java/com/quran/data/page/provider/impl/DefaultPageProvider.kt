package com.quran.data.page.provider.impl

import android.graphics.Point
import android.view.Display
import com.quran.data.page.provider.PageProvider

internal open class DefaultPageProvider(display: Display) : PageProvider {

  private val maxWidth: Int
  private var overrideParam: String? = null
  private val baseUrl = "http://android.quran.com/data"

  init {
    val point = Point()
    display.getSize(point)
    maxWidth = if (point.x > point.y) point.x else point.y
  }

  override fun getImageVersion() = 5

  override fun getImageUrl() = "$baseUrl/"

  override fun getImageZipUrl() = "$baseUrl/zips"

  override fun getPatchBaseUrl() = "$baseUrl/patches/v"

  override fun getAyahInfoUrl() = "$baseUrl/databases/ayahinfo/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = getDatabaseDirectoryName()

  override fun getImagesDirectoryName() = ""

  override fun getWidthParameter(): String {
    return when {
      maxWidth <= 320 -> "320"
      maxWidth <= 480 -> "480"
      maxWidth <= 800 -> "800"
      maxWidth <= 1280 -> "1024"
      else -> overrideParam ?: "1260"
    }
  }

  override fun getTabletWidthParameter(): String {
    return if ("1260" == getWidthParameter()) {
      // for tablet, if the width is more than 1280, use 1260
      // images for both dimens (only applies to new installs)
      "1260"
    } else {
      getBestTabletLandscapeSizeMatch(maxWidth / 2)
    }
  }

  override fun setOverrideParameter(parameter: String) {
    if (parameter.isNotBlank()) {
      overrideParam = parameter
    }
  }

  private fun getBestTabletLandscapeSizeMatch(width: Int): String {
    return if (width <= 640) {
      "512"
    } else {
      "1024"
    }
  }
}
