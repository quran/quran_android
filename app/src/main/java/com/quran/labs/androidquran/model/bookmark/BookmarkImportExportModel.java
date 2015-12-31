package com.quran.labs.androidquran.model.bookmark;

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
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class BookmarkImportExportModel {
  private static final String FILE_NAME = "quran_android.backup";

  private final Context appContext;
  private final BookmarkJsonModel jsonModel;
  private final BookmarkModel bookmarkModel;

  public BookmarkImportExportModel(Context context) {
    this(context.getApplicationContext(),
        new BookmarkJsonModel(), BookmarkModel.getInstance(context));
  }

  public BookmarkImportExportModel(Context appContext,
      BookmarkJsonModel model, BookmarkModel bookmarkModel) {
    this.appContext = appContext;
    this.jsonModel = model;
    this.bookmarkModel = bookmarkModel;
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
    return bookmarkModel.getBookmarkDataObservable(BookmarksDBAdapter.SORT_DATE_ADDED)
        .flatMap(new Func1<BookmarkData, Observable<Uri>>() {
          @Override
          public Observable<Uri> call(BookmarkData bookmarkData) {
            try {
              return Observable.just(exportBookmarks(bookmarkData));
            } catch (IOException ioe) {
              return Observable.error(ioe);
            }
          }
        }).subscribeOn(Schedulers.io());
  }

  private Uri exportBookmarks(BookmarkData data) throws IOException {
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
