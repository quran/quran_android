package com.quran.labs.androidquran.model.bookmark

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.quran.data.core.QuranInfo
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.dao.RecentPagesDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.BackupReadingBookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.labs.androidquran.R
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
  private val quranInfo: QuranInfo
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
        BookmarkData(
          tags = bookmarksDao.tags(),
          bookmarks = bookmarksDao.bookmarks(BookmarkSortOrder.SORT_DATE_ADDED)
            .filterNot { bookmark -> bookmark.isPageBookmark() },
          recentPages = recentPagesDao.recentPages(),
          readingBookmark = readingBookmarksDao.readingBookmark()
            ?.let(BackupReadingBookmark::fromReadingBookmark)
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
    val tagIdsByName = mutableMapOf<String, Long>()
    bookmarksDao.tags().forEach { tag ->
      if (!tagIdsByName.containsKey(tag.name)) {
        tagIdsByName[tag.name] = tag.id
      }
    }

    val importedTagIdsByBackupId = mutableMapOf<Long, Long>()
    data.tags.forEach { tag ->
      if (tag.name.isNotBlank()) {
        importedTagIdsByBackupId[tag.id] = tagIdForName(tag.name, tagIdsByName)
      }
    }

    val bookmarksToImport = linkedMapOf<SuraAyah, ImportedBookmark>()
    var oldPageBookmarksTagId: Long? = null
    data.bookmarks.forEach { bookmark ->
      val importedTagIds = bookmark.tags.mapNotNull { tagId -> importedTagIdsByBackupId[tagId] }
      val page = bookmark.page
      val normalizedBookmark = if (bookmark.isPageBookmark()) {
        if (!quranInfo.isValidPage(page)) return@forEach
        val oldPageTagId = oldPageBookmarksTagId
          ?: tagIdForName(appContext.getString(R.string.old_page_bookmarks), tagIdsByName)
            .also { oldPageBookmarksTagId = it }
        val bounds = quranInfo.getPageBounds(page)
        NormalizedBookmark(SuraAyah(bounds[0], bounds[1]), page, importedTagIds + oldPageTagId)
      } else {
        val sura = bookmark.sura ?: return@forEach
        val ayah = bookmark.ayah ?: return@forEach
        val ayahPage = validPageForSuraAyah(sura, ayah) ?: return@forEach
        NormalizedBookmark(SuraAyah(sura, ayah), ayahPage, importedTagIds)
      }

      val importedBookmark = bookmarksToImport.getOrPut(normalizedBookmark.suraAyah) {
        ImportedBookmark(
          normalizedBookmark.suraAyah,
          page = normalizedBookmark.page
        )
      }
      importedBookmark.tagIds.addAll(normalizedBookmark.tagIds)
    }

    bookmarksToImport.values.forEach { bookmark ->
      if (!bookmarksDao.isSuraAyahBookmarked(bookmark.suraAyah)) {
        bookmarksDao.toggleAyahBookmark(bookmark.suraAyah, bookmark.page)
      }
      if (bookmark.tagIds.isNotEmpty()) {
        bookmarksDao.updateAyahBookmarkTags(
          suraAyah = bookmark.suraAyah,
          page = bookmark.page,
          tagIds = bookmark.tagIds,
          deleteNonTagged = false
        )
      }
    }

    data.recentPages.asReversed()
      .filter { recentPage -> quranInfo.isValidPage(recentPage.page) }
      .forEach { recentPage -> recentPagesDao.addRecentPage(recentPage.page) }

    data.readingBookmark?.let { readingBookmark ->
      importReadingBookmark(readingBookmark)
    }
  }

  private suspend fun tagIdForName(name: String, tagIdsByName: MutableMap<String, Long>): Long {
    tagIdsByName[name]?.let { return it }
    val tagId = bookmarksDao.addTag(name)
    tagIdsByName[name] = tagId
    return tagId
  }

  private suspend fun importReadingBookmark(readingBookmark: BackupReadingBookmark) {
    when (readingBookmark.type) {
      BackupReadingBookmark.TYPE_AYAH -> {
        val sura = readingBookmark.sura ?: return
        val ayah = readingBookmark.ayah ?: return
        if (validPageForSuraAyah(sura, ayah) != null) {
          readingBookmarksDao.setAyahReadingBookmark(SuraAyah(sura, ayah))
        }
      }
      BackupReadingBookmark.TYPE_PAGE -> {
        val page = readingBookmark.page ?: return
        if (quranInfo.isValidPage(page)) {
          readingBookmarksDao.setPageReadingBookmark(page)
        }
      }
      else -> Unit
    }
  }

  private fun validPageForSuraAyah(sura: Int, ayah: Int): Int? {
    val numberOfAyahs = quranInfo.getNumberOfAyahs(sura)
    if (ayah !in 1..numberOfAyahs) return null
    return quranInfo.getPageFromSuraAyah(sura, ayah)
      .takeIf { page -> quranInfo.isValidPage(page) }
  }

  private data class NormalizedBookmark(
    val suraAyah: SuraAyah,
    val page: Int,
    val tagIds: List<Long>
  )

  private data class ImportedBookmark(
    val suraAyah: SuraAyah,
    val page: Int,
    val tagIds: MutableSet<Long> = mutableSetOf()
  )

  companion object {
    private const val FILE_NAME = "quran_android.backup"
  }
}
