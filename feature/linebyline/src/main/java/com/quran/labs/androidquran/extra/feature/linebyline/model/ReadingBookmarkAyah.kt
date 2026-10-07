package com.quran.labs.androidquran.extra.feature.linebyline.model

import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.mobile.linebyline.data.dao.AyahHighlight
import kotlinx.collections.immutable.ImmutableList

data class ReadingBookmarkAyah(
  val slots: ImmutableList<ReadingBookmarkType>,
  val ayahHighlights: List<AyahHighlight>
)
