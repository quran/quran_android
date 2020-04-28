package com.quran.data.model

data class VerseRange(
  @JvmField val startSura: Int,
  @JvmField val startAyah: Int,
  @JvmField val endingSura: Int,
  @JvmField val endingAyah: Int,
  @JvmField val versesInRange: Int
)
