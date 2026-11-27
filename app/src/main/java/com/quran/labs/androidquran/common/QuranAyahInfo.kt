package com.quran.labs.androidquran.common

import com.quran.data.model.SuraAyah
import java.util.Collections

/**
 * QuranAyahInfo
 * TODO: This should become QuranAyah at some point in the future
 * This is because most usages of QuranAyah should actually be usages of SuraAyah
 */
class QuranAyahInfo(
  @JvmField val sura: Int,
  @JvmField val ayah: Int,
  @JvmField val arabicText: String?,
  texts: List<TranslationMetadata>,
  @JvmField val ayahId: Int
) {
  @JvmField
  val texts: List<TranslationMetadata> = Collections.unmodifiableList(texts)

  fun asSuraAyah(): SuraAyah = SuraAyah(sura, ayah)
}
