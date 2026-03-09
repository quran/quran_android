package com.quran.mobile.feature.voicesearch.matching

enum class MatchType {
  EXACT,
  WORD_OVERLAP,
  FUZZY
}

data class VerseMatch(
  val sura: Int,
  val ayah: Int,
  val verseText: String,
  val score: Float,
  val matchType: MatchType
)
