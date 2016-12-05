package com.quran.labs.androidquran.model.bookmark;

import android.content.Context;
import android.net.Uri;
import android.support.v4.content.FileProvider;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;


public class BookmarkImportExportModel {
  private static final String FILE_NAME = "quran_android.backup";

  private final Context appContext;
  private final BookmarkJsonModel jsonModel;
  private final BookmarkModel bookmarkModel;

  @Inject
  public BookmarkImportExportModel(Context appContext,
      BookmarkJsonModel model, BookmarkModel bookmarkModel) {
    this.appContext = appContext;
    this.jsonModel = model;
    this.bookmarkModel = bookmarkModel;
  }

  public Single<BookmarkData> readBookmarks(final BufferedSource source) {
    return Single.defer(new Callable<SingleSource<BookmarkData>>() {
      @Override
      public SingleSource<BookmarkData> call() throws Exception {
        return Single.just(jsonModel.fromJson(source));
      }
    })
    .subscribeOn(Schedulers.io());
  }

  public Single<Uri> exportBookmarksObservable() {
    return bookmarkModel.getBookmarkDataObservable(BookmarksDBAdapter.SORT_DATE_ADDED)
        .flatMap(new Function<BookmarkData, SingleSource<Uri>>() {
          @Override
          public SingleSource<Uri> apply(BookmarkData bookmarkData) throws Exception {
            return Single.just(exportBookmarks(bookmarkData));
          }
        })
        .subscribeOn(Schedulers.io());
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
