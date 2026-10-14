package com.quran.labs.androidquran.fakes

import android.graphics.Bitmap
import com.quran.labs.androidquran.presenter.quran.QuranPageScreen
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates

class FakeQuranPageScreen : QuranPageScreen {
  val pageCoordinatesSet: MutableList<PageCoordinates> = mutableListOf()
  var ayahCoordinatesErrorCalled: Boolean = false
  var hidePageDownloadErrorCalled: Boolean = false
  val ayahCoordinatesDataSet: MutableList<AyahCoordinates> = mutableListOf()
  val pageBitmapsSet: MutableMap<Int, Bitmap> = mutableMapOf()
  val pageDownloadErrors: MutableList<Int> = mutableListOf()

  override fun setPageCoordinates(pageCoordinates: PageCoordinates) {
    pageCoordinatesSet.add(pageCoordinates)
  }

  override fun setAyahCoordinatesError() {
    ayahCoordinatesErrorCalled = true
  }

  override fun hidePageDownloadError() {
    hidePageDownloadErrorCalled = true
  }

  override fun setAyahCoordinatesData(coordinates: AyahCoordinates) {
    ayahCoordinatesDataSet.add(coordinates)
  }

  override fun setPageBitmap(page: Int, pageBitmap: Bitmap) {
    pageBitmapsSet[page] = pageBitmap
  }

  override fun setPageDownloadError(errorMessage: Int) {
    pageDownloadErrors.add(errorMessage)
  }
}
