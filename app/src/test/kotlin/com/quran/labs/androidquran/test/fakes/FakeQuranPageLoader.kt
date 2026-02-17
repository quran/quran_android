package com.quran.labs.androidquran.test.fakes

import com.quran.labs.androidquran.common.Response
import io.reactivex.rxjava3.core.Observable

/**
 * Fake implementation of QuranPageLoader for testing.
 *
 * Pattern: Configurable responses
 *
 * Usage:
 * ```
 * val fakeLoader = FakeQuranPageLoader()
 * fakeLoader.setLoadPagesResponse(Observable.just(mockResponse))
 *
 * // Use in presenter
 * val presenter = QuranPagePresenter(..., fakeLoader, ...)
 * ```
 */
class FakeQuranPageLoader {

  private var loadPagesResponse: Observable<Response> = Observable.empty()
  private val loadPagesCalls = mutableListOf<Array<Int>>()

  fun loadPages(pages: Array<Int>): Observable<Response> {
    loadPagesCalls.add(pages)
    return loadPagesResponse
  }

  // Configuration methods
  fun setLoadPagesResponse(response: Observable<Response>) {
    loadPagesResponse = response
  }

  fun setLoadPagesError(error: Throwable) {
    loadPagesResponse = Observable.error(error)
  }

  fun setLoadPagesSuccess(response: Response) {
    loadPagesResponse = Observable.just(response)
  }

  fun setLoadPagesEmpty() {
    loadPagesResponse = Observable.empty()
  }

  // Assertion helpers
  fun assertLoadPagesCalled(vararg pages: Int) {
    val expected = pages.toList()
    require(loadPagesCalls.any { it.toList() == expected }) {
      "Expected loadPages(${pages.toList()}) but was called with: ${loadPagesCalls.map { it.toList() }}"
    }
  }

  fun assertLoadPagesCalledOnce() {
    require(loadPagesCalls.size == 1) {
      "Expected loadPages called once but was called ${loadPagesCalls.size} times"
    }
  }

  fun assertLoadPagesNotCalled() {
    require(loadPagesCalls.isEmpty()) {
      "Expected loadPages not to be called but it was called ${loadPagesCalls.size} times"
    }
  }

  // Query helpers
  fun getLoadPagesCallCount(): Int = loadPagesCalls.size

  fun getLastLoadPagesCall(): Array<Int>? = loadPagesCalls.lastOrNull()

  fun getAllLoadPagesCalls(): List<Array<Int>> = loadPagesCalls.toList()

  fun wasLoadPagesCalled(): Boolean = loadPagesCalls.isNotEmpty()

  // Reset for test isolation
  fun reset() {
    loadPagesCalls.clear()
    loadPagesResponse = Observable.empty()
  }
}
