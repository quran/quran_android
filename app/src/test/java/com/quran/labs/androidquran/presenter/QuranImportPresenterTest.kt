package com.quran.labs.androidquran.presenter

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.dao.Settings
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeContentResolverOps
import com.quran.labs.androidquran.fakes.FakePageProvider
import com.quran.labs.androidquran.fakes.FakeReadingBookmarksDao
import com.quran.labs.androidquran.fakes.FakeRecentPagesDao
import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel
import com.quran.labs.androidquran.model.bookmark.BookmarkJsonModel
import com.quran.labs.awaitTerminalEvent
import com.quran.mobile.bookmark.importdata.BookmarkBackupImportNormalizer
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportResult
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.bookmark.model.ReadingBookmarkPageMapper
import com.quran.mobile.bookmark.time.DefaultMobileSyncTimestampProvider
import io.reactivex.rxjava3.observers.TestObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import okio.BufferedSource
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class QuranImportPresenterTest {

  private lateinit var context: Context
  private lateinit var importExportModel: BookmarkImportExportModel

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    val settings = FakeSettings()
    val pageMapper = ReadingBookmarkPageMapper(
      settings = settings,
      pageProviders = mapOf("madani" to FakePageProvider()),
      fallbackPageType = "madani"
    )
    importExportModel = BookmarkImportExportModel(
      context,
      BookmarkJsonModel(),
      FakeBookmarksDao(),
      FakeRecentPagesDao(),
      FakeReadingBookmarksDao(),
      settings,
      BookmarkBackupImportNormalizer(
        context,
        pageMapper,
        DefaultMobileSyncTimestampProvider()
      ),
      FakeMobileSyncImporter()
    )
  }

  // ---- parseExternalFile tests ----

  @Test
  @Throws(FileNotFoundException::class)
  fun testParseExternalFile() {
    val uri = Uri.parse("content://quran.test/backup")
    val stream: InputStream = ByteArrayInputStream(ByteArray(32))
    val fakeOps = FakeContentResolverOps(inputStream = stream)

    val presenter = QuranImportPresenter(context, importExportModel, fakeOps)

    val observer = TestObserver<BufferedSource>()
    presenter.parseExternalFile(uri).subscribe(observer)
    observer.awaitTerminalEvent()
    observer.assertValueCount(1)
    observer.assertNoErrors()
    observer.assertComplete()

    val events = observer.values()
    assertThat(events).hasSize(1)
    assertThat(events[0]).isNotNull()
  }

  @Test
  @Throws(FileNotFoundException::class)
  fun testParseExternalFileNullIs() {
    val uri = Uri.parse("content://quran.test/backup")
    // FakeContentResolverOps returns null inputStream by default
    val fakeOps = FakeContentResolverOps()

    val presenter = QuranImportPresenter(context, importExportModel, fakeOps)

    val observer = TestObserver<BufferedSource>()
    presenter.parseExternalFile(uri).subscribe(observer)
    observer.awaitTerminalEvent()
    observer.assertValueCount(0)
    observer.assertNoErrors()
    observer.assertComplete()
  }

  // ---- parseUri tests ----

  @Test
  @Throws(FileNotFoundException::class)
  fun testParseUriNullFd() {
    val uri = Uri.parse("content://quran.test/backup")
    // FakeContentResolverOps returns null fileDescriptor by default
    val fakeOps = FakeContentResolverOps()

    val presenter = QuranImportPresenter(context, importExportModel, fakeOps)

    val observer = TestObserver<BufferedSource>()
    presenter.parseUri(uri).subscribe(observer)
    observer.awaitTerminalEvent()
    observer.assertComplete()
    observer.assertValueCount(0)
    observer.assertNoErrors()
  }

  @Test
  @Throws(FileNotFoundException::class)
  fun testParseUriWithException() {
    val uri = Uri.parse("content://quran.test/backup")
    val fakeOps = FakeContentResolverOps(fileDescriptorException = NullPointerException())

    val presenter = QuranImportPresenter(context, importExportModel, fakeOps)

    val observer = TestObserver<BufferedSource>()
    presenter.parseUri(uri).subscribe(observer)
    observer.awaitTerminalEvent()
    observer.assertError(NullPointerException::class.java)
    observer.assertValueCount(0)
  }

  private class FakeMobileSyncImporter : MobileSyncImporter {
    override suspend fun importData(
      data: MobileSyncImportData,
      deleteExisting: Boolean
    ): MobileSyncImportResult {
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
}
