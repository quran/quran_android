package com.quran.labs.androidquran.presenter.quran

import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates

interface QuranPageScreen {
  fun setPageCoordinates(pageCoordinates: PageCoordinates)
  fun setAyahCoordinatesError()
  fun setPageBitmap(page: Int, pageBitmap: Bitmap)
  fun hidePageDownloadError()
  fun setPageDownloadError(@StringRes errorMessage: Int)
  fun setAyahCoordinatesData(coordinates: AyahCoordinates)
}
