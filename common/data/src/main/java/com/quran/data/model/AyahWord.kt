package com.quran.data.model

import com.quran.data.model.QuranRef.QuranId
import java.io.Serializable

data class AyahWord(
  @JvmField val ayah: SuraAyah,
  @JvmField val wordPosition: Int
) : Comparable<AyahWord>, Serializable, QuranId {

  override fun compareTo(other: AyahWord): Int {
    return when {
      this == other -> 0
      ayah != other.ayah -> ayah.compareTo(other.ayah)
      else -> wordPosition.compareTo(other.wordPosition)
    }
  }

  override fun toString(): String {
    return "(${ayah.sura}:${ayah.ayah}:$wordPosition)"
  }

}
