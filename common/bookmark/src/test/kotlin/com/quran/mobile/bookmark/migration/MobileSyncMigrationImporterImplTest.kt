package com.quran.mobile.bookmark.migration

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppCoroutineScope
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.bookmark.importdata.MobileSyncImportBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollection
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollectionBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportReadingBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportReadingSession
import com.quran.mobile.bookmark.importdata.MobileSyncImporterImpl
import com.quran.mobile.bookmark.model.BookmarksDaoImpl
import com.quran.mobile.bookmark.sync.FakeLocalDataChangeNotifier
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepositoryImpl
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepositoryImpl
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepositoryImpl
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepositoryImpl
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepositoryImpl
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MobileSyncImporterImplTest {

  private lateinit var mobileSyncDatabase: MobileSyncDatabase
  private lateinit var importer: MobileSyncImporterImpl
  private lateinit var bookmarksDao: BookmarksDaoImpl
  private lateinit var appCoroutineScope: AppCoroutineScope
  private lateinit var localDataChangeNotifier: FakeLocalDataChangeNotifier

  @Before
  fun setUp() {
    val context = RuntimeEnvironment.getApplication().applicationContext as Context
    context.deleteDatabase("quran.db")
    mobileSyncDatabase = MobileSyncDatabase(context)
    appCoroutineScope = AppCoroutineScope()
    localDataChangeNotifier = FakeLocalDataChangeNotifier()
    bookmarksDao = BookmarksDaoImpl(
      quranInfoProvider = { QuranInfo(MadaniDataSource()) },
      bookmarksRepository = BookmarksRepositoryImpl(mobileSyncDatabase.database),
      collectionsRepository = CollectionsRepositoryImpl(mobileSyncDatabase.database),
      collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(mobileSyncDatabase.database),
      localDataChangeNotifier = localDataChangeNotifier,
      timestampProvider = FakeMobileSyncTimestampProvider(),
      appCoroutineScope = appCoroutineScope
    )
    importer = MobileSyncImporterImpl(mobileSyncDatabase, localDataChangeNotifier)
  }

  @After
  fun tearDown() {
    if (::appCoroutineScope.isInitialized) {
      appCoroutineScope.cancel()
    }
  }

  @Test
  fun `import data writes bookmarks collections and reading sessions`() = runTest {
    importer.importData(
      MobileSyncImportData(
        bookmarks = listOf(
          MobileSyncImportBookmark(
            importId = "bookmark-2-255",
            sura = 2,
            ayah = 255,
            timestampSeconds = 1234L
          )
        ),
        collections = listOf(
          MobileSyncImportCollection(
            importId = "tag-1",
            name = "Reading",
            timestampSeconds = 1200L
          )
        ),
        collectionBookmarks = listOf(
          MobileSyncImportCollectionBookmark(
            collectionImportId = "tag-1",
            bookmarkImportId = "bookmark-2-255",
            timestampSeconds = 1234L
          )
        ),
        readingSessions = listOf(
          MobileSyncImportReadingSession(
            sura = 18,
            ayah = 1,
            timestampSeconds = 1100L
          )
        ),
        readingBookmark = MobileSyncImportReadingBookmark.Page(
          page = 42,
          timestampSeconds = 1300L
        )
      )
    )

    val bookmarks = bookmarksDao.bookmarks()
    val tags = bookmarksDao.tags()
    val readingSessions = ReadingSessionsRepositoryImpl(mobileSyncDatabase.database).getReadingSessions()
    val readingBookmark = ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database).getReadingBookmark()

    assertThat(bookmarks.map { bookmark -> bookmark.sura to bookmark.ayah }).containsExactly(2 to 255)
    assertThat(bookmarks.single().timestamp).isEqualTo(1234L)
    assertThat(tags.map { tag -> tag.name }).containsExactly("Reading")
    assertThat(bookmarksDao.getBookmarkTagIds(bookmarks.single().id)).containsExactly(tags.single().id)
    assertThat(readingSessions.map { session -> session.sura to session.ayah }).containsExactly(18 to 1)
    assertThat(readingBookmark).isNotNull()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(1)
  }

  @Test
  fun `delete existing flag replaces active imported data`() = runTest {
    importer.importData(
      MobileSyncImportData(
        bookmarks = listOf(
          MobileSyncImportBookmark(
            importId = "bookmark-2-255",
            sura = 2,
            ayah = 255,
            timestampSeconds = 1234L
          )
        )
      )
    )

    importer.importData(
      data = MobileSyncImportData(
        bookmarks = listOf(
          MobileSyncImportBookmark(
            importId = "bookmark-3-2",
            sura = 3,
            ayah = 2,
            timestampSeconds = 1300L
          )
        )
      ),
      deleteExisting = true
    )

    assertThat(bookmarksDao.bookmarks().map { bookmark -> bookmark.sura to bookmark.ayah })
      .containsExactly(3 to 2)
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(2)
  }
}
