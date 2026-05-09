package com.quran.mobile.bookmark.migration

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppCoroutineScope
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.bookmark.model.BookmarksDaoImpl
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
class MobileSyncMigrationImporterImplTest {

  private lateinit var mobileSyncDatabase: MobileSyncDatabase
  private lateinit var importer: MobileSyncMigrationImporterImpl
  private lateinit var bookmarksDao: BookmarksDaoImpl
  private lateinit var appCoroutineScope: AppCoroutineScope

  @Before
  fun setUp() {
    val context = RuntimeEnvironment.getApplication().applicationContext as Context
    context.deleteDatabase("quran.db")
    mobileSyncDatabase = MobileSyncDatabase(context)
    appCoroutineScope = AppCoroutineScope()
    bookmarksDao = BookmarksDaoImpl(
      quranInfoProvider = { QuranInfo(MadaniDataSource()) },
      mobileSyncDatabase = mobileSyncDatabase,
      appCoroutineScope = appCoroutineScope
    )
    importer = MobileSyncMigrationImporterImpl(mobileSyncDatabase)
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
      MobileSyncMigrationData(
        bookmarks = listOf(
          MobileSyncMigrationBookmark(
            importId = "bookmark-2-255",
            sura = 2,
            ayah = 255,
            timestampSeconds = 1234L
          )
        ),
        collections = listOf(
          MobileSyncMigrationCollection(
            importId = "tag-1",
            name = "Reading",
            timestampSeconds = 1200L
          )
        ),
        collectionBookmarks = listOf(
          MobileSyncMigrationCollectionBookmark(
            collectionImportId = "tag-1",
            bookmarkImportId = "bookmark-2-255",
            timestampSeconds = 1234L
          )
        ),
        readingSessions = listOf(
          MobileSyncMigrationReadingSession(
            sura = 18,
            ayah = 1,
            timestampSeconds = 1100L
          )
        )
      )
    )

    val bookmarks = bookmarksDao.bookmarks()
    val tags = bookmarksDao.tags()
    val readingSessions = ReadingSessionsRepositoryImpl(mobileSyncDatabase.database).getReadingSessions()

    assertThat(bookmarks.map { bookmark -> bookmark.sura to bookmark.ayah }).containsExactly(2 to 255)
    assertThat(bookmarks.single().timestamp).isEqualTo(1234L)
    assertThat(tags.map { tag -> tag.name }).containsExactly("Reading")
    assertThat(bookmarksDao.getBookmarkTagIds(bookmarks.single().id)).containsExactly(tags.single().id)
    assertThat(readingSessions.map { session -> session.sura to session.ayah }).containsExactly(18 to 1)
  }
}
