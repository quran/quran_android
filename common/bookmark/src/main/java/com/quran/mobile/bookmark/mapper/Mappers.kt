package com.quran.mobile.bookmark.mapper

import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag

object Mappers {

  val bookmarkWithTagMapper: ((
    id: Long,
    sura: Int?,
    ayah: Int?,
    page: Int,
    addedDate: Long,
    tagId: Long?
  ) -> Bookmark) = { id, sura, ayah, page, addedDate, tagId ->
    val tags = if (tagId == null) emptyList() else listOf(tagId)
    Bookmark(id, sura, ayah, page, addedDate, tags)
  }

  val recentPageMapper: ((id: Long, page: Int, addedDate: Long) -> RecentPage) =
    { _, page, addedDate -> RecentPage(page, addedDate) }

  val tagMapper: ((id: Long, name: String, addedDate: Long) -> Tag) =
    { id, name, _ -> Tag(id, name) }
}
