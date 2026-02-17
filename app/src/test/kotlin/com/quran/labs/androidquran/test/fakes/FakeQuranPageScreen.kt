package com.quran.labs.androidquran.test.fakes

import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.quran.labs.androidquran.presenter.quran.QuranPageScreen
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates

/**
 * Fake implementation of QuranPageScreen for testing.
 *
 * Pattern: Call tracking with assertion helpers
 *
 * Usage:
 * ```
 * val fakeScreen = FakeQuranPageScreen()
 * presenter.bind(fakeScreen)
 *
 * // Assert methods were called
 * fakeScreen.assertSetPageCoordinatesCalled(expectedCoordinates)
 * fakeScreen.assertSetAyahCoordinatesErrorCalled()
 * ```
 */
class FakeQuranPageScreen : QuranPageScreen {

  private val pageCoordinatesCalls = mutableListOf<PageCoordinates>()
  private val ayahCoordinatesDataCalls = mutableListOf<AyahCoordinates>()
  private val ayahCoordinatesErrorCalls = mutableListOf<Unit>()
  private val hidePageDownloadErrorCalls = mutableListOf<Unit>()
  private val pageBitmapCalls = mutableListOf<PageBitmapCall>()
  private val pageDownloadErrorCalls = mutableListOf<Int>()

  data class PageBitmapCall(val page: Int, val bitmap: Bitmap)

  override fun setPageCoordinates(pageCoordinates: PageCoordinates) {
    pageCoordinatesCalls.add(pageCoordinates)
  }

  override fun setAyahCoordinatesData(coordinates: AyahCoordinates) {
    ayahCoordinatesDataCalls.add(coordinates)
  }

  override fun setAyahCoordinatesError() {
    ayahCoordinatesErrorCalls.add(Unit)
  }

  override fun hidePageDownloadError() {
    hidePageDownloadErrorCalls.add(Unit)
  }

  override fun setPageBitmap(page: Int, pageBitmap: Bitmap) {
    pageBitmapCalls.add(PageBitmapCall(page, pageBitmap))
  }

  override fun setPageDownloadError(@StringRes errorMessage: Int) {
    pageDownloadErrorCalls.add(errorMessage)
  }

  // Assertion helpers
  fun assertSetPageCoordinatesCalled(expected: PageCoordinates) {
    require(pageCoordinatesCalls.contains(expected)) {
      "Expected setPageCoordinates($expected) but was called with: $pageCoordinatesCalls"
    }
  }

  fun assertSetPageCoordinatesCalledOnce() {
    require(pageCoordinatesCalls.size == 1) {
      "Expected setPageCoordinates called once but was called ${pageCoordinatesCalls.size} times"
    }
  }

  fun assertSetAyahCoordinatesDataCalled(expected: AyahCoordinates) {
    require(ayahCoordinatesDataCalls.contains(expected)) {
      "Expected setAyahCoordinatesData($expected) but was called with: $ayahCoordinatesDataCalls"
    }
  }

  fun assertSetAyahCoordinatesErrorCalled() {
    require(ayahCoordinatesErrorCalls.isNotEmpty()) {
      "Expected setAyahCoordinatesError() to be called but it wasn't"
    }
  }

  fun assertHidePageDownloadErrorCalled() {
    require(hidePageDownloadErrorCalls.isNotEmpty()) {
      "Expected hidePageDownloadError() to be called but it wasn't"
    }
  }

  fun assertSetPageBitmapCalled(page: Int) {
    require(pageBitmapCalls.any { it.page == page }) {
      "Expected setPageBitmap($page, ...) but was called with pages: ${pageBitmapCalls.map { it.page }}"
    }
  }

  fun assertSetPageDownloadErrorCalled(errorRes: Int) {
    require(pageDownloadErrorCalls.contains(errorRes)) {
      "Expected setPageDownloadError($errorRes) but was called with: $pageDownloadErrorCalls"
    }
  }

  // Query helpers
  fun getLastPageCoordinates(): PageCoordinates? = pageCoordinatesCalls.lastOrNull()

  fun getLastAyahCoordinatesData(): AyahCoordinates? = ayahCoordinatesDataCalls.lastOrNull()

  fun getPageCoordinatesCallCount(): Int = pageCoordinatesCalls.size

  fun getAyahCoordinatesDataCallCount(): Int = ayahCoordinatesDataCalls.size

  fun wasSetAyahCoordinatesErrorCalled(): Boolean = ayahCoordinatesErrorCalls.isNotEmpty()

  fun wasHidePageDownloadErrorCalled(): Boolean = hidePageDownloadErrorCalls.isNotEmpty()

  // Reset for test isolation
  fun reset() {
    pageCoordinatesCalls.clear()
    ayahCoordinatesDataCalls.clear()
    ayahCoordinatesErrorCalls.clear()
    hidePageDownloadErrorCalls.clear()
    pageBitmapCalls.clear()
    pageDownloadErrorCalls.clear()
  }
}
