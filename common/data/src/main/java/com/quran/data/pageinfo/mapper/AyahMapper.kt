package com.quran.data.pageinfo.mapper

import com.quran.data.model.QuranText
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange

interface AyahMapper {
  /**
   * Map the current counting [SuraAyah] value to a list of kufi [SuraAyah] values
   */
  fun mapAyah(suraAyah: SuraAyah): List<SuraAyah>

  /**
   * Map a kufi [SuraAyah] value to a list of the current counting [SuraAyah] values
   */
  fun reverseMapAyah(suraAyah: SuraAyah): List<SuraAyah>

  /**
   * Map the current counting [VerseRange] value to a correspodning kufi [VerseRange] value
   */
  fun mapRange(verseRange: VerseRange): VerseRange

  /**
   * Map kufi counting verse data corresponding to a mapped [VerseRange] of [targetVerseRange] back
   * to the current counting system
   */
  fun mapKufiData(targetVerseRange: VerseRange, kufiData: List<QuranText>): List<QuranText>
}
