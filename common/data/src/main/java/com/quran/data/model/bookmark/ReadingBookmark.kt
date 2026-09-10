package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

sealed interface ReadingBookmark {
  val timestamp: Long
}

@JsonClass(generateAdapter = true)
data class PageReadingBookmark(
  val page: Int,
  override val timestamp: Long
) : ReadingBookmark

@JsonClass(generateAdapter = true)
data class AyahReadingBookmark(
  val sura: Int,
  val ayah: Int,
  override val timestamp: Long
) : ReadingBookmark
