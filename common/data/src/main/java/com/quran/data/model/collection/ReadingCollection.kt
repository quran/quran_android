package com.quran.data.model.collection

import kotlin.time.Instant

data class ReadingCollection(
  val id: String,
  val name: String,
  val lastUpdated: Instant,
  val isSystem: Boolean
)
