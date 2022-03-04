package com.quran.recitation.common

import com.quran.data.model.SuraAyah

interface RecitedAyah {
  fun ayah(): SuraAyah
  fun recitedSections(): List<RecitedSection>

  fun startTime(): Double? = recitedSections().firstOrNull()?.startTime()
  fun endTime(): Double? = recitedSections().lastOrNull()?.endTime()
}
