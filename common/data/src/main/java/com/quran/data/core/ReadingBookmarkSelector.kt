package com.quran.data.core

import com.quran.data.model.JumpLocation
import com.quran.data.model.Page
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import dev.zacsweers.metro.Inject

class ReadingBookmarkSelector @Inject constructor(private val quranInfo: QuranInfo) {

  fun select(
    location: JumpLocation?,
    bookmarks: List<ReadingBookmark>,
    explicitType: ReadingBookmarkType? = null,
    previousType: ReadingBookmarkType? = null
  ): ReadingBookmarkType {
    val placed = bookmarks.filterNot { it is EmptyReadingBookmark }
    placed.firstOrNull { it.slot == explicitType }?.let { return it.slot }

    if (location is SuraAyah) {
      placed.filterIsInstance<AyahReadingBookmark>()
        .filter { it.asSuraAyah() == location }
        .maxByOrNull { it.timestamp }
        ?.let { return it.slot }
    }

    val page = when (location) {
      is Page -> location.page
      is SuraAyah -> quranInfo.getPageFromSuraAyah(location.sura, location.ayah)
      null -> null
    }
    if (page != null) {
      val pages = placed.map { it to pageOf(it) }
      pages.filter { it.second == page }
        .maxByOrNull { it.first.timestamp }
        ?.let { return it.first.slot }

      val preceding = pages.filter { it.second < page }.sortedByDescending { it.second }
      val closest = preceding.firstOrNull()
      val runnerUp = preceding.getOrNull(1)
      if (closest != null && page - closest.second <= 25 &&
        (runnerUp == null || closest.second - runnerUp.second >= 3)
      ) {
        return closest.first.slot
      }
    }

    return placed.firstOrNull { it.slot == previousType }?.slot
      ?: placed.maxByOrNull { it.timestamp }?.slot
      ?: ReadingBookmarkType.PURPLE
  }

  private fun pageOf(bookmark: ReadingBookmark): Int = when (bookmark) {
    is PageReadingBookmark -> bookmark.page
    is AyahReadingBookmark -> quranInfo.getPageFromSuraAyah(bookmark.sura, bookmark.ayah)
    is EmptyReadingBookmark -> error("Empty bookmarks have no page")
  }
}
