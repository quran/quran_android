package com.quran.data.pageinfo.mapper

import com.quran.data.model.QuranText
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange

class IdentityAyahMapper : AyahMapper {
  override fun mapAyah(suraAyah: SuraAyah) = listOf(suraAyah)
  override fun reverseMapAyah(suraAyah: SuraAyah): List<SuraAyah>  = listOf(suraAyah)
  override fun mapRange(verseRange: VerseRange): VerseRange = verseRange
  override fun mapKufiData(targetVerseRange: VerseRange, kufiData: List<QuranText>) = kufiData
}
