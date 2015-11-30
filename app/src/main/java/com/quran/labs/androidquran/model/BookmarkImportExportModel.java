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
import okio.Okio;
import rx.Observable;
import rx.schedulers.Schedulers;

public class BookmarkImportExportModel {
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
        File file = new File(externalFilesDir, "backup.json");
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
}
