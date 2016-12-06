package com.quran.labs.androidquran.presenter;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

import io.reactivex.observers.TestObserver;
import okio.BufferedSource;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QuranImportPresenterTest {

  private Context appContext;
  private QuranImportPresenter presenter;

  @Before
  public void setup() {
    appContext = mock(Context.class);
    BookmarkImportExportModel model = mock(BookmarkImportExportModel.class);
    presenter = new QuranImportPresenter(appContext, model, mock(BookmarkModel.class));
  }

  @Test
  public void testParseExternalFile() throws FileNotFoundException {
    InputStream is = new ByteArrayInputStream(new byte[32]);
    ContentResolver resolver = mock(ContentResolver.class);
    when(resolver.openInputStream(any(Uri.class))).thenReturn(is);
    when(appContext.getContentResolver()).thenReturn(resolver);

    TestObserver<BufferedSource> observer = new TestObserver<>();
    presenter.parseExternalFile(Uri.EMPTY)
        .subscribe(observer);
    observer.awaitTerminalEvent();
    observer.assertValueCount(1);
    observer.assertNoErrors();
    observer.assertComplete();

    List<BufferedSource> events = observer.values();
    assertThat(events).hasSize(1);
    assertThat(events.get(0)).isNotNull();
  }

  @Test
  public void testParseExternalFileNullIs() throws FileNotFoundException {
    ContentResolver resolver = mock(ContentResolver.class);
    when(resolver.openInputStream(any(Uri.class))).thenReturn(null);
    when(appContext.getContentResolver()).thenReturn(resolver);

    TestObserver<BufferedSource> observer = new TestObserver<>();
    presenter.parseExternalFile(Uri.EMPTY)
        .subscribe(observer);
    observer.awaitTerminalEvent();
    observer.assertValueCount(0);
    observer.assertNoErrors();
    observer.assertComplete();
  }

  @Test
  public void testParseUriNullFd() throws FileNotFoundException {
    ContentResolver resolver = mock(ContentResolver.class);
    when(resolver.openFileDescriptor(any(Uri.class), anyString())).thenReturn(null);
    when(appContext.getContentResolver()).thenReturn(resolver);

    TestObserver<BufferedSource> observer = new TestObserver<>();
    presenter.parseUri(Uri.EMPTY)
        .subscribe(observer);
    observer.awaitTerminalEvent();
    observer.assertComplete();
    observer.assertValueCount(0);
    observer.assertNoErrors();
  }

  @Test
  public void testParseUriWithException() throws FileNotFoundException {
    ParcelFileDescriptor pfd = mock(ParcelFileDescriptor.class);
    when(pfd.getFd()).thenReturn(-1);

    ContentResolver resolver = mock(ContentResolver.class);
    when(resolver.openFileDescriptor(any(Uri.class), anyString())).thenReturn(pfd);
    when(appContext.getContentResolver()).thenReturn(resolver);

    TestObserver<BufferedSource> observer = new TestObserver<>();
    presenter.parseUri(Uri.EMPTY)
        .subscribe(observer);
    observer.awaitTerminalEvent();
    observer.assertError(NullPointerException.class);
    observer.assertValueCount(0);
  }
}
