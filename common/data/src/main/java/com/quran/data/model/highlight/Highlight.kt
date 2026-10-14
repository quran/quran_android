package com.quran.data.model.highlight

import com.quran.data.model.SuraAyah
import kotlin.time.Instant

data class Highlight(
  val suraAyah: SuraAyah,
  val color: HighlightColor,
  val timestamp: Instant
)
