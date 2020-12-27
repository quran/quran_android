package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;

import com.quran.labs.androidquran.dao.translation.TranslationList;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranSettings;

import com.quran.labs.androidquran.util.UrlUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import io.reactivex.observers.TestObserver;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import okio.Okio;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

public class TranslationManagerPresenterTest {
  private static final String CLI_ROOT_DIRECTORY = "src/test/resources";

  private MockWebServer mockWebServer;
  private TranslationManagerPresenter translationManager;

  @Before
  public void setup() {
    Context mockAppContext = mock(Context.class);
    QuranSettings mockSettings = mock(QuranSettings.class);
    OkHttpClient mockOkHttp = new OkHttpClient.Builder().build();
    mockWebServer = new MockWebServer();
    translationManager = new TranslationManagerPresenter(
        mockAppContext, mockOkHttp, mockSettings, null,
        mock(QuranFileUtils.class), new UrlUtil()) {
      @Override
      void writeTranslationList(TranslationList list) {
        // no op
      }
    };
    translationManager.host = mockWebServer.url("").toString();
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
    TestObserver<TranslationList> testObserver = new TestObserver<>();
    this.translationManager.getCachedTranslationListObservable()
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();
    testObserver.assertNoValues();
    testObserver.assertNoErrors();
  }

  @Test
  public void getRemoteTranslationListObservable() throws Exception {
    MockResponse mockResponse = new MockResponse();
    File file = new File(CLI_ROOT_DIRECTORY, "translations.json");
    Buffer buffer = new Buffer();
    buffer.writeAll(Okio.source(file));
    mockResponse.setBody(buffer);
    this.mockWebServer.enqueue(mockResponse);

    TestObserver<TranslationList> testObserver = new TestObserver<>();
    this.translationManager.getRemoteTranslationListObservable()
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();
    testObserver.assertValueCount(1);
    testObserver.assertNoErrors();
    TranslationList list = testObserver.values().get(0);
    assertThat(list.getTranslations()).hasSize(57);
  }

  @Test
  public void getRemoteTranslationListObservableIssue() {
    MockResponse mockResponse = new MockResponse();
    mockResponse.setResponseCode(500);
    this.mockWebServer.enqueue(mockResponse);

    TestObserver<TranslationList> testObserver = new TestObserver<>();
    this.translationManager.getRemoteTranslationListObservable()
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();
    testObserver.assertNoValues();
    testObserver.assertError(IOException.class);
  }
}
