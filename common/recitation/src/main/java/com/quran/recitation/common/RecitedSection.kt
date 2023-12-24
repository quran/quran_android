package com.quran.recitation.common

import com.quran.data.model.AyahWord
import com.quran.data.model.SuraAyah

interface RecitedSection {
  fun type(): RecitationType
  fun ayah(): SuraAyah
  fun recitedWords(): List<RecitedWord>

  fun startWord(): AyahWord?
  fun endWord(): AyahWord?

  fun prevSection(): RecitedSection?
  fun nextSection(): RecitedSection?

  fun hasTimingInfo(): Boolean
  fun startTime(): Double?
  fun endTime(): Double?
}
