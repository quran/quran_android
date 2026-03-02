package com.quran.labs.androidquran.test.fakes

import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import io.reactivex.rxjava3.core.Observable

/**
 * Fake implementation of CoordinatesModel for testing.
 *
 * Pattern: Configurable observables
 *
 * Usage:
 * ```
 * val fakeModel = FakeCoordinatesModel()
 * fakeModel.setPageCoordinatesResponse(Observable.just(mockCoordinates))
 * fakeModel.setAyahCoordinatesResponse(Observable.just(mockAyahCoordinates))
 *
 * // Use in presenter
 * val presenter = QuranPagePresenter(fakeModel, ...)
 * ```
 */
class FakeCoordinatesModel {

  private var pageCoordinatesResponse: Observable<PageCoordinates> = Observable.empty()
  private val ayahCoordinatesResponses = mutableMapOf<Int, Observable<AyahCoordinates>>()
  private var defaultAyahCoordinatesResponse: Observable<AyahCoordinates> = Observable.empty()

  private val pageCoordinatesCalls = mutableListOf<PageCoordinatesCall>()
  private val ayahCoordinatesCalls = mutableListOf<Int>()

  data class PageCoordinatesCall(val wantPageBounds: Boolean, val pages: IntArray) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as PageCoordinatesCall

      if (wantPageBounds != other.wantPageBounds) return false
      if (!pages.contentEquals(other.pages)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = wantPageBounds.hashCode()
      result = 31 * result + pages.contentHashCode()
      return result
    }
  }

  fun getPageCoordinates(wantPageBounds: Boolean, vararg pages: Int): Observable<PageCoordinates> {
    pageCoordinatesCalls.add(PageCoordinatesCall(wantPageBounds, pages))
    return pageCoordinatesResponse
  }

  fun getAyahCoordinates(vararg pages: Int): Observable<AyahCoordinates> {
    pages.forEach { page ->
      ayahCoordinatesCalls.add(page)
    }

    // Return specific response for each page if configured
    return if (pages.size == 1 && ayahCoordinatesResponses.containsKey(pages[0])) {
      ayahCoordinatesResponses[pages[0]]!!
    } else {
      defaultAyahCoordinatesResponse
    }
  }

  // Configuration methods
  fun setPageCoordinatesResponse(response: Observable<PageCoordinates>) {
    pageCoordinatesResponse = response
  }

  fun setAyahCoordinatesResponse(response: Observable<AyahCoordinates>) {
    defaultAyahCoordinatesResponse = response
  }

  fun setAyahCoordinatesResponseForPage(page: Int, response: Observable<AyahCoordinates>) {
    ayahCoordinatesResponses[page] = response
  }

  fun setPageCoordinatesError(error: Throwable) {
    pageCoordinatesResponse = Observable.error(error)
  }

  fun setAyahCoordinatesError(error: Throwable) {
    defaultAyahCoordinatesResponse = Observable.error(error)
  }

  // Assertion helpers
  fun assertGetPageCoordinatesCalled(wantPageBounds: Boolean, vararg pages: Int) {
    val expected = PageCoordinatesCall(wantPageBounds, pages)
    require(pageCoordinatesCalls.any { it == expected }) {
      "Expected getPageCoordinates($wantPageBounds, ${pages.toList()}) but was called with: $pageCoordinatesCalls"
    }
  }

  fun assertGetAyahCoordinatesCalled(page: Int) {
    require(ayahCoordinatesCalls.contains(page)) {
      "Expected getAyahCoordinates($page) but was called with: $ayahCoordinatesCalls"
    }
  }

  // Query helpers
  fun getPageCoordinatesCallCount(): Int = pageCoordinatesCalls.size

  fun getAyahCoordinatesCallCount(): Int = ayahCoordinatesCalls.size

  fun getLastPageCoordinatesCall(): PageCoordinatesCall? = pageCoordinatesCalls.lastOrNull()

  fun getAllAyahCoordinatesCalls(): List<Int> = ayahCoordinatesCalls.toList()

  // Reset for test isolation
  fun reset() {
    pageCoordinatesCalls.clear()
    ayahCoordinatesCalls.clear()
    pageCoordinatesResponse = Observable.empty()
    defaultAyahCoordinatesResponse = Observable.empty()
    ayahCoordinatesResponses.clear()
  }
}
