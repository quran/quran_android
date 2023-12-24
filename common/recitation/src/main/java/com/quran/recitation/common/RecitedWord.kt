package com.quran.recitation.common

import com.quran.data.model.AyahWord

interface RecitedWord {
  fun text(): String

  fun hasTimingInfo(): Boolean
  fun startTime(): Double?
  fun endTime(): Double?

  fun matchedWords(): List<AyahWord>
}
