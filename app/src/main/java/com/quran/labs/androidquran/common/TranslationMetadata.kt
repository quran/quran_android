package com.quran.labs.androidquran.common

import com.quran.data.model.SuraAyah

data class TranslationMetadata(
  val sura: Int,
  val ayah: Int,
  val text: String,
  val localTranslationId: Int? = null,
  val link: SuraAyah? = null,
  val linkPageNumber: Int? = null,
  val ayat: List<IntRange> = emptyList(),
  val footnotes: List<IntRange> = emptyList()
)
