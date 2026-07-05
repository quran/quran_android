package com.quran.labs.androidquran.model.bookmark

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.dao.RecentPagesDao
import com.quran.data.dao.Settings
import com.quran.data.model.bookmark.BackupReadingBookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.labs.androidquran.R
import com.quran.mobile.bookmark.importdata.BookmarkBackupImportNormalizer
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.bookmark.model.ReadingBookmarkPageMapper
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.runBlocking
import okio.BufferedSource
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException

class BookmarkImportExportModel @Inject internal constructor(
  @param:ApplicationContext private val appContext: Context,
  private val jsonModel: BookmarkJsonModel,
  private val bookmarksDao: BookmarksDao,
  private val recentPagesDao: RecentPagesDao,
  private val readingBookmarksDao: ReadingBookmarksDao,
  private val settings: Settings,
  private val pageMapper: ReadingBookmarkPageMapper,
  private val backupImportNormalizer: BookmarkBackupImportNormalizer,
  private val mobileSyncImporter: MobileSyncImporter
) {
  fun readBookmarks(source: BufferedSource): Single<BookmarkData> {
    return Single.defer {
      Single.just(jsonModel.fromJson(source))
    }
      .subscribeOn(Schedulers.io())
  }

  fun exportBookmarksObservable(): Single<Uri> {
    return bookmarkDataWithRecentsObservable()
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
    return bookmarkDataWithRecentsObservable()
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

  private fun bookmarkDataWithRecentsObservable(): Single<BookmarkData> {
    return Single.fromCallable {
      runBlocking {
        val pageType = pageMapper.currentPageType()
        BookmarkData(
          tags = bookmarksDao.tags(),
          bookmarks = bookmarksDao.bookmarks(BookmarkSortOrder.SORT_DATE_ADDED)
            .filterNot { bookmark -> bookmark.isPageBookmark() },
          recentPages = recentPagesDao.recentPages(),
          readingBookmark = readingBookmarksDao.readingBookmark()
            ?.let { bookmark ->
              BackupReadingBookmark.fromReadingBookmark(bookmark) { sura, ayah ->
                pageMapper.suraAyahToPage(sura, ayah, pageType)
              }
            },
          pageType = pageType
        )
      }
    }.subscribeOn(Schedulers.io())
  }

  fun importBookmarksObservable(data: BookmarkData): Observable<Boolean> {
    return Observable.fromCallable {
      runBlocking {
        importBookmarks(data)
      }
      true
    }.subscribeOn(Schedulers.io()).cache()
  }

  private suspend fun importBookmarks(data: BookmarkData) {
    val importData = backupImportNormalizer.normalize(data)
    if (!importData.isEmpty()) {
      mobileSyncImporter.importData(importData, deleteExisting = false)
    }
  }

  companion object {
    private const val FILE_NAME = "quran_android.backup"
  }
}
