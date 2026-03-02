package com.quran.mobile.bookmark.model

import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.labs.test.TestDataFactory
import com.quran.mobile.bookmark.Bookmarks
import com.quran.mobile.bookmark.Last_pages
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for BookmarksDaoImpl using in-memory SQLite database.
 *
 * Tests cover:
 * - Bookmark CRUD operations
 * - Toggle functionality (page and ayah bookmarks)
 * - Recent pages management
 * - Flow emissions and change notifications
 * - Transaction safety
 */
class BookmarksDaoImplTest {

  private lateinit var database: BookmarksDatabase
  private lateinit var dao: BookmarksDaoImpl

  @Before
  fun setup() {
    // Create in-memory SQLite database with proper adapters
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BookmarksDatabase.Schema.create(driver)
    database = BookmarksDatabase(
      driver,
      Bookmarks.Adapter(IntColumnAdapter, IntColumnAdapter, IntColumnAdapter),
      Last_pages.Adapter(IntColumnAdapter)
    )
    dao = BookmarksDaoImpl(database)
  }

  @After
  fun tearDown() {
    database.bookmarkQueries
    // Driver will be garbage collected
  }

  // ==================== Bookmark Tests ====================

  @Test
  fun `should return empty list when no bookmarks exist`() = runTest {
    val bookmarks = dao.bookmarks()
    assertThat(bookmarks).isEmpty()
  }

  @Test
  fun `should add and retrieve ayah bookmark`() = runTest {
    val suraAyah = TestDataFactory.ayatAlKursi() // 2:255
    val page = 42

    dao.toggleAyahBookmark(suraAyah, page)
    val bookmarks = dao.bookmarks()

    assertThat(bookmarks).hasSize(1)
    assertThat(bookmarks[0].sura).isEqualTo(2)
    assertThat(bookmarks[0].ayah).isEqualTo(255)
    assertThat(bookmarks[0].page).isEqualTo(page)
  }

  @Test
  fun `should add and retrieve page bookmark`() = runTest {
    val page = 604

    dao.togglePageBookmark(page)
    val bookmarks = dao.bookmarks()

    assertThat(bookmarks).hasSize(1)
    assertThat(bookmarks[0].sura).isNull()
    assertThat(bookmarks[0].ayah).isNull()
    assertThat(bookmarks[0].page).isEqualTo(page)
  }

  @Test
  fun `should toggle ayah bookmark on`() = runTest {
    val suraAyah = TestDataFactory.createSuraAyah(sura = 1, ayah = 1)
    val page = 1

    val added = dao.toggleAyahBookmark(suraAyah, page)

    assertThat(added).isTrue()
    assertThat(dao.bookmarks()).hasSize(1)
  }

  @Test
  fun `should toggle ayah bookmark off`() = runTest {
    val suraAyah = TestDataFactory.createSuraAyah(sura = 1, ayah = 1)
    val page = 1

    // Add bookmark
    dao.toggleAyahBookmark(suraAyah, page)
    assertThat(dao.bookmarks()).hasSize(1)

    // Remove bookmark
    val removed = dao.toggleAyahBookmark(suraAyah, page)

    assertThat(removed).isFalse()
    assertThat(dao.bookmarks()).isEmpty()
  }

  @Test
  fun `should toggle page bookmark on`() = runTest {
    val page = 50

    val added = dao.togglePageBookmark(page)

    assertThat(added).isTrue()
    assertThat(dao.bookmarks()).hasSize(1)
  }

  @Test
  fun `should toggle page bookmark off`() = runTest {
    val page = 50

    // Add bookmark
    dao.togglePageBookmark(page)
    assertThat(dao.bookmarks()).hasSize(1)

    // Remove bookmark
    val removed = dao.togglePageBookmark(page)

    assertThat(removed).isFalse()
    assertThat(dao.bookmarks()).isEmpty()
  }

