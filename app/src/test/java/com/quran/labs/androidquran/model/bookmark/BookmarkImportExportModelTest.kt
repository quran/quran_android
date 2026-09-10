package com.quran.labs.androidquran.model.bookmark

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.Settings
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
import com.quran.labs.androidquran.fakes.FakePageProvider
import com.quran.labs.androidquran.fakes.FakeReadingBookmarksDao
import com.quran.labs.androidquran.fakes.FakeRecentPagesDao
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.bookmark.importdata.BookmarkBackupImportNormalizer
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportReadingBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportResult
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.bookmark.model.ReadingBookmarkPageMapper
import com.quran.mobile.bookmark.time.DefaultMobileSyncTimestampProvider
import io.reactivex.rxjava3.observers.TestObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
  private lateinit var settings: FakeSettings
  private lateinit var quranInfo: QuranInfo
  private lateinit var mobileSyncImporter: FakeMobileSyncImporter
  private lateinit var bookmarkImportExportModel: BookmarkImportExportModel

  @Before
  fun setUp() {
    bookmarksDao = FakeBookmarksDao()
    recentPagesDao = FakeRecentPagesDao()
    readingBookmarksDao = FakeReadingBookmarksDao()
    settings = FakeSettings()
    quranInfo = QuranInfo(MadaniDataSource())
    mobileSyncImporter = FakeMobileSyncImporter()
    clearFileProviderCache()
    val pageMapper = ReadingBookmarkPageMapper(
      settings = settings,
      pageProviders = mapOf("madani" to FakePageProvider()),
      fallbackPageType = "madani"
    )
    bookmarkImportExportModel = BookmarkImportExportModel(
      context,
      BookmarkJsonModel(),
      bookmarksDao,
      recentPagesDao,
      readingBookmarksDao,
      settings,
      pageMapper,
      BookmarkBackupImportNormalizer(
        context,
        pageMapper,
        DefaultMobileSyncTimestampProvider()
      ),
      mobileSyncImporter
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
    assertThat(testObserver.values()[0].tags.map { tag -> tag.id })
      .containsExactly("1", "2", "3")
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
    val ayahBookmark = Bookmark("1", 2, 255, 50, System.currentTimeMillis())
    bookmarksDao.setTags(
      listOf(
        Tag("1", "Export Tag"),
        Tag("2", "Another Tag")
      )
    )
    bookmarksDao.setBookmarks(
      listOf(
        ayahBookmark,
        Bookmark("2", null, null, 51, System.currentTimeMillis())
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
    assertThat(exportedData.pageType).isEqualTo("madani")
    assertThat(exportedData.readingBookmark).isEqualTo(
      BackupReadingBookmark(
        type = BackupReadingBookmark.TYPE_PAGE,
        page = 42,
        timestamp = 1234
      )
    )
  }

  @Test
  fun testExportBookmarksWithAyahReadingBookmarkIncludesResolvedPage() {
    val sura = 2
    val ayah = 255
    val page = quranInfo.getPageFromSuraAyah(sura, ayah)
    readingBookmarksDao.setReadingBookmark(AyahReadingBookmark(sura, ayah, timestamp = 1234))

    val testObserver = TestObserver<Uri>()
    bookmarkImportExportModel.exportBookmarksObservable()
      .subscribe(testObserver)
    BaseTestExtension.awaitTerminalEvent(testObserver)

    testObserver.assertNoErrors()
    testObserver.assertValueCount(1)

    val exportedData = BookmarkJsonModel().fromJson(backupFile().source().buffer())
    assertThat(exportedData.readingBookmark).isEqualTo(
      BackupReadingBookmark(
        type = BackupReadingBookmark.TYPE_AYAH,
        sura = sura,
        ayah = ayah,
        page = page,
        timestamp = 1234
      )
    )
  }

  @Test
  fun testExportBookmarksCsvWithDataProducesUri() {
    bookmarksDao.setTags(listOf(Tag("1", "CSV Tag")))
    bookmarksDao.setBookmarks(
      listOf(Bookmark("1", 1, 1, 1, System.currentTimeMillis()))
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
  fun testExportBookmarksCsvWithAyahReadingBookmarkIncludesResolvedPage() {
    val sura = 2
    val ayah = 255
    val page = quranInfo.getPageFromSuraAyah(sura, ayah)
    readingBookmarksDao.setReadingBookmark(AyahReadingBookmark(sura, ayah, timestamp = 999))

    val testObserver = TestObserver<Uri>()
    bookmarkImportExportModel.exportBookmarksCSVObservable()
      .subscribe(testObserver)
    BaseTestExtension.awaitTerminalEvent(testObserver)

    testObserver.assertNoErrors()
    testObserver.assertValueCount(1)
    assertThat(csvBackupFile().readText()).contains("reading_bookmark, 2, 255, $page, 999")
  }

  @Test
  fun testImportDeduplicatesTagNames() {
    importData(
      BookmarkData(
        tags = listOf(Tag("99", "Existing"), Tag("100", "Existing")),
        bookmarks = listOf(
          Bookmark(
            "1",
            2,
            255,
            50,
            1000,
            tags = listOf("100")
          )
        )
      )
    )

    val importedData = importedData()
    assertThat(importedData.collections.map { collection -> collection.name }).containsExactly("Existing")
    assertThat(importedData.collectionBookmarks.single().collectionImportId)
      .isEqualTo(importedData.collections.single().importId)
  }

  @Test
  fun testImportCreatesMissingTagName() {
    importData(
      BookmarkData(
        tags = listOf(Tag("99", "Missing")),
        bookmarks = listOf(
          Bookmark(
            "1",
            2,
            255,
            50,
            1000,
            tags = listOf("99")
          )
        )
      )
    )

    val importedData = importedData()
    assertThat(importedData.collections.map { collection -> collection.name }).containsExactly("Missing")
    assertThat(importedData.collections.single().timestampMillis).isGreaterThan(0)
    assertThat(importedData.bookmarks.single().sura).isEqualTo(2)
    assertThat(importedData.bookmarks.single().ayah).isEqualTo(255)
    assertThat(importedData.bookmarks.single().timestampMillis).isEqualTo(1_000_000)
    assertThat(importedData.collectionBookmarks.single().timestampMillis).isEqualTo(1_000_000)
  }

  @Test
  fun testImportNormalizesLegacyMillisecondTimestamps() {
    val bookmarkTimestampMillis = 1_700_000_001_000L
    val recentTimestampMillis = 1_700_000_002_000L
    val readingBookmarkTimestampMillis = 1_700_000_003_000L
    val page = 50
    val oldPageBookmarksTagName = context.getString(R.string.old_page_bookmarks)

    importData(
      BookmarkData(
        tags = listOf(Tag("99", "Imported Tag")),
        bookmarks = listOf(
          Bookmark(
            id = "1",
            sura = null,
            ayah = null,
            page = page,
            timestamp = bookmarkTimestampMillis,
            tags = listOf("99")
          )
        ),
        recentPages = listOf(RecentPage(page, recentTimestampMillis)),
        readingBookmark = BackupReadingBookmark(
          type = BackupReadingBookmark.TYPE_PAGE,
          page = page,
          timestamp = readingBookmarkTimestampMillis
        )
      )
    )

    val importedData = importedData()
    val oldPageCollection = importedData.collections.single { collection ->
      collection.name == oldPageBookmarksTagName
    }
    val readingBookmark = importedData.readingBookmark as MobileSyncImportReadingBookmark.Page

    assertThat(importedData.bookmarks.single().timestampMillis)
      .isEqualTo(bookmarkTimestampMillis)
    assertThat(importedData.collectionBookmarks.map { link -> link.timestampMillis })
      .containsExactly(bookmarkTimestampMillis, bookmarkTimestampMillis)
    assertThat(oldPageCollection.timestampMillis).isEqualTo(bookmarkTimestampMillis)
    assertThat(importedData.readingSessions.single().timestampMillis)
      .isEqualTo(recentTimestampMillis)
    assertThat(readingBookmark.timestampMillis).isEqualTo(readingBookmarkTimestampMillis)
  }

  @Test
  fun testImportConvertsPageBookmarkToAyahBookmarkWithOldPageBookmarkTag() {
    val page = 50
    val originalTagName = "Imported Tag"
    val oldPageBookmarksTagName = context.getString(R.string.old_page_bookmarks)

    importData(
      BookmarkData(
        tags = listOf(Tag("99", originalTagName)),
        bookmarks = listOf(
          Bookmark(
            "1",
            null,
            null,
            page,
            1000,
            tags = listOf("99")
          )
        )
      )
    )

    val pageBounds = quranInfo.getPageBounds(page)
    val importedData = importedData()
    val importedBookmark = importedData.bookmarks.single()
    val collectionsByName = importedData.collections.associateBy { collection -> collection.name }

    assertThat(importedBookmark.sura).isEqualTo(pageBounds[0])
    assertThat(importedBookmark.ayah).isEqualTo(pageBounds[1])
    assertThat(importedData.collections.map { collection -> collection.name })
      .containsExactly(originalTagName, oldPageBookmarksTagName)
      .inOrder()
    assertThat(importedData.collectionBookmarks.map { link -> link.collectionImportId }).containsExactly(
      collectionsByName.getValue(originalTagName).importId,
      collectionsByName.getValue(oldPageBookmarksTagName).importId
    )
    assertThat(mobileSyncImporter.deleteExisting).isFalse()
  }

  @Test
  fun testImportUsesBackupTagIdsOnlyAsLookupKeys() {
    val page = 50
    val backupTagId = "old-page-bookmarks"
    val originalTagName = "Imported Tag"
    val oldPageBookmarksTagName = context.getString(R.string.old_page_bookmarks)

    importData(
      BookmarkData(
        tags = listOf(Tag(backupTagId, originalTagName)),
        bookmarks = listOf(
          Bookmark(
            id = "1",
            sura = null,
            ayah = null,
            page = page,
            timestamp = 1000,
            tags = listOf(backupTagId)
          )
        )
      )
    )

    val importedData = importedData()
    val collectionsByName = importedData.collections.associateBy { collection -> collection.name }
    val originalTagCollection = collectionsByName.getValue(originalTagName)
    val oldPageBookmarksCollection = collectionsByName.getValue(oldPageBookmarksTagName)

    val collectionImportIds = importedData.collections.map { collection -> collection.importId }
    assertThat(collectionImportIds).containsNoDuplicates()
    assertThat(collectionImportIds).doesNotContain(backupTagId)
    assertThat(originalTagCollection.importId).isNotEqualTo(backupTagId)
    assertThat(importedData.collectionBookmarks.map { link -> link.collectionImportId }).containsExactly(
      originalTagCollection.importId,
      oldPageBookmarksCollection.importId
    )
  }

  @Test
  fun testImportGeneratedCollectionIdsDoNotReuseBackupTagIds() {
    val backupTagIds = listOf("backup-collection-0", "backup-collection-1", "backup-collection-2")

    importData(
      BookmarkData(
        tags = listOf(
          Tag(backupTagIds[0], "First Imported Tag"),
          Tag(backupTagIds[1], "Second Imported Tag"),
          Tag(backupTagIds[2], "Third Imported Tag")
        ),
        bookmarks = listOf(
          Bookmark(
            id = "1",
            sura = null,
            ayah = null,
            page = 50,
            timestamp = 1000,
            tags = backupTagIds
          )
        )
      )
    )

    val collectionImportIds = importedData().collections.map { collection -> collection.importId }

    assertThat(collectionImportIds).containsNoDuplicates()
    backupTagIds.forEach { backupTagId ->
      assertThat(collectionImportIds).doesNotContain(backupTagId)
    }
  }

  @Test
  fun testImportRecentPagesKeepsBackupOrder() {
    importData(
      BookmarkData(
        recentPages = listOf(RecentPage(50, 1000), RecentPage(51, 900))
      )
    )

    val importedPages = importedData().readingSessions.map { session ->
      quranInfo.getPageFromSuraAyah(session.sura, session.ayah)
    }
    assertThat(importedPages).containsExactly(50, 51).inOrder()
    assertThat(importedData().readingSessions.map { session -> session.timestampMillis })
      .containsExactly(1_000_000L, 900_000L)
      .inOrder()
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

    val readingBookmark = importedData().readingBookmark as MobileSyncImportReadingBookmark.Ayah
    assertThat(readingBookmark.sura).isEqualTo(2)
    assertThat(readingBookmark.ayah).isEqualTo(255)
    assertThat(readingBookmark.timestampMillis).isEqualTo(1_000_000)
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

  private fun importedData(): MobileSyncImportData = requireNotNull(mobileSyncImporter.importedData)

  private fun backupFile(): File =
    File(File(context.getExternalFilesDir(null), "backups"), "quran_android.backup")

  private fun csvBackupFile(): File =
    File(File(context.getExternalFilesDir(null), "backups"), "quran_android.backup.csv")

  private class FakeMobileSyncImporter : MobileSyncImporter {
    var importedData: MobileSyncImportData? = null
      private set
    var deleteExisting: Boolean? = null
      private set

    override suspend fun importData(
      data: MobileSyncImportData,
      deleteExisting: Boolean
    ): MobileSyncImportResult {
      importedData = data
      this.deleteExisting = deleteExisting
      return MobileSyncImportResult(
        bookmarksImported = data.bookmarks.size,
        collectionsImported = data.collections.size,
        collectionBookmarksImported = data.collectionBookmarks.size,
        readingSessionsImported = data.readingSessions.size,
        readingBookmarkImported = data.readingBookmark != null
      )
    }
  }

  private class FakeSettings : Settings {
    private val preferences = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var pageType = "madani"

    override suspend fun setVersion(version: Int) = Unit

    override suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean) = Unit

    override suspend fun lastPage(): Int = 1

    override suspend fun isNightMode(): Boolean = false

    override suspend fun nightModeTextBrightness(): Int = 0

    override suspend fun nightModeBackgroundBrightness(): Int = 0

    override suspend fun shouldShowHeaderFooter(): Boolean = false

    override suspend fun shouldShowBookmarks(): Boolean = false

    override suspend fun pageType(): String = pageType

    override suspend fun setPageType(pageType: String) {
      this.pageType = pageType
      preferences.emit("pageType")
    }

    override suspend fun showSidelines(): Boolean = false

    override suspend fun setShowSidelines(show: Boolean) = Unit

    override suspend fun showLineDividers(): Boolean = false

    override suspend fun setShouldShowLineDividers(show: Boolean) = Unit

    override suspend fun setAyahTextSize(value: Int) = Unit

    override suspend fun translationTextSize(): Int = 0

    override fun preferencesFlow(): Flow<String> = preferences
  }

  companion object {
    private const val TAGS_JSON =
      "{\"bookmarks\":[],\"tags\":[{\"id\":1,\"name\":\"First\"}," +
        "{\"id\":2,\"name\":\"Second\"},{\"id\":3,\"name\":\"Third\"}]}"
  }
}
