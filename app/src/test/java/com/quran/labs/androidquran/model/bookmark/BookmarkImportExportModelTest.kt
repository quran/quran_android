package com.quran.labs.androidquran.model.bookmark

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.BackupReadingBookmark
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.labs.BaseTestExtension
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeReadingBookmarksDao
import com.quran.labs.androidquran.fakes.FakeRecentPagesDao
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import io.reactivex.rxjava3.observers.TestObserver
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.buffer
import okio.source
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class BookmarkImportExportModelTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private lateinit var bookmarksDao: FakeBookmarksDao
  private lateinit var recentPagesDao: FakeRecentPagesDao
  private lateinit var readingBookmarksDao: FakeReadingBookmarksDao
  private lateinit var quranInfo: QuranInfo
  private lateinit var bookmarkImportExportModel: BookmarkImportExportModel

  @Before
  fun setUp() {
    bookmarksDao = FakeBookmarksDao()
    recentPagesDao = FakeRecentPagesDao()
    readingBookmarksDao = FakeReadingBookmarksDao()
    quranInfo = QuranInfo(MadaniDataSource())
    clearFileProviderCache()
    bookmarkImportExportModel = BookmarkImportExportModel(
      context,
      BookmarkJsonModel(),
      bookmarksDao,
      recentPagesDao,
      readingBookmarksDao,
      quranInfo
    )
  }

  /**
   * FileProvider caches path strategies in a static map keyed by authority. When Robolectric
   * runs multiple export tests in sequence, each test gets a different external-files temp
   * directory, but the cached strategy still points to the first test's directory. Clearing
   * the cache before each test forces FileProvider to re-initialize with the current directory.
   */
  private fun clearFileProviderCache() {
    try {
      val cacheField = FileProvider::class.java.getDeclaredField("sCache")
      cacheField.isAccessible = true
      @Suppress("UNCHECKED_CAST")
      val cache = cacheField.get(null) as? MutableMap<*, *>
      cache?.clear()
    } catch (_: Exception) {
      // If the field is renamed in a future AndroidX version the tests will still run;
      // they may fail for a different reason, at which point this helper needs updating.
    }
  }

  @Test
  fun testReadBookmarks() {
    val buffer = Buffer().writeUtf8(TAGS_JSON)
    val testObserver = TestObserver<BookmarkData>()
    bookmarkImportExportModel.readBookmarks(buffer)
      .subscribe(testObserver)
    BaseTestExtension.awaitTerminalEvent(testObserver)
    testObserver.assertValueCount(1)
    testObserver.assertNoErrors()
    assertThat(testObserver.values()[0].readingBookmark).isNull()
  }

  @Test
  fun testReadInvalidBookmarks() {
    val testObserver = TestObserver<BookmarkData>()

    val source = Buffer()
    source.writeUtf8(")")

    bookmarkImportExportModel.readBookmarks(source)
      .subscribe(testObserver)
    BaseTestExtension.awaitTerminalEvent(testObserver)
    testObserver.assertValueCount(0)
    testObserver.assertError(IOException::class.java)
  }

  @Test
  fun testExportBookmarksWithDataProducesUri() {
    val ayahBookmark = Bookmark(1L, 2, 255, 50, System.currentTimeMillis())
    bookmarksDao.setTags(listOf(Tag(1L, "Export Tag"), Tag(2L, "Another Tag")))
    bookmarksDao.setBookmarks(
      listOf(
        ayahBookmark,
        Bookmark(2L, null, null, 51, System.currentTimeMillis())
      )
    )
    recentPagesDao.setRecentPages(listOf(RecentPage(50, 1000), RecentPage(51, 900)))
    readingBookmarksDao.setReadingBookmark(PageReadingBookmark(42, timestamp = 1234))

    val testObserver = TestObserver<Uri>()
    bookmarkImportExportModel.exportBookmarksObservable()
      .subscribe(testObserver)
    BaseTestExtension.awaitTerminalEvent(testObserver)

    testObserver.assertNoErrors()
    testObserver.assertValueCount(1)
    assertThat(testObserver.values()[0]).isNotNull()

    val exportedData = BookmarkJsonModel().fromJson(backupFile().source().buffer())
    assertThat(exportedData.bookmarks).containsExactly(ayahBookmark)
    assertThat(exportedData.recentPages)
      .containsExactly(RecentPage(50, 1000), RecentPage(51, 900))
      .inOrder()
    assertThat(exportedData.readingBookmark).isEqualTo(
      BackupReadingBookmark(
        type = BackupReadingBookmark.TYPE_PAGE,
        page = 42,
        timestamp = 1234
      )
    )
  }

  @Test
  fun testExportBookmarksCsvWithDataProducesUri() {
    bookmarksDao.setTags(listOf(Tag(1L, "CSV Tag")))
    bookmarksDao.setBookmarks(
      listOf(Bookmark(1L, 1, 1, 1, System.currentTimeMillis()))
    )
    recentPagesDao.setRecentPages(listOf(RecentPage(12, 1000)))
    readingBookmarksDao.setReadingBookmark(PageReadingBookmark(12, timestamp = 999))

    val testObserver = TestObserver<Uri>()
    bookmarkImportExportModel.exportBookmarksCSVObservable()
      .subscribe(testObserver)
    BaseTestExtension.awaitTerminalEvent(testObserver)

    testObserver.assertNoErrors()
    testObserver.assertValueCount(1)
    assertThat(testObserver.values()[0]).isNotNull()
    assertThat(csvBackupFile().readText()).contains("recent,,, 12, 1000")
    assertThat(csvBackupFile().readText()).contains("reading_bookmark, null, null, 12, 999")
  }

  @Test
  fun testImportReusesExistingTagName() {
    bookmarksDao.setTags(listOf(Tag(1L, "Existing")))

    importData(
      BookmarkData(
        tags = listOf(Tag(99L, "Existing")),
        bookmarks = listOf(Bookmark(1L, 2, 255, 50, 1000, tags = listOf(99L)))
      )
    )

    assertThat(bookmarksDao.addedTagNames()).doesNotContain("Existing")
    assertThat(bookmarksDao.currentBookmarks()).hasSize(1)
    assertThat(bookmarksDao.currentBookmarks()[0].tags).containsExactly(1L)
  }

  @Test
  fun testImportCreatesMissingTagName() {
    importData(
      BookmarkData(
        tags = listOf(Tag(99L, "Missing")),
        bookmarks = listOf(Bookmark(1L, 2, 255, 50, 1000, tags = listOf(99L)))
      )
    )

    assertThat(bookmarksDao.addedTagNames()).containsExactly("Missing")
    assertThat(bookmarksDao.currentTags()).containsExactly(Tag(1L, "Missing"))
    assertThat(bookmarksDao.currentBookmarks()[0].tags).containsExactly(1L)
  }

  @Test
  fun testImportConvertsPageBookmarkToAyahBookmarkWithOldPageBookmarkTag() {
    val page = 50
    val originalTagName = "Imported Tag"
    val oldPageBookmarksTagName = context.getString(R.string.old_page_bookmarks)

    importData(
      BookmarkData(
        tags = listOf(Tag(99L, originalTagName)),
        bookmarks = listOf(Bookmark(1L, null, null, page, 1000, tags = listOf(99L)))
      )
    )

    val pageBounds = quranInfo.getPageBounds(page)
    val importedBookmark = bookmarksDao.currentBookmarks().single()
    val tagsByName = bookmarksDao.currentTags().associateBy { it.name }

    assertThat(importedBookmark.sura).isEqualTo(pageBounds[0])
    assertThat(importedBookmark.ayah).isEqualTo(pageBounds[1])
    assertThat(importedBookmark.page).isEqualTo(page)
    assertThat(bookmarksDao.addedTagNames()).containsExactly(originalTagName, oldPageBookmarksTagName).inOrder()
    assertThat(importedBookmark.tags).containsExactly(
      tagsByName.getValue(originalTagName).id,
      tagsByName.getValue(oldPageBookmarksTagName).id
    )
  }

  @Test
  fun testImportRecentPagesKeepsBackupOrder() {
    importData(
      BookmarkData(
        recentPages = listOf(RecentPage(50, 1000), RecentPage(51, 900))
      )
    )

    val importedPages = runBlocking { recentPagesDao.recentPages() }.map { it.page }
    assertThat(importedPages).containsExactly(50, 51).inOrder()
  }

  @Test
  fun testImportReadingBookmark() {
    importData(
      BookmarkData(
        readingBookmark = BackupReadingBookmark(
          type = BackupReadingBookmark.TYPE_AYAH,
          sura = 2,
          ayah = 255,
          page = 50,
          timestamp = 1000
        )
      )
    )

    val readingBookmark = runBlocking { readingBookmarksDao.readingBookmark() } as AyahReadingBookmark
    assertThat(readingBookmark.sura).isEqualTo(2)
    assertThat(readingBookmark.ayah).isEqualTo(255)
  }

  private fun importData(data: BookmarkData) {
    val testObserver = TestObserver<Boolean>()
    bookmarkImportExportModel.importBookmarksObservable(data)
      .subscribe(testObserver)
    BaseTestExtension.awaitTerminalEvent(testObserver)

    testObserver.assertNoErrors()
    testObserver.assertValue(true)
    testObserver.assertComplete()
  }

  private fun backupFile(): File =
    File(File(context.getExternalFilesDir(null), "backups"), "quran_android.backup")

  private fun csvBackupFile(): File =
    File(File(context.getExternalFilesDir(null), "backups"), "quran_android.backup.csv")

  companion object {
    private const val TAGS_JSON =
      "{\"bookmarks\":[],\"tags\":[{\"id\":1,\"name\":\"First\"}," +
        "{\"id\":2,\"name\":\"Second\"},{\"id\":3,\"name\":\"Third\"}]}"
  }
}
