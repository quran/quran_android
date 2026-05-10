package com.quran.mobile.bookmark.model

import app.cash.turbine.test
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.bookmark.sync.FakeLocalDataChangeNotifier
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepositoryImpl
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadingBookmarksDaoImplTest {

  private lateinit var repository: ReadingBookmarksRepositoryImpl
  private lateinit var quranInfo: QuranInfo
  private lateinit var dao: ReadingBookmarksDaoImpl
  private lateinit var appCoroutineScope: AppCoroutineScope
  private lateinit var localDataChangeNotifier: FakeLocalDataChangeNotifier
  private lateinit var timestampProvider: FakeMobileSyncTimestampProvider

  @Before
  fun setup() {
    val context = RuntimeEnvironment.getApplication().applicationContext as Context
    context.deleteDatabase("quran.db")
    val mobileSyncDatabase = MobileSyncDatabase(context)
    repository = ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database)
    quranInfo = QuranInfo(MadaniDataSource())
    appCoroutineScope = AppCoroutineScope()
    localDataChangeNotifier = FakeLocalDataChangeNotifier()
    timestampProvider = FakeMobileSyncTimestampProvider()
    dao = ReadingBookmarksDaoImpl(
      quranInfoProvider = { quranInfo },
      readingBookmarksRepository = repository,
      localDataChangeNotifier = localDataChangeNotifier,
      timestampProvider = timestampProvider,
      appCoroutineScope = appCoroutineScope
    )
  }

  @After
  fun tearDown() {
    if (::appCoroutineScope.isInitialized) {
      appCoroutineScope.cancel()
    }
  }

  @Test
  fun `reading bookmark is null when no mobile sync reading bookmark exists`() = runTest {
    assertThat(dao.readingBookmark()).isNull()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(0)
  }

  @Test
  fun `set page reading bookmark stores mobile sync page reading bookmark`() = runTest {
    dao.setPageReadingBookmark(42)

    val bookmark = dao.readingBookmark() as PageReadingBookmark
    assertThat(bookmark.page).isEqualTo(42)
    assertThat(bookmark.timestamp).isEqualTo(timestampProvider.timestampSeconds)
    assertThat(dao.isPageReadingBookmark(42)).isTrue()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(1)
  }

  @Test
  fun `set ayah reading bookmark maps page from quran info`() = runTest {
    val suraAyah = SuraAyah(2, 255)

    dao.setAyahReadingBookmark(suraAyah)

    val bookmark = dao.readingBookmark() as AyahReadingBookmark
    assertThat(bookmark.sura).isEqualTo(suraAyah.sura)
    assertThat(bookmark.ayah).isEqualTo(suraAyah.ayah)
    assertThat(bookmark.page).isEqualTo(quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
  }

  @Test
  fun `set page reading bookmark replaces existing ayah reading bookmark`() = runTest {
    dao.setAyahReadingBookmark(SuraAyah(2, 255))

    dao.setPageReadingBookmark(42)

    val bookmark = dao.readingBookmark() as PageReadingBookmark
    assertThat(bookmark.page).isEqualTo(42)
  }

  @Test
  fun `toggle page reading bookmark deletes exact current page`() = runTest {
    dao.setPageReadingBookmark(42)

    val isBookmarked = dao.togglePageReadingBookmark(42)

    assertThat(isBookmarked).isFalse()
    assertThat(dao.readingBookmark()).isNull()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(2)
  }

  @Test
  fun `deleting no reading bookmark does not notify`() = runTest {
    val deleted = dao.deleteReadingBookmark()

    assertThat(deleted).isFalse()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(0)
  }

  @Test
  fun `toggle page reading bookmark replaces ayah bookmark on same page`() = runTest {
    val suraAyah = SuraAyah(2, 255)
    val page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
    dao.setAyahReadingBookmark(suraAyah)

    val isBookmarked = dao.togglePageReadingBookmark(page)

    assertThat(isBookmarked).isTrue()
    val bookmark = dao.readingBookmark() as PageReadingBookmark
    assertThat(bookmark.page).isEqualTo(page)
  }

  @Test
  fun `reading bookmark flow emits external mobile sync writes`() = runTest {
    dao.readingBookmarkFlow().test {
      assertThat(awaitItem()).isNull()

      repository.addPageReadingBookmark(42)

      val bookmark = awaitItem() as PageReadingBookmark
      assertThat(bookmark.page).isEqualTo(42)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
