package com.quran.labs.androidquran.service.download

import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange

fun SuraAyah.asVerseRangeTo(end: SuraAyah, quranInfo: QuranInfo): VerseRange =
  VerseRange(
    startSura = sura,
    startAyah = ayah,
    endingSura = end.sura,
    endingAyah = end.ayah,
    versesInRange = versesInRangeTo(end, quranInfo)
  )

fun SuraAyah.versesInRangeTo(end: SuraAyah, quranInfo: QuranInfo): Int {
  return if (sura == end.sura) {
    end.ayah - ayah + 1
  } else {
    val ayatInMiddleSuras = ((sura + 1)..<end.sura)
      .sumOf { quranInfo.getNumberOfAyahs(it) }
    val ayatInStartAyah = quranInfo.getNumberOfAyahs(sura) - ayah + 1
    ayatInStartAyah + ayatInMiddleSuras + end.ayah
  }
}
