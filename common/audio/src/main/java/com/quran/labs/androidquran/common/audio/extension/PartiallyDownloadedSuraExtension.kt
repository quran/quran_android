package com.quran.labs.androidquran.common.audio.extension

import com.quran.labs.androidquran.common.audio.model.PartiallyDownloadedSura

fun PartiallyDownloadedSura.didDownloadAyat(currentSura: Int, start: Int, end: Int): Boolean {
  val ayat = IntRange(start, end)
  return sura == currentSura && ayat.all { it in downloadedAyat }
}
