package com.quran.data.model

import java.io.Serializable

data class SuraAyah(
  @JvmField val sura: Int,
  @JvmField val ayah: Int
) : Comparable<SuraAyah>, Serializable {

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

  companion object {
    @JvmStatic fun min(a: SuraAyah, b: SuraAyah): SuraAyah {
      return if (a <= b) a else b
    }

    @JvmStatic fun max(a: SuraAyah, b: SuraAyah): SuraAyah {
      return if (a >= b) a else b
    }
  }
}
