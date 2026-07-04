package com.quran.data.model.bookmark

import kotlin.time.Instant

data class AyahBookmark(
  val sura: Int,
  val ayah: Int,
  val addedDate: Instant,
  val lastUpdated: Instant
)
