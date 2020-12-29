package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;
import android.util.Pair;
import android.util.SparseArray;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.dao.translation.Translation;
import com.quran.labs.androidquran.dao.translation.TranslationItem;
import com.quran.labs.androidquran.dao.translation.TranslationList;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.TranslationManagerActivity;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.UrlUtil;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.Okio;
import timber.log.Timber;

@Singleton
public class TranslationManagerPresenter implements Presenter<TranslationManagerActivity> {
  private static final String WEB_SERVICE_ENDPOINT = "data/translations.php?v=5";
  private static final String CACHED_RESPONSE_FILE_NAME = "translations.v5.cache";

  private final Context appContext;
  private final OkHttpClient okHttpClient;
  private final QuranSettings quranSettings;
  private final QuranFileUtils quranFileUtils;
  private final TranslationsDBAdapter translationsDBAdapter;
  private final UrlUtil urlUtil;

  @VisibleForTesting String host;
  private TranslationManagerActivity currentActivity;

  @Inject
  TranslationManagerPresenter(Context appContext,
                              OkHttpClient okHttpClient,
                              QuranSettings quranSettings,
                              TranslationsDBAdapter dbAdapter,
                              QuranFileUtils quranFileUtils,
                              UrlUtil urlUtil) {
    this.host = Constants.HOST;
    this.appContext = appContext;
    this.okHttpClient = okHttpClient;
    this.quranSettings = quranSettings;
    this.quranFileUtils = quranFileUtils;
    this.translationsDBAdapter = dbAdapter;
    this.urlUtil = urlUtil;
  }

  public void checkForUpdates() {
    getTranslationsList(true);
  }

