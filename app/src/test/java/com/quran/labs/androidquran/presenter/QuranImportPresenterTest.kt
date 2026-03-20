package com.quran.labs.androidquran.presenter

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeBookmarkModel
import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel
import com.quran.labs.awaitTerminalEvent
import io.reactivex.rxjava3.observers.TestObserver
import okio.BufferedSource
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import org.mockito.Mockito.`when` as whenever

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class QuranImportPresenterTest {

  private lateinit var context: Context

  // model is final (internal constructor), cannot be subclassed — keep as Mockito mock
  private lateinit var importExportModel: BookmarkImportExportModel

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    importExportModel = mock(BookmarkImportExportModel::class.java)
  }

  // ---- parseExternalFile tests — use ShadowContentResolver ----

  @Test
  @Throws(FileNotFoundException::class)
  fun testParseExternalFile() {
    val uri = Uri.parse("content://quran.test/backup")
    val stream: InputStream = ByteArrayInputStream(ByteArray(32))
    shadowOf(context.contentResolver).registerInputStream(uri, stream)

    val presenter = QuranImportPresenter(context, importExportModel, FakeBookmarkModel())

    val observer = TestObserver<BufferedSource>()
    presenter.parseExternalFile(uri)
      .subscribe(observer)
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
    // ShadowContentResolver cannot return null from openInputStream for content:// URIs
    // (it falls back to UnregisteredInputStream). Use a Mockito mock resolver to force null.
    val resolver = mock(ContentResolver::class.java)
    whenever(resolver.openInputStream(any(Uri::class.java))).thenReturn(null)

    val mockedContext = mock(Context::class.java)
    whenever(mockedContext.contentResolver).thenReturn(resolver)

    val presenter = QuranImportPresenter(mockedContext, importExportModel, FakeBookmarkModel())

    val observer = TestObserver<BufferedSource>()
    presenter.parseExternalFile(uri)
      .subscribe(observer)
    observer.awaitTerminalEvent()
    observer.assertValueCount(0)
    observer.assertNoErrors()
    observer.assertComplete()
  }

  // ---- parseUri tests — ShadowContentResolver has no openFileDescriptor support,
  //      so keep Mockito mock for ContentResolver only in these two tests ----

  @Test
  @Throws(FileNotFoundException::class)
  fun testParseUriNullFd() {
    val uri = Uri.parse("content://quran.test/backup")

    val resolver = mock(ContentResolver::class.java)
    whenever(resolver.openFileDescriptor(any(Uri::class.java), anyString())).thenReturn(null)

    val mockedContext = mock(Context::class.java)
    whenever(mockedContext.contentResolver).thenReturn(resolver)

    val presenter = QuranImportPresenter(mockedContext, importExportModel, FakeBookmarkModel())

    val observer = TestObserver<BufferedSource>()
    presenter.parseUri(uri)
      .subscribe(observer)
    observer.awaitTerminalEvent()
    observer.assertComplete()
    observer.assertValueCount(0)
    observer.assertNoErrors()
  }

  @Test
  @Throws(FileNotFoundException::class)
  fun testParseUriWithException() {
    val uri = Uri.parse("content://quran.test/backup")

    // pfd.fileDescriptor (reference type) returns null by default from unconfigured mock,
    // causing FileInputStream(null) to throw NullPointerException
    val pfd = mock(ParcelFileDescriptor::class.java)

    val resolver = mock(ContentResolver::class.java)
    whenever(resolver.openFileDescriptor(any(Uri::class.java), anyString())).thenReturn(pfd)

    val mockedContext = mock(Context::class.java)
    whenever(mockedContext.contentResolver).thenReturn(resolver)

    val presenter = QuranImportPresenter(mockedContext, importExportModel, FakeBookmarkModel())

    val observer = TestObserver<BufferedSource>()
    presenter.parseUri(uri)
      .subscribe(observer)
    observer.awaitTerminalEvent()
    observer.assertError(NullPointerException::class.java)
    observer.assertValueCount(0)
  }
}
