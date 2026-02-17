package com.quran.labs.androidquran.fakes

import com.quran.data.model.bookmark.RecentPage
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.labs.androidquran.model.bookmark.RecentPageModel
import io.reactivex.rxjava3.core.Single
import org.mockito.Mockito.mock

/**
 * Fake implementation of RecentPageModel for testing.
 *
 * This fake provides configurable behavior and tracks method calls for assertions.
 * Since RecentPageModel is open, we can extend it and override open methods.
 *
 * Usage:
 * ```
 * val fake = FakeRecentPageModel()
 * fake.setRecentPages(listOf(RecentPage(42, timestamp)))
 *
 * // Subscribe to observables
 * fake.getRecentPagesObservable().subscribe { pages -> ... }
 *
 * // Assertions
 * fake.assertGetRecentPagesObservableCalled()
 * ```
 */
class FakeRecentPageModel : RecentPageModel(
  // Pass a mock adapter since we override the methods that use it
  mock(BookmarksDBAdapter::class.java)
) {

  // State
  private val recentPages = mutableListOf<RecentPage>()

  // Call tracking
  private val getRecentPagesObservableCalls = mutableListOf<Unit>()

  // Configuration methods
  fun setRecentPages(pages: List<RecentPage>) {
    recentPages.clear()
    recentPages.addAll(pages)
  }

  // Override open methods from RecentPageModel
  override fun getRecentPagesObservable(): Single<List<RecentPage>> {
    getRecentPagesObservableCalls.add(Unit)
    return Single.just(recentPages.toList())
  }

  // Assertion methods
  fun assertGetRecentPagesObservableCalled() {
    if (getRecentPagesObservableCalls.isEmpty()) {
      throw AssertionError("Expected getRecentPagesObservable() but was not called")
    }
  }

  fun getGetRecentPagesObservableCallCount(): Int = getRecentPagesObservableCalls.size

  fun getCurrentRecentPages(): List<RecentPage> = recentPages.toList()

  // Clear methods for test isolation
  fun clearCallHistory() {
    getRecentPagesObservableCalls.clear()
  }

  fun reset() {
    recentPages.clear()
    clearCallHistory()
  }
}
