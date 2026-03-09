package com.quran.mobile.feature.voicesearch.matching

data class IndexedVerse(
  val sura: Int,
  val ayah: Int,
  val rawText: String,
  val normalizedText: String,
  val words: List<String>
)
