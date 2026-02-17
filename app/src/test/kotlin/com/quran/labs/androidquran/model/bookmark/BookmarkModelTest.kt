package com.quran.labs.androidquran.model.bookmark

import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.bookmark.Tag
import com.quran.labs.awaitTerminalEvent
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.mobile.bookmark.Bookmarks
import com.quran.mobile.bookmark.Last_pages
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.test.RxSchedulerRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for BookmarkModel using in-memory SQLite database.
 * Migrated from Mockito to real database implementation.
 *
 * Uses RobolectricTestRunner so that the SQLite JDBC driver loads in the same sandbox
 * classloader as other Robolectric tests, preventing DriverManager classloader conflicts
 * when the full test suite runs.
 */
@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class BookmarkModelTest {

  @get:Rule
  val rxRule = RxSchedulerRule()

  private lateinit var database: BookmarksDatabase
  private lateinit var bookmarksAdapter: BookmarksDBAdapter
  private lateinit var recentPageModel: RecentPageModel
  private lateinit var model: BookmarkModel

  @Before
  fun setupTest() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BookmarksDatabase.Schema.create(driver)
    database = BookmarksDatabase(
      driver,
      Bookmarks.Adapter(IntColumnAdapter, IntColumnAdapter, IntColumnAdapter),
      Last_pages.Adapter(IntColumnAdapter)
    )
    bookmarksAdapter = BookmarksDBAdapter(database)
    recentPageModel = RecentPageModel(bookmarksAdapter)
    model = BookmarkModel(bookmarksAdapter, recentPageModel)
  }

  @Test
  fun testUpdateTag() {
    // Setup: Create a tag first
    val tagId = bookmarksAdapter.addTag("Original Tag")
    val tag = Tag(tagId, "Updated Tag")

    // Execute: Update the tag
    val testObserver = model.updateTag(tag).test()
    testObserver.awaitTerminalEvent()

    // Verify: No errors and operation completed
    testObserver.assertNoErrors()
    testObserver.assertComplete()

    // Verify: Tag was actually updated in database
    val allTags = bookmarksAdapter.getTags()
    val updatedTag = allTags.find { it.id == tagId }
    assertThat(updatedTag).isNotNull()
    assertThat(updatedTag!!.name).isEqualTo("Updated Tag")
  }

  @Test
  fun testUpdateBookmarkTags() {
    // Setup: Create a bookmark and a tag
    val bookmarkId = bookmarksAdapter.addBookmark(null, null, 1)
    val tagId = bookmarksAdapter.addTag("Test Tag")
    val tags = setOf(tagId)

    // Execute: Tag the bookmark
    val testObserver = model.updateBookmarkTags(longArrayOf(bookmarkId), tags, false).test()
    testObserver.awaitTerminalEvent()

    // Verify: No errors and operation completed successfully
    testObserver.assertNoErrors()
    testObserver.assertComplete()
    testObserver.assertValue(true)

    // Verify: Bookmark is actually tagged in database
    val bookmarkTags = bookmarksAdapter.getBookmarkTagIds(bookmarkId)
    assertThat(bookmarkTags).contains(tagId)
  }
}
