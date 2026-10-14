package com.quran.data.model.bookmark

import com.quran.data.model.SuraAyah

sealed interface ReadingBookmarkTarget {
  data class Page(val page: Int) : ReadingBookmarkTarget
  data class Ayah(val suraAyah: SuraAyah) : ReadingBookmarkTarget
}

fun ReadingBookmark.isAt(target: ReadingBookmarkTarget): Boolean {
  return when (this) {
    is PageReadingBookmark -> target is ReadingBookmarkTarget.Page && page == target.page
    is AyahReadingBookmark -> target is ReadingBookmarkTarget.Ayah && asSuraAyah() == target.suraAyah
    is EmptyReadingBookmark -> false
  }
}
