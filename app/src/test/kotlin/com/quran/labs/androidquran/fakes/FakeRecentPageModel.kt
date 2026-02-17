package com.quran.labs.androidquran.fakes

import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.quran.data.model.bookmark.RecentPage
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.labs.androidquran.model.bookmark.RecentPageModel
import com.quran.mobile.bookmark.Bookmarks
import com.quran.mobile.bookmark.Last_pages
import io.reactivex.rxjava3.core.Single

private fun inMemoryBookmarksAdapter(): BookmarksDBAdapter {
  val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  BookmarksDatabase.Schema.create(driver)
  val database = BookmarksDatabase(
    driver,
    Bookmarks.Adapter(IntColumnAdapter, IntColumnAdapter, IntColumnAdapter),
    Last_pages.Adapter(IntColumnAdapter)
  )
  return BookmarksDBAdapter(database)
}

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
class FakeRecentPageModel : RecentPageModel(inMemoryBookmarksAdapter()) {

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
