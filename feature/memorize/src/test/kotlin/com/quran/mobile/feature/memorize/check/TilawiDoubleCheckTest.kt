package com.quran.mobile.feature.memorize.check

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.memorize.data.Passage
import com.quran.mobile.feature.memorize.data.PassageWord
import com.quran.mobile.feature.memorize.tracker.WordId
import org.junit.Test

class TilawiDoubleCheckTest {

  // Al-Ikhlas 1 as the lexicon has it, and its clean text
  private val passage = Passage(
    listOf("قُل", "هُوَ", "للَااهُ", "ءَحَدڇ").mapIndexed { i, wasl -> PassageWord(WordId(112, 1, i + 1), wasl, wasl) },
  )
  private val clean = mapOf(112 * 1000 + 1 to "بسم الله الرحمن الرحيم قل هو الله احد")

  // a vocabulary of single letters (id = index) and a word start
  private val alphabet = listOf("▁") + "قلهواحدسمع".map { it.toString() }

  /** A recognizer that "hears" [text]: one piece per letter, a word start before each word. */
  private fun hearing(text: String) = object : PieceRecognizer {
    override fun pieces(audio: FloatArray): IntArray = text.split(' ').flatMap { word ->
      listOf(0) + word.map { alphabet.indexOf(it.toString()) }
    }.toIntArray()
  }

  private fun check(heard: String) =
    TilawiDoubleCheck(hearing(heard), alphabet.map { CheckText.pieceLetters(it) }) { sura, ayah -> clean[sura * 1000 + ayah] }

  private val audio = FloatArray(32_000)

  @Test
  fun `a word heard right after the one before it was said`() {
    assertThat(check("قل هو اله").said(audio, passage, 2)).isTrue()
    // with a letter off (within a quarter of the word)
    assertThat(check("قل هو احد").said(audio, passage, 3)).isFalse()
    assertThat(check("هو اله احد").said(audio, passage, 3)).isTrue()
  }

  @Test
  fun `a word heard on its own isn't enough`() {
    // short words match too much: the word before must be there too
    assertThat(check("اله").said(audio, passage, 2)).isFalse()
    assertThat(check("سمع اله").said(audio, passage, 2)).isFalse()
  }

  @Test
  fun `the first word needs the one after it`() {
    assertThat(check("قل هو").said(audio, passage, 0)).isTrue()
    assertThat(check("قل سمع").said(audio, passage, 0)).isFalse()
  }

  @Test
  fun `too little audio is never a yes`() {
    assertThat(check("قل هو اله").said(FloatArray(8_000), passage, 2)).isFalse()
  }
}
