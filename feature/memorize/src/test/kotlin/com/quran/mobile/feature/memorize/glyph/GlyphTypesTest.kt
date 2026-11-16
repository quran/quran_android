package com.quran.mobile.feature.memorize.glyph

import com.google.common.truth.Truth.assertThat
import com.quran.page.common.data.coordinates.GlyphSize
import org.junit.Test

class GlyphTypesTest {

  // sizes: words are 40-130 wide and 50 high; waqf marks float above the line and are short
  private val word = GlyphSize(120, 50)
  private val shortWord = GlyphSize(40, 50)
  private val waqf = GlyphSize(30, 20)
  private val wideWaqf = GlyphSize(140, 22) // e.g. the ج in 38:26
  private val hizb = GlyphSize(60, 60)
  private val sajdah = GlyphSize(50, 45)
  private val end = GlyphSize(150, 60) // the ayah number can be wider than the words

  @Test
  fun `the madani layout gives back the text's own sequence`() {
    val glyphs = listOf(hizb, word, shortWord, waqf, word, end)

    assertThat(GlyphTypes.derive("hwwpwe", glyphs)).isEqualTo("hwwpwe")
  }

  @Test
  fun `a font that places its waqf marks elsewhere gets them where its glyphs are`() {
    // the text has one waqf mark after the second word; this font has two, after words 1 and 3
    val glyphs = listOf(word, wideWaqf, shortWord, word, waqf, word, end)

    assertThat(GlyphTypes.derive("wwpwwe", glyphs)).isEqualTo("wpwwpwe")
  }

  @Test
  fun `a font without waqf marks has only words`() {
    assertThat(GlyphTypes.derive("wwpwwe", listOf(word, shortWord, word, word, end))).isEqualTo("wwwwe")
  }

  @Test
  fun `the sajdah marker stays just before the end`() {
    val glyphs = listOf(word, waqf, word, sajdah, end)

    assertThat(GlyphTypes.derive("wpwse", glyphs)).isEqualTo("wpwse")
  }

  @Test
  fun `a hizb marker drawn in the margin isn't taken for a word`() {
    assertThat(GlyphTypes.derive("hwwe", listOf(word, word, end))).isEqualTo("wwe")
  }

  @Test
  fun `a font that joins words into one glyph can't be told apart`() {
    assertThat(GlyphTypes.derive("wwwe", listOf(word, word, end))).isNull()
    assertThat(GlyphTypes.derive("wwwe", emptyList())).isNull()
  }
}
