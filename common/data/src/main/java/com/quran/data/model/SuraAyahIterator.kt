package com.quran.data.model

import com.quran.data.core.QuranInfo

class SuraAyahIterator(
  private val quranInfo: QuranInfo,
  start: SuraAyah,
  end: SuraAyah
) {
  private val start: SuraAyah
  private val end: SuraAyah
  private var started = false

  var sura = 0
    private set
  var ayah = 0
    private set

  val curAyah: SuraAyah
    get() = SuraAyah(sura, ayah)

  init {
    // Sanity check
    if ((start.compareTo(end)) <= 0) {
      this.start = start
      this.end = end
    } else {
      this.start = end
      this.end = start
    }
    reset()
  }

  private fun reset() {
    sura = start.sura
    ayah = start.ayah
    started = false
  }

  private operator fun hasNext(): Boolean {
    return !started || sura < end.sura || ayah < end.ayah
  }

  operator fun next(): Boolean {
    if (!started) {
      started = true
      return true
    } else if (!hasNext()) {
      return false
    }
    if (ayah < quranInfo.getNumberOfAyahs(sura)) {
      ayah++
    } else {
      ayah = 1
      sura++
    }
    return true
  }
}
