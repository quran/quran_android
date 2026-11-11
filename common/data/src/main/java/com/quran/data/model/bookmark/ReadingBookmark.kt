package com.quran.data.model.bookmark

import com.quran.data.model.SuraAyah
import com.squareup.moshi.JsonClass
import kotlin.time.Instant

sealed interface ReadingBookmark {
  val timestamp: Instant
  val slot: ReadingBookmarkType
  val name: String?
}

@JsonClass(generateAdapter = true)
data class PageReadingBookmark(
  override val slot: ReadingBookmarkType,
  val page: Int,
  override val timestamp: Instant,
  override val name: String? = null
) : ReadingBookmark

@JsonClass(generateAdapter = true)
data class AyahReadingBookmark(
  override val slot: ReadingBookmarkType,
  val sura: Int,
  val ayah: Int,
  override val timestamp: Instant,
  override val name: String? = null
) : ReadingBookmark {
  fun asSuraAyah() = SuraAyah(sura = sura, ayah = ayah)
}

@JsonClass(generateAdapter = true)
data class EmptyReadingBookmark(
  override val slot: ReadingBookmarkType,
  override val timestamp: Instant,
  override val name: String? = null
) : ReadingBookmark
