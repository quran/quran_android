package com.quran.mobile.bookmark.importdata

import com.quran.data.dao.ReadingBookmarkSlotSettings
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.BackupReadingBookmark
import com.quran.mobile.bookmark.model.LocalReadingBookmarks
import com.quran.mobile.bookmark.model.ReadingBookmarkPageMapper
import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Restores a backup's reading bookmarks kept on the device only, and turns their slots on. */
fun interface LocalReadingBookmarkImporter {
  suspend fun import(bookmarks: List<BackupReadingBookmark>, sourcePageType: String?)
}

@ContributesBinding(AppScope::class)
class LocalReadingBookmarkImporterImpl @Inject constructor(
  private val localBookmarks: LocalReadingBookmarks,
  private val slotSettings: ReadingBookmarkSlotSettings,
  private val pageMapper: ReadingBookmarkPageMapper,
) : LocalReadingBookmarkImporter {
  override suspend fun import(bookmarks: List<BackupReadingBookmark>, sourcePageType: String?) {
    val pageType = pageMapper.currentPageType()
    bookmarks.filterNot { it.slot.synced }.forEach { bookmark ->
      val atMs = bookmark.timestamp.toEpochMilliseconds()
      val sura = bookmark.sura
      val ayah = bookmark.ayah
      val page = bookmark.page
      when {
        bookmark.type == BackupReadingBookmark.TYPE_AYAH && sura != null && ayah != null &&
          pageMapper.isValidSuraAyah(sura, ayah) ->
          localBookmarks.setAyah(bookmark.slot, SuraAyah(sura, ayah), atMs)
        bookmark.type == BackupReadingBookmark.TYPE_PAGE && page != null -> {
          val first = pageMapper.sourcePageToSuraAyah(page, sourcePageType)
          val pageHere = if (sourcePageType == pageType) page else pageMapper.suraAyahToPage(first.sura, first.ayah, pageType)
          localBookmarks.setPage(bookmark.slot, pageHere, pageType, atMs)
        }
        else -> return@forEach
      }
      bookmark.name?.let { localBookmarks.rename(bookmark.slot, it, atMs) }
      slotSettings.setEnabled(bookmark.slot, true)
    }
  }
}
