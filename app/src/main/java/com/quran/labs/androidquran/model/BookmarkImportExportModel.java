package com.quran.labs.androidquran.model;

import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import android.content.Context;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Callable;

import okio.BufferedSink;
import okio.Okio;
import rx.Observable;
import rx.schedulers.Schedulers;

public class BookmarkImportExportModel {
  private final BookmarkJsonModel jsonModel;
  private final BookmarksDBAdapter databaseAdapter;
  private final File externalFilesDir;

  public BookmarkImportExportModel(Context context) {
    jsonModel = new BookmarkJsonModel();
    databaseAdapter = new BookmarksDBAdapter(context);
    externalFilesDir = new File(context.getExternalFilesDir(null), "backups");
  }

  public Observable<Boolean> exportBookmarksObservable() {
    return Observable.fromCallable(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        return exportBookmarks();
      }
    }).subscribeOn(Schedulers.io());
  }

  private boolean exportBookmarks() {
    BookmarkData data = new BookmarkData(databaseAdapter.getTags(),
        databaseAdapter.getBookmarks(true, BookmarksDBAdapter.SORT_DATE_ADDED));

    try {
      if (externalFilesDir.exists() || externalFilesDir.mkdir()) {
        BufferedSink sink = Okio.buffer(Okio.sink(new File(externalFilesDir, "backup.json")));
        jsonModel.toJson(sink, data);
        sink.close();
      }
    } catch (FileNotFoundException ffe) {
      return false;
    } catch (IOException ioe) {
      return false;
    }
    return true;
  }
}
