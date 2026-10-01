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
import com.quran.mobile.bookmark.model.RepositoryBackedTestBookmarkCollectionsState
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.model.AyahReadingBookmark
import com.quran.shared.persistence.model.PageReadingBookmark
import com.quran.shared.persistence.model.ReadingBookmarkSlot
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

  @Before
  fun setUp() {
    val context = RuntimeEnvironment.getApplication().applicationContext as Context
    context.deleteDatabase("quran.db")
    mobileSyncDatabase = MobileSyncDatabase(context)
    appCoroutineScope = AppCoroutineScope()
    val collectionsRepository = CollectionsRepositoryImpl(mobileSyncDatabase.database)
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(mobileSyncDatabase.database)
    bookmarksDao = BookmarksDaoImpl(
      quranInfoProvider = { QuranInfo(MadaniDataSource()) },
      bookmarksRepository = BookmarksRepositoryImpl(mobileSyncDatabase.database),
      collectionsRepository = collectionsRepository,
      collectionBookmarksRepository = collectionBookmarksRepository,
      bookmarkCollectionsState = RepositoryBackedTestBookmarkCollectionsState(
        collectionsRepository,
        collectionBookmarksRepository,
        appCoroutineScope
      ),
      timestampProvider = FakeMobileSyncTimestampProvider(),
      appCoroutineScope = appCoroutineScope
    )
    importer = MobileSyncImporterImpl(mobileSyncDatabase, context)
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
            timestampMillis = 1_234_000L
          )
        ),
        collections = listOf(
          MobileSyncImportCollection(
            importId = "tag-1",
            name = "Reading",
            timestampMillis = 1_200_000L
          )
        ),
        collectionBookmarks = listOf(
          MobileSyncImportCollectionBookmark(
            collectionImportId = "tag-1",
            bookmarkImportId = "bookmark-2-255",
            timestampMillis = 1_234_000L
          )
        ),
        readingSessions = listOf(
          MobileSyncImportReadingSession(
            sura = 18,
            ayah = 1,
            timestampMillis = 1_100_000L
          )
        )
      )
    )

    val bookmarks = bookmarksDao.bookmarks()
    // the default collection is always present, so this asserts on the imported ones
    val tags = bookmarksDao.tags().filterNot { tag -> tag.isSystem }
    val readingSessions = ReadingSessionsRepositoryImpl(mobileSyncDatabase.database).getReadingSessions()

    assertThat(bookmarks.map { bookmark -> bookmark.sura to bookmark.ayah }).containsExactly(2 to 255)
    assertThat(bookmarks.single().timestamp).isEqualTo(1234L)
    assertThat(tags.map { tag -> tag.name }).containsExactly("Reading")
    val favorites = bookmarksDao.tags().single { it.isSystem }
    assertThat(bookmarksDao.getBookmarkTagIds(bookmarks.single().id))
      .containsExactly(favorites.id, tags.single().id)
    assertThat(readingSessions.map { session -> session.sura to session.ayah }).containsExactly(18 to 1)
  }

  @Test
  fun `reading bookmark import replaces supplied slots and preserves omitted slots`() = runTest {
    val repository = ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database)
    val existing = repository.setPageReadingBookmark(ReadingBookmarkSlot.GREEN, 12)
    val omitted = repository.setPageReadingBookmark(ReadingBookmarkSlot.BLUE, 50)
    repository.renameReadingBookmark(ReadingBookmarkSlot.GREEN, "Old name")

    val result = importer.importData(
      MobileSyncImportData(
        readingBookmarks = listOf(
          MobileSyncImportReadingBookmark.Page(
            slot = ReadingBookmarkSlot.GREEN,
            page = 42,
            timestampMillis = 1_700_000_000_000L
          ),
          MobileSyncImportReadingBookmark.Ayah(
            slot = ReadingBookmarkSlot.PURPLE,
            sura = 2,
            ayah = 255,
            timestampMillis = 1_700_000_001_000L,
            name = "Nightly reading"
          )
        )
      )
    )

    assertThat(result.readingBookmarkImported).isEqualTo(2)
    val bookmarks = repository.getReadingBookmarks()
    val page = bookmarks.single { it.slot == ReadingBookmarkSlot.GREEN } as PageReadingBookmark
    assertThat(page.id).isEqualTo(existing.id)
    assertThat(page.page).isEqualTo(42)
    assertThat(page.name).isNull()
    assertThat(page.lastUpdated.toEpochMilliseconds()).isEqualTo(1_700_000_000_000L)
    val ayah = bookmarks.single { it.slot == ReadingBookmarkSlot.PURPLE } as AyahReadingBookmark
    assertThat(ayah.sura to ayah.ayah).isEqualTo(2 to 255)
    assertThat(ayah.name).isEqualTo("Nightly reading")
    assertThat(ayah.lastUpdated.toEpochMilliseconds()).isEqualTo(1_700_000_001_000L)
    assertThat(bookmarks.single { it.slot == ReadingBookmarkSlot.BLUE }).isEqualTo(omitted)
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
            timestampMillis = 1_234_000L
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
            timestampMillis = 1_300_000L
          )
        )
      ),
      deleteExisting = true
    )

    assertThat(bookmarksDao.bookmarks().map { bookmark -> bookmark.sura to bookmark.ayah })
      .containsExactly(3 to 2)
  }
}
