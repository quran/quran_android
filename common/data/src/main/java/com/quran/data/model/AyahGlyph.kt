package com.quran.data.model

import java.io.Serializable

sealed class AyahGlyph : Comparable<AyahGlyph>, Serializable {

  abstract val ayah: SuraAyah
  abstract val position: Int

  data class    HizbGlyph(override val ayah: SuraAyah, override val position: Int) : AyahGlyph()
  data class  SajdahGlyph(override val ayah: SuraAyah, override val position: Int) : AyahGlyph()
  data class   PauseGlyph(override val ayah: SuraAyah, override val position: Int) : AyahGlyph()
  data class AyahEndGlyph(override val ayah: SuraAyah, override val position: Int) : AyahGlyph()
  data class    WordGlyph(override val ayah: SuraAyah, override val position: Int,
                          val wordPosition: Int) : AyahGlyph() {
    fun toAyahWord(): AyahWord = AyahWord(ayah, wordPosition)
  }

  override fun compareTo(other: AyahGlyph): Int {
    return when {
      this == other -> 0
      ayah != other.ayah -> ayah.compareTo(other.ayah)
      else -> position.compareTo(other.position)
    }
  }

}
