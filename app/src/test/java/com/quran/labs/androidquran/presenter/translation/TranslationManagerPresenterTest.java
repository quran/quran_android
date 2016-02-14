package com.quran.labs.androidquran.presenter.translation;

import com.quran.labs.androidquran.common.TranslationList;
import com.quran.labs.androidquran.util.QuranSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import android.content.Context;

import java.io.File;
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import okio.Okio;
import rx.observers.TestSubscriber;

import static com.google.common.truth.Truth.assertThat;

public class TranslationManagerPresenterTest {
  private static final String CLI_ROOT_DIRECTORY = "src/test/resources";

  private MockWebServer mockWebServer;
  private TranslationManagerPresenter translationManager;

  @Before
  public void setup() {
    Context mockAppContext = Mockito.mock(Context.class);
    QuranSettings mockSettings = Mockito.mock(QuranSettings.class);
    OkHttpClient mockOkHttp = new OkHttpClient.Builder().build();
    mockWebServer = new MockWebServer();
    translationManager = new TranslationManagerPresenter(
        mockAppContext, mockOkHttp, mockSettings, mockWebServer.url("").toString()) {
      @Override
      void writeTranslationList(TranslationList list) {
        // no op
      }
    };
  }

  @After
  public void tearDown() {
    try {
      mockWebServer.shutdown();
    } catch (Exception e) {
      // no op
    }
  }

  @Test
  public void testGetCachedTranslationListObservable() {
    TestSubscriber<TranslationList> testSubscriber = new TestSubscriber<>();
    this.translationManager.getCachedTranslationListObservable(true)
        .subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    testSubscriber.assertNoValues();
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
  }

  @Test
  public void getRemoteTranslationListObservable() throws Exception {
    MockResponse mockResponse = new MockResponse();
    File file = new File(CLI_ROOT_DIRECTORY, "translations.json");
    Buffer buffer = new Buffer();
    buffer.writeAll(Okio.source(file));
    mockResponse.setBody(buffer);
    this.mockWebServer.enqueue(mockResponse);

    TestSubscriber<TranslationList> testSubscriber = new TestSubscriber<>();
    this.translationManager.getRemoteTranslationListObservable()
        .subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    testSubscriber.assertValueCount(1);
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    TranslationList list = testSubscriber.getOnNextEvents().get(0);
    assertThat(list.translations).hasSize(50);
  }

  @Test
  public void getRemoteTranslationListObservableIssue() throws Exception {
    MockResponse mockResponse = new MockResponse();
    mockResponse.setResponseCode(500);
    this.mockWebServer.enqueue(mockResponse);

    TestSubscriber<TranslationList> testSubscriber = new TestSubscriber<>();
    this.translationManager.getRemoteTranslationListObservable()
        .subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    testSubscriber.assertNoValues();
    testSubscriber.assertError(IOException.class);
  }
}