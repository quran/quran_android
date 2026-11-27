package com.quran.mobile.mapper.imlaei.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WordAlignmentTest {

  @Test
  fun `asWordAlignment parses single integer glyph word number`() {
    val row = Word_alignment(
      sura = 2L,
      ayah = 255L,
      audio_word_number = 4L,
      glyph_word_numbers = "13"
    )

    val result = row.asWordAlignment()

    assertThat(result).isEqualTo(
      WordAlignment(
        sura = 2,
        ayah = 255,
        audioWordNumber = 4,
        glyphWordNumbers = listOf(13)
      )
    )
  }

  @Test
  fun `asWordAlignment parses comma separated glyph word numbers`() {
    val row = Word_alignment(
      sura = 1L,
      ayah = 7L,
      audio_word_number = 2L,
      glyph_word_numbers = "3,4,5"
    )

    val result = row.asWordAlignment()

    assertThat(result.glyphWordNumbers).containsExactly(3, 4, 5).inOrder()
  }

  @Test
  fun `asWordAlignment returns empty list for blank or invalid glyph word numbers`() {
    val blank = Word_alignment(1L, 1L, 1L, "").asWordAlignment()
    val invalid = Word_alignment(1L, 1L, 1L, "abc").asWordAlignment()
    val partial = Word_alignment(1L, 1L, 1L, "7,bad,9").asWordAlignment()

    assertThat(blank.glyphWordNumbers).isEmpty()
    assertThat(invalid.glyphWordNumbers).isEmpty()
    assertThat(partial.glyphWordNumbers).containsExactly(7, 9).inOrder()
  }
}
