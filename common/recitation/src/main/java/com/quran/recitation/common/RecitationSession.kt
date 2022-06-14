package com.quran.recitation.common

import com.quran.data.model.SuraAyah

interface RecitationSession {
  fun id(): String

  fun startAyah(): SuraAyah
  fun currentAyah(): SuraAyah
  fun startedAt(): Long

  fun recitation(): Recitation
}
