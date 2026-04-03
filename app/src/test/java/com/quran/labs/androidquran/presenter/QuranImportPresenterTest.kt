package com.quran.labs.androidquran.presenter

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeBookmarkModel
import com.quran.labs.androidquran.fakes.FakeContentResolverOps
import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel
import com.quran.labs.androidquran.model.bookmark.BookmarkJsonModel
import com.quran.labs.awaitTerminalEvent
import io.reactivex.rxjava3.observers.TestObserver
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
    importExportModel = BookmarkImportExportModel(context, BookmarkJsonModel(), FakeBookmarkModel())
  }

  // ---- parseExternalFile tests ----

  @Test
  @Throws(FileNotFoundException::class)
  fun testParseExternalFile() {
    val uri = Uri.parse("content://quran.test/backup")
    val stream: InputStream = ByteArrayInputStream(ByteArray(32))
    val fakeOps = FakeContentResolverOps(inputStream = stream)

    val presenter = QuranImportPresenter(context, importExportModel, FakeBookmarkModel(), fakeOps)

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

    val presenter = QuranImportPresenter(context, importExportModel, FakeBookmarkModel(), fakeOps)

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

    val presenter = QuranImportPresenter(context, importExportModel, FakeBookmarkModel(), fakeOps)

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

    val presenter = QuranImportPresenter(context, importExportModel, FakeBookmarkModel(), fakeOps)

    val observer = TestObserver<BufferedSource>()
    presenter.parseUri(uri).subscribe(observer)
    observer.awaitTerminalEvent()
    observer.assertError(NullPointerException::class.java)
    observer.assertValueCount(0)
  }
}