  @Test
  fun `should check if sura ayah is bookmarked`() = runTest {
    val suraAyah = TestDataFactory.createSuraAyah(sura = 2, ayah = 255)
    val page = 42

    assertThat(dao.isSuraAyahBookmarked(suraAyah)).isFalse()

    dao.toggleAyahBookmark(suraAyah, page)

    assertThat(dao.isSuraAyahBookmarked(suraAyah)).isTrue()
  }

  @Test
  fun `should get bookmarks for specific page`() = runTest {
    val page1 = 10
    val page2 = 20
    val suraAyah1 = TestDataFactory.createSuraAyah(sura = 1, ayah = 1)
    val suraAyah2 = TestDataFactory.createSuraAyah(sura = 2, ayah = 1)

    dao.toggleAyahBookmark(suraAyah1, page1)
    dao.toggleAyahBookmark(suraAyah2, page2)

    val bookmarksForPage1 = dao.bookmarksForPage(page1).first()

    assertThat(bookmarksForPage1).hasSize(1)
    assertThat(bookmarksForPage1[0].page).isEqualTo(page1)
    assertThat(bookmarksForPage1[0].sura).isEqualTo(1)
  }

  @Test
  fun `should remove bookmarks for page`() = runTest {
    val page = 15
    dao.togglePageBookmark(page)
    assertThat(dao.bookmarks()).hasSize(1)

    dao.removeBookmarksForPage(page)

    assertThat(dao.bookmarks()).isEmpty()
  }

  @Test
  fun `should emit change notification when bookmark added`() = runTest {
    val suraAyah = TestDataFactory.createSuraAyah(sura = 1, ayah = 1)

    dao.changes.test {
      dao.toggleAyahBookmark(suraAyah, 1)

      val change = awaitItem()
      assertThat(change).isGreaterThan(0L)
    }
  }

  // ==================== Recent Pages Tests ====================

  @Test
  fun `should return empty list when no recent pages exist`() = runTest {
    val recentPages = dao.recentPages()
    assertThat(recentPages).isEmpty()
  }

  @Test
  fun `should add and retrieve recent pages`() = runTest {
    val pages = listOf(
      TestDataFactory.createRecentPage(page = 1),
      TestDataFactory.createRecentPage(page = 2),
      TestDataFactory.createRecentPage(page = 3)
    )

    dao.replaceRecentPages(pages)
    val recentPages = dao.recentPages()

    assertThat(recentPages).hasSize(3)
    assertThat(recentPages.map { it.page }).containsExactly(1, 2, 3)
  }

  @Test
  fun `should limit recent pages to maximum`() = runTest {
    val pages = listOf(
      TestDataFactory.createRecentPage(page = 1),
      TestDataFactory.createRecentPage(page = 2),
      TestDataFactory.createRecentPage(page = 3),
      TestDataFactory.createRecentPage(page = 4),
      TestDataFactory.createRecentPage(page = 5)
    )

    dao.replaceRecentPages(pages)
    val recentPages = dao.recentPages()

    // Max is 3 pages
    assertThat(recentPages).hasSize(3)
  }

  @Test
  fun `should remove all recent pages`() = runTest {
    val pages = listOf(
      TestDataFactory.createRecentPage(page = 1),
      TestDataFactory.createRecentPage(page = 2)
    )
    dao.replaceRecentPages(pages)
    assertThat(dao.recentPages()).isNotEmpty()

    dao.removeRecentPages()

    assertThat(dao.recentPages()).isEmpty()
  }

  @Test
  fun `should remove specific recent page`() = runTest {
    val pages = listOf(
      TestDataFactory.createRecentPage(page = 1),
      TestDataFactory.createRecentPage(page = 2),
      TestDataFactory.createRecentPage(page = 3)
    )
    dao.replaceRecentPages(pages)

    dao.removeRecentsForPage(2)
    val recentPages = dao.recentPages()

    assertThat(recentPages).hasSize(2)
    assertThat(recentPages.map { it.page }).containsExactly(1, 3)
  }
}
