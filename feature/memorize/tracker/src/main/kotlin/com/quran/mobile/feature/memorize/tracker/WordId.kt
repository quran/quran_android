package com.quran.mobile.feature.memorize.tracker

/**
 * A word on the madani page: [word] is the 1-based glyph word number within the ayah, the same
 * numbering the page images use (WordGlyph.wordPosition).
 */
data class WordId(val sura: Int, val ayah: Int, val word: Int) : Comparable<WordId> {

  override fun compareTo(other: WordId): Int = when {
    sura != other.sura -> sura.compareTo(other.sura)
    ayah != other.ayah -> ayah.compareTo(other.ayah)
    else -> word.compareTo(other.word)
  }

  override fun toString(): String = "$sura:$ayah:$word"
}
