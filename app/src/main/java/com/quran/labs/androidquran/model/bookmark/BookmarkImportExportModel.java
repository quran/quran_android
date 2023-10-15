package com.quran.labs.androidquran.model.bookmark;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.quran.data.model.bookmark.BookmarkData;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.mobile.di.qualifier.ApplicationContext;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;


public class BookmarkImportExportModel {
  private static final String FILE_NAME = "quran_android.backup";

  private final Context appContext;
  private final BookmarkJsonModel jsonModel;
  private final BookmarkModel bookmarkModel;

  @Inject
  BookmarkImportExportModel(@ApplicationContext Context appContext,
                            BookmarkJsonModel model, BookmarkModel bookmarkModel) {
    this.appContext = appContext;
    this.jsonModel = model;
    this.bookmarkModel = bookmarkModel;
  }

  public Single<BookmarkData> readBookmarks(@NonNull final BufferedSource source) {
    return Single.defer(() -> Single.just(jsonModel.fromJson(source)))
        .subscribeOn(Schedulers.io());
  }

  public Single<Uri> exportBookmarksObservable() {
    return bookmarkModel.getBookmarkDataObservable(BookmarksDBAdapter.SORT_DATE_ADDED)
        .flatMap(bookmarkData -> Single.just(exportBookmarks(bookmarkData)))
        .subscribeOn(Schedulers.io());
  }

  @NonNull
  private Uri exportBookmarks(BookmarkData data) throws IOException {
    File externalFilesDir = new File(appContext.getExternalFilesDir(null), "backups");
    if (externalFilesDir.exists() || externalFilesDir.mkdir()) {
      File file = new File(externalFilesDir, FILE_NAME);
      BufferedSink sink = Okio.buffer(Okio.sink(file));
      jsonModel.toJson(sink, data);
      sink.close();

      return FileProvider.getUriForFile(
          appContext, appContext.getString(R.string.file_authority), file);
    }
    throw new IOException("Unable to write to external files directory.");
  }

  public Single<Uri> exportBookmarksCSVObservable() {
    return bookmarkModel.getBookmarkDataObservable(BookmarksDBAdapter.SORT_DATE_ADDED)
        .flatMap(bookmarkData -> Single.just(exportBookmarksCSV(bookmarkData)))
        .subscribeOn(Schedulers.io());
  }

  @NonNull
  private Uri exportBookmarksCSV(BookmarkData data) throws IOException {
    File externalFilesDir = new File(appContext.getExternalFilesDir(null), "backups");
    if (externalFilesDir.exists() || externalFilesDir.mkdir()) {
      File file = new File(externalFilesDir, FILE_NAME + ".csv");
      BufferedSink sink = Okio.buffer(Okio.sink(file));
      jsonModel.toCSV(sink, data);
      sink.close();

      return FileProvider.getUriForFile(
          appContext, appContext.getString(R.string.file_authority), file);
    }
    throw new IOException("Unable to write to external files directory.");
  }
}
