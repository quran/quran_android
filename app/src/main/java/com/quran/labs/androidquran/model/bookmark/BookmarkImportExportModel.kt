package com.quran.labs.androidquran.model.bookmark

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.quran.data.model.bookmark.BookmarkData
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.mobile.di.qualifier.ApplicationContext
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import okio.BufferedSource
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import javax.inject.Inject

class BookmarkImportExportModel @Inject internal constructor(
  @param:ApplicationContext private val appContext: Context,
  private val jsonModel: BookmarkJsonModel,
  private val bookmarkModel: BookmarkModel
) {
  fun readBookmarks(source: BufferedSource): Single<BookmarkData> {
    return Single.defer {
      Single.just(jsonModel.fromJson(source))
    }
      .subscribeOn(Schedulers.io())
  }

  fun exportBookmarksObservable(): Single<Uri> {
    return bookmarkModel.getBookmarkDataObservable(BookmarksDBAdapter.SORT_DATE_ADDED)
      .flatMap { bookmarkData: BookmarkData ->
        Single.just(
          exportBookmarks(bookmarkData)
        )
      }
      .subscribeOn(Schedulers.io())
  }

  @Throws(IOException::class)
  private fun exportBookmarks(data: BookmarkData): Uri {
    val externalFilesDir = File(appContext.getExternalFilesDir(null), "backups")
    if (externalFilesDir.exists() || externalFilesDir.mkdir()) {
      val file = File(externalFilesDir, FILE_NAME)
      val sink = file.sink().buffer()
      jsonModel.toJson(sink, data)
      sink.close()

      return FileProvider.getUriForFile(
        appContext, appContext.getString(R.string.file_authority), file
      )
    }
    throw IOException("Unable to write to external files directory.")
  }

  fun exportBookmarksCSVObservable(): Single<Uri> {
    return bookmarkModel.getBookmarkDataObservable(BookmarksDBAdapter.SORT_DATE_ADDED)
      .flatMap { bookmarkData: BookmarkData ->
        Single.just(
          exportBookmarksCSV(bookmarkData)
        )
      }
      .subscribeOn(Schedulers.io())
  }

  @Throws(IOException::class)
  private fun exportBookmarksCSV(data: BookmarkData): Uri {
    val externalFilesDir = File(appContext.getExternalFilesDir(null), "backups")
    if (externalFilesDir.exists() || externalFilesDir.mkdir()) {
      val file = File(externalFilesDir, "$FILE_NAME.csv")
      val sink = file.sink().buffer()
      jsonModel.toCSV(sink, data)
      sink.close()

      return FileProvider.getUriForFile(
        appContext, appContext.getString(R.string.file_authority), file
      )
    }
    throw IOException("Unable to write to external files directory.")
  }

  companion object {
    private const val FILE_NAME = "quran_android.backup"
  }
}
