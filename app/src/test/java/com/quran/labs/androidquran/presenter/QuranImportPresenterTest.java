package com.quran.labs.androidquran.presenter;

import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel;

import org.junit.Before;
import org.junit.Test;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

import okio.BufferedSource;
import rx.observers.TestSubscriber;

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
    presenter = new QuranImportPresenter(appContext, model);
  }

  @Test
  public void testParseExternalFile() throws FileNotFoundException {
    InputStream is = new ByteArrayInputStream(new byte[32]);
    ContentResolver resolver = mock(ContentResolver.class);
    when(resolver.openInputStream(any(Uri.class))).thenReturn(is);
    when(appContext.getContentResolver()).thenReturn(resolver);

    TestSubscriber<BufferedSource> subscriber = new TestSubscriber<>();
    presenter.parseExternalFile(Uri.EMPTY)
        .subscribe(subscriber);
    subscriber.awaitTerminalEvent();
    subscriber.assertValueCount(1);
    subscriber.assertNoErrors();
    subscriber.assertCompleted();
    subscriber.assertUnsubscribed();

    List<BufferedSource> events = subscriber.getOnNextEvents();
    assertThat(events).hasSize(1);
    assertThat(events.get(0)).isNotNull();
  }

  @Test
  public void testParseExternalFileNullIs() throws FileNotFoundException {
    ContentResolver resolver = mock(ContentResolver.class);
    when(resolver.openInputStream(any(Uri.class))).thenReturn(null);
    when(appContext.getContentResolver()).thenReturn(resolver);

    TestSubscriber<BufferedSource> subscriber = new TestSubscriber<>();
    presenter.parseExternalFile(Uri.EMPTY)
        .subscribe(subscriber);
    subscriber.awaitTerminalEvent();
    subscriber.assertValueCount(1);
    subscriber.assertValue(null);
    subscriber.assertNoErrors();
    subscriber.assertCompleted();
    subscriber.assertUnsubscribed();
  }

  @Test
  public void testParseUriNullFd() throws FileNotFoundException {
    ContentResolver resolver = mock(ContentResolver.class);
    when(resolver.openFileDescriptor(any(Uri.class), anyString())).thenReturn(null);
    when(appContext.getContentResolver()).thenReturn(resolver);

    TestSubscriber<BufferedSource> subscriber = new TestSubscriber<>();
    presenter.parseUri(Uri.EMPTY)
        .subscribe(subscriber);
    subscriber.awaitTerminalEvent();
    subscriber.assertValueCount(1);
    subscriber.assertValue(null);
    subscriber.assertNoErrors();
    subscriber.assertCompleted();
    subscriber.assertUnsubscribed();
  }

  @Test
  public void testParseUriWithException() throws FileNotFoundException {
    ParcelFileDescriptor pfd = mock(ParcelFileDescriptor.class);
    when(pfd.getFd()).thenReturn(-1);

    ContentResolver resolver = mock(ContentResolver.class);
    when(resolver.openFileDescriptor(any(Uri.class), anyString())).thenReturn(pfd);
    when(appContext.getContentResolver()).thenReturn(resolver);

    TestSubscriber<BufferedSource> subscriber = new TestSubscriber<>();
    presenter.parseUri(Uri.EMPTY)
        .subscribe(subscriber);
    subscriber.awaitTerminalEvent();
    subscriber.assertError(NullPointerException.class);
    subscriber.assertValueCount(0);
    subscriber.assertUnsubscribed();
  }
}