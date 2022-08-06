package com.quran.data.model

import com.quran.data.core.QuranConstants
import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranRef.QuranId
import java.io.Serializable

data class SuraAyah(
  @JvmField val sura: Int,
  @JvmField val ayah: Int
) : Comparable<SuraAyah>, Serializable, QuranId {

  override fun compareTo(other: SuraAyah): Int {
    return when {
      this == other -> 0
      sura != other.sura -> sura.compareTo(other.sura)
      else -> ayah.compareTo(other.ayah)
    }
  }

  override fun toString(): String {
    return "($sura:$ayah)"
  }

  fun after(next: SuraAyah): Boolean {
    return this > next
  }

  fun next(quranInfo: QuranInfo): SuraAyah? {
    return if (ayah < quranInfo.getNumberOfAyahs(sura)) {
      SuraAyah(sura, ayah + 1)
    } else if (sura < QuranConstants.NUMBER_OF_SURAS) {
      SuraAyah(sura + 1, 1)
    } else {
      null
    }
  }

  fun prev(quranInfo: QuranInfo): SuraAyah? {
    return if (ayah > 1) {
      SuraAyah(sura, ayah - 1)
    } else if (sura > 1) {
      SuraAyah(sura - 1, quranInfo.getNumberOfAyahs(sura - 1))
    } else {
      null
    }
  }

  fun id(quranInfo: QuranInfo): Int {
    return quranInfo.getAyahId(sura, ayah)
  }

  companion object {
    @JvmStatic fun min(a: SuraAyah, b: SuraAyah): SuraAyah {
      return if (a <= b) a else b
    }

    @JvmStatic fun max(a: SuraAyah, b: SuraAyah): SuraAyah {
      return if (a >= b) a else b
    }
  }
}
