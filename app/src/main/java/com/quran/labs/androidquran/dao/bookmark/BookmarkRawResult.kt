package com.quran.labs.androidquran.dao.bookmark

import com.quran.data.model.bookmark.Tag

data class BookmarkRawResult(
  val rows: List<BookmarkRowData>,
  val tagMap: Map<Long, Tag>
)