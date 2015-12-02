package com.quran.labs.androidquran.model;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import android.content.Context;
import android.net.Uri;
import android.support.v4.content.FileProvider;

import java.io.File;
import java.io.IOException;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import rx.Observable;
import rx.functions.Func0;
import rx.schedulers.Schedulers;

public class BookmarkImportExportModel {
  private static final String FILE_NAME = "quran_android.backup";

  private final Context appContext;
  private final BookmarkJsonModel jsonModel;
  private final BookmarksDBAdapter databaseAdapter;

  public BookmarkImportExportModel(Context context) {
    this(context.getApplicationContext(),
        new BookmarkJsonModel(), new BookmarksDBAdapter(context));
  }

  public BookmarkImportExportModel(Context appContext,
      BookmarkJsonModel model, BookmarksDBAdapter databaseAdapter) {
    this.appContext = appContext;
    this.jsonModel = model;
    this.databaseAdapter = databaseAdapter;
  }

  public Observable<BookmarkData> readBookmarks(final BufferedSource source) {
    return Observable.defer(new Func0<Observable<BookmarkData>>() {
      @Override
      public Observable<BookmarkData> call() {
        try {
          return Observable.just(jsonModel.fromJson(source));
        } catch (IOException ioe) {
          return Observable.error(ioe);
        }
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Uri> exportBookmarksObservable() {
    return Observable.defer(new Func0<Observable<Uri>>() {
      @Override
      public Observable<Uri> call() {
        try {
          return Observable.just(exportBookmarks());
        } catch (IOException ioe) {
          return Observable.error(ioe);
        }
      }
    }).subscribeOn(Schedulers.io());
  }

  private Uri exportBookmarks() throws IOException {
    BookmarkData data = new BookmarkData(databaseAdapter.getTags(),
        databaseAdapter.getBookmarks(true, BookmarksDBAdapter.SORT_DATE_ADDED));

    Uri result = null;
    File externalFilesDir = new File(appContext.getExternalFilesDir(null), "backups");
    if (externalFilesDir.exists() || externalFilesDir.mkdir()) {
      File file = new File(externalFilesDir, FILE_NAME);
      BufferedSink sink = Okio.buffer(Okio.sink(file));
      jsonModel.toJson(sink, data);
      sink.close();

      result = FileProvider.getUriForFile(
          appContext, appContext.getString(R.string.file_authority), file);
    }
    return result;
  }
}
