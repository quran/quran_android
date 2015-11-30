package com.quran.labs.androidquran.model;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import android.content.Context;
import android.net.Uri;
import android.support.v4.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import rx.Observable;
import rx.schedulers.Schedulers;

public class BookmarkImportExportModel {
  private static final String FILE_NAME = "quran_android.backup";

  private final BookmarkJsonModel jsonModel;
  private final BookmarksDBAdapter databaseAdapter;
  private final File externalFilesDir;
  private final Context appContext;

  public BookmarkImportExportModel(Context context) {
    jsonModel = new BookmarkJsonModel();
    databaseAdapter = new BookmarksDBAdapter(context);
    externalFilesDir = new File(context.getExternalFilesDir(null), "backups");
    appContext = context.getApplicationContext();
  }

  public Observable<Uri> exportBookmarksObservable() {
    return Observable.fromCallable(new Callable<Uri>() {
      @Override
      public Uri call() throws Exception {
        return exportBookmarks();
      }
    }).subscribeOn(Schedulers.io());
  }

  private Uri exportBookmarks() {
    BookmarkData data = new BookmarkData(databaseAdapter.getTags(),
        databaseAdapter.getBookmarks(true, BookmarksDBAdapter.SORT_DATE_ADDED));

    Uri result = null;
    try {
      if (externalFilesDir.exists() || externalFilesDir.mkdir()) {
        File file = new File(externalFilesDir, FILE_NAME);
        BufferedSink sink = Okio.buffer(Okio.sink(file));
        jsonModel.toJson(sink, data);
        sink.close();

        result = FileProvider.getUriForFile(
            appContext, appContext.getString(R.string.file_authority), file);
      }
    } catch (IOException ioe) {
      // probably log at some point
    }
    return result;
  }

  public Observable<BookmarkData> readBookmarks(final BufferedSource source) {
    return Observable.fromCallable(new Callable<BookmarkData>() {
      @Override
      public BookmarkData call() throws Exception {
        try {
          return jsonModel.fromJson(source);
        } catch (IOException ioe) {
          // log this in the future
        }
        return null;
      }
    }).subscribeOn(Schedulers.io());
  }
}
