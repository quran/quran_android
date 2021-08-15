package com.quran.labs.androidquran.presenter

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor

import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel
import com.quran.labs.androidquran.model.bookmark.BookmarkModel

import org.junit.Before
import org.junit.Test

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream

import io.reactivex.rxjava3.observers.TestObserver
import okio.BufferedSource

import com.google.common.truth.Truth.assertThat
import com.quran.labs.awaitTerminalEvent
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when` as whenever
import org.mockito.Mockito.mock

class QuranImportPresenterTest {

  private lateinit var appContext: Context
  private lateinit var presenter: QuranImportPresenter

  @Before
  fun setup() {
    appContext = mock(Context::class.java)
    val model = mock(BookmarkImportExportModel::class.java)
    presenter = QuranImportPresenter(appContext, model, mock(BookmarkModel::class.java))
  }

  @Test
  @Throws(FileNotFoundException::class)
  fun testParseExternalFile() {
    val `is`: InputStream = ByteArrayInputStream(ByteArray(32))
    val resolver = mock(ContentResolver::class.java)
    whenever(resolver.openInputStream(any())).thenReturn(`is`)
    whenever(appContext.contentResolver).thenReturn(resolver)

    val observer = TestObserver<BufferedSource>()
    presenter.parseExternalFile(Uri.EMPTY)
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
    val resolver = mock(ContentResolver::class.java)
    whenever(resolver.openInputStream(any(Uri::class.java))).thenReturn(null)
    whenever(appContext.contentResolver).thenReturn(resolver)

    val observer = TestObserver<BufferedSource>()
    presenter.parseExternalFile(Uri.EMPTY)
      .subscribe(observer)
    observer.awaitTerminalEvent()
    observer.assertValueCount(0)
    observer.assertNoErrors()
    observer.assertComplete()
  }

  @Test
  @Throws(FileNotFoundException::class)
  fun testParseUriNullFd() {
    val resolver = mock(ContentResolver::class.java)
    whenever(resolver.openFileDescriptor(any(Uri::class.java), anyString())).thenReturn(null)
    whenever(appContext.contentResolver).thenReturn(resolver)

    val observer = TestObserver<BufferedSource>()
    presenter.parseUri(Uri.EMPTY)
      .subscribe(observer)
    observer.awaitTerminalEvent()
    observer.assertComplete()
    observer.assertValueCount(0)
    observer.assertNoErrors()
  }

  @Test
  @Throws(FileNotFoundException::class)
  fun testParseUriWithException() {
    val pfd = mock(ParcelFileDescriptor::class.java)
    whenever(pfd.fd).thenReturn(-1)

    val resolver = mock(ContentResolver::class.java)
    whenever(resolver.openFileDescriptor(any(), anyString())).thenReturn(pfd)
    whenever(appContext.contentResolver).thenReturn(resolver)

    val observer = TestObserver<BufferedSource>()
    presenter.parseUri(Uri.EMPTY)
      .subscribe(observer)
    observer.awaitTerminalEvent()
    observer.assertError(NullPointerException::class.java)
    observer.assertValueCount(0)
  }
}
