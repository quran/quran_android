package com.quran.mobile.bookmark.mapper

import com.quran.data.model.bookmark.Bookmark

/**
 * Converges a list in which commonly tagged [Bookmark]s are listed
 * several times into a list where each [Bookmark] is only visible
 * once.
 */
fun List<Bookmark>.convergeCommonlyTagged(): List<Bookmark> {
  return groupBy { it.id }
    .map {
      val firstBookmark = it.value.first()
      if (it.value.size == 1) {
        firstBookmark
      } else {
        val tagIds =
          it.value.fold(mutableListOf<Long>()) { acc, bookmark ->
            acc.apply { addAll(bookmark.tags) }
          }
        firstBookmark.withTags(tagIds)
      }
    }
}
