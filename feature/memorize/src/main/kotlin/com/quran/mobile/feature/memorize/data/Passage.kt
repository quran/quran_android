package com.quran.mobile.feature.memorize.data

import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.tracker.WordId

/** A word to recite, with its pronunciation in context ([wasl]) and on its own ([isolated]). */
data class PassageWord(val id: WordId, val wasl: String, val isolated: String)

/**
 * The words a memorization session expects, in order. A session doesn't end with a sura: more suras
 * are added as the user goes on, until [reachesEnd].
 */
data class Passage(val words: List<PassageWord>) {
  val start: SuraAyah? = words.firstOrNull()?.let { SuraAyah(it.id.sura, it.id.ayah) }

  /** Whether the last word is the last word of the Quran: there is nothing more to load. */
  val reachesEnd: Boolean get() = words.lastOrNull()?.id?.sura == LexiconRepository.LAST_SURA &&
    words.last().id.ayah == LAST_AYAH_OF_QURAN

  operator fun plus(more: Passage): Passage = Passage(words + more.words)

  fun ayahOf(index: Int): SuraAyah = words[index].id.let { SuraAyah(it.sura, it.ayah) }

  /** Whether the word at [index] is the first word of its ayah. */
  fun startsAyah(index: Int): Boolean = index < words.size && words[index].id.word == 1

  /** Whether the word at [index] is the last word of its ayah. */
  fun endsAyah(index: Int): Boolean =
    index < words.size && (index + 1 == words.size || words[index + 1].id.ayah != words[index].id.ayah)

  /** Whether a user about to say the word at [index] is between ayat (see HintScheduler). */
  fun atAyahBoundary(index: Int): Boolean = startsAyah(index) || endsAyah(index)

  private companion object {
    // An-Nas has 6 ayat
    const val LAST_AYAH_OF_QURAN = 6
  }
}