  public void getTranslationsList(boolean forceDownload) {
    final boolean isCacheStale = System.currentTimeMillis() -
        quranSettings.getLastUpdatedTranslationDate() > Constants.MIN_TRANSLATION_REFRESH_TIME;
    final Observable<TranslationList> source =
        Observable.concat(getCachedTranslationListObservable(), getRemoteTranslationListObservable());
    final Observable<TranslationList> observableSource;
    if (forceDownload) {
      // we only force if we pulled to refresh or are refreshing in the background,
      // implying that we have data on the screen already (or don't need data in the
      // background case), so just get remote data.
      observableSource = getRemoteTranslationListObservable();
    } else if (isCacheStale) {
      observableSource = source;
    } else {
      observableSource = source.take(1);
    }

    observableSource
        .filter(translationList -> !translationList.getTranslations().isEmpty())
        .map(translationList -> mergeWithServerTranslations(translationList.getTranslations()))
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new DisposableObserver<List<TranslationItem>>() {
          @Override
          public void onNext(@NonNull List<TranslationItem> translationItems) {
            if (currentActivity != null) {
              currentActivity.onTranslationsUpdated(translationItems);
            }

            // used for marking upgrades, irrespective of whether or not there is a bound activity
            boolean updatedTranslations = false;
            for (TranslationItem item : translationItems) {
              if (item.needsUpgrade()) {
                updatedTranslations = true;
                break;
              }
            }
            quranSettings.setHaveUpdatedTranslations(updatedTranslations);
          }

          @Override
          public void onError(Throwable e) {
            if (!(e instanceof IOException)) {
              Timber.e(e, "error updating translations list");
            }

            if (currentActivity != null) {
              currentActivity.onErrorDownloadTranslations();
            }
          }

          @Override
          public void onComplete() {
          }
        });
  }

  public void updateItem(final TranslationItem item) {
    Observable.fromCallable(() -> {
          // for upgrades, remove the old file to stop the tafseer from showing up
          // twice. this happens because old and new tafaseer (ex ibn kathir) have
          // different ids when they target different schema versions, and so the
          // old file needs to be removed from the database explicitly
          final Translation translation = item.getTranslation();
          if (translation.getMinimumVersion() >= 5) {
            translationsDBAdapter.deleteTranslationByFile(translation.getFileName());
          }
          return translationsDBAdapter.writeTranslationUpdates(Collections.singletonList(item));
        }
    ).subscribeOn(Schedulers.io())
        .subscribe();
  }

  public void updateItemOrdering(final List<TranslationItem> items) {
    Observable.fromCallable(() -> translationsDBAdapter.writeTranslationUpdates(items))
            .subscribeOn(Schedulers.io())
            .subscribe();
  }

  Observable<TranslationList> getCachedTranslationListObservable() {
    return Observable.defer(() -> {
      try {
        File cachedFile = getCachedFile();
        if (cachedFile.exists()) {
          Moshi moshi = new Moshi.Builder().build();
          JsonAdapter<TranslationList> jsonAdapter = moshi.adapter(TranslationList.class);
          final TranslationList list = jsonAdapter.fromJson(Okio.buffer(Okio.source(cachedFile)));
          if (list != null) {
            return Observable.just(list);
          }
        }
      } catch (Exception e) {
        Timber.e(e);
      }
      return Observable.empty();
    });
  }

  Observable<TranslationList> getRemoteTranslationListObservable() {
    final String url = host + WEB_SERVICE_ENDPOINT;
    return
        downloadTranslationList(url)
            .onErrorResumeNext(downloadTranslationList(urlUtil.fallbackUrl(url)))
            .doOnNext(translationList -> {
              translationList.getTranslations();
              if (!translationList.getTranslations().isEmpty()) {
                writeTranslationList(translationList);
              }
            });
  }

  private Observable<TranslationList> downloadTranslationList(String url) {
    return Observable.fromCallable(() -> {
      Request request = new Request.Builder()
          .url(url)
          .build();
      Response response = okHttpClient.newCall(request).execute();

      Moshi moshi = new Moshi.Builder().build();
      JsonAdapter<TranslationList> jsonAdapter = moshi.adapter(TranslationList.class);

      ResponseBody responseBody = response.body();
      TranslationList result = jsonAdapter.fromJson(responseBody.source());
      responseBody.close();
      return result;
    });
  }

  void writeTranslationList(TranslationList list) {
    File cacheFile = getCachedFile();
    try {
      File directory = cacheFile.getParentFile();
      boolean directoryExists = directory.mkdirs() || directory.isDirectory();
      if (directoryExists) {
        if (cacheFile.exists()) {
          cacheFile.delete();
        }
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<TranslationList> jsonAdapter = moshi.adapter(TranslationList.class);
        BufferedSink sink = Okio.buffer(Okio.sink(cacheFile));
        jsonAdapter.toJson(sink, list);
        sink.close();
        quranSettings.setLastUpdatedTranslationDate(System.currentTimeMillis());
      }
    } catch (Exception e) {
      cacheFile.delete();
      Timber.e(e);
    }
  }

  private File getCachedFile() {
    String dir = quranFileUtils.getQuranDatabaseDirectory(appContext);
    return new File(dir + File.separator + CACHED_RESPONSE_FILE_NAME);
  }

  private List<TranslationItem> mergeWithServerTranslations(List<Translation> serverTranslations) {
    List<TranslationItem> results = new ArrayList<>(serverTranslations.size());
    SparseArray<LocalTranslation> localTranslations = translationsDBAdapter.getTranslationsHash();
    String databaseDir = quranFileUtils.getQuranDatabaseDirectory(appContext);

    List<TranslationItem> updates = new ArrayList<>();
    for (int i = 0, count = serverTranslations.size(); i < count; i++) {
      Translation translation = serverTranslations.get(i);
      LocalTranslation local = localTranslations.get(translation.getId());

      File dbFile = new File(databaseDir, translation.getFileName());
      boolean exists = dbFile.exists();

      TranslationItem item;
      TranslationItem override = null;
      if (exists) {
        if (local == null) {
          final Pair<Integer, Integer> versions = getVersionFromDatabase(translation.getFileName());
          item = new TranslationItem(translation, versions.first);
          if (versions.second != translation.getMinimumVersion()) {
            // schema change, write downloaded schema version to the db and return server item
            override = new TranslationItem(translation.withSchema(versions.second), versions.first);
          }
        } else {
          item = new TranslationItem(translation, local.getVersion(), local.getDisplayOrder());
        }
      } else {
        item = new TranslationItem(translation);
      }

      if (exists && !item.exists()) {
        // delete the file, it has been corrupted
        if (dbFile.delete()) {
          exists = false;
        }
      }

      if ((local == null && exists) || (local != null && !exists)) {
        if (override != null && item.getTranslation().getMinimumVersion() >= 5) {
          // certain schema changes, especially those going to v5, keep the same filename while
          // changing the database entry id. this could cause duplicate entries in the database.
          // work around it by removing the existing entries before doing the updates.
          translationsDBAdapter.deleteTranslationByFile(override.getTranslation().getFileName());
        }
        updates.add(override == null ? item : override);
      } else if (local != null && local.getLanguageCode() == null) {
        // older items don't have a language code
        updates.add(item);
      }
      results.add(item);
    }

    if (!updates.isEmpty()) {
      translationsDBAdapter.writeTranslationUpdates(updates);
    }
    return results;
  }

  private Pair<Integer, Integer> getVersionFromDatabase(String filename) {
    try {
      DatabaseHandler handler =
          DatabaseHandler.getDatabaseHandler(appContext, filename, quranFileUtils);
      if (handler.validDatabase()) {
        return new Pair<>(handler.getTextVersion(), handler.getSchemaVersion());
      }
    } catch (Exception e) {
      Timber.d(e, "exception opening database: %s", filename);
    }
    return new Pair<>(0, 0);
  }


  @Override
  public void bind(TranslationManagerActivity activity) {
    currentActivity = activity;
  }

  @Override
  public void unbind(TranslationManagerActivity activity) {
    if (activity == currentActivity) {
      currentActivity = null;
    }
  }
}
