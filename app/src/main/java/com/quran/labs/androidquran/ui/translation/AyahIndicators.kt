package com.quran.labs.androidquran.ui.translation

import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.Tag
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor

data class AyahIndicators(
  val readingBookmarks: List<AyahReadingBookmark> = emptyList(),
  val collections: List<Tag> = emptyList(),
  val highlight: HighlightColor? = null
)

fun ayahIndicators(
  bookmarks: List<Bookmark>,
  tags: List<Tag>,
  highlights: List<Highlight>,
  readingBookmarks: List<ReadingBookmark>
): Map<SuraAyah, AyahIndicators> {
  val tagsById = tags.associateBy { it.id }
  val collections = bookmarks.mapNotNull { bookmark ->
    val sura = bookmark.sura
    val ayah = bookmark.ayah
    if (sura != null && ayah != null) {
      SuraAyah(sura, ayah) to bookmark.tags.mapNotNull { tagsById[it] }
    } else {
      null
    }
  }.toMap()
  val pins = readingBookmarks.filterIsInstance<AyahReadingBookmark>().groupBy { it.asSuraAyah() }
  val colors = highlights.associate { it.suraAyah to it.color }

  return (collections.keys + pins.keys + colors.keys).associateWith { suraAyah ->
    AyahIndicators(
      readingBookmarks = pins[suraAyah].orEmpty().sortedBy { it.slot },
      collections = collections[suraAyah].orEmpty(),
      highlight = colors[suraAyah]
    )
  }
}
