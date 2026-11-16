package com.quran.labs.androidquran.extra.feature.linebyline.presenter

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.highlight.HighlightInfo
import com.quran.labs.androidquran.extra.feature.linebyline.model.HighlightAyah
import com.quran.mobile.linebyline.data.dao.AyahHighlight
import com.quran.mobile.linebyline.data.dao.WordHighlight
import org.junit.Test
import com.quran.data.model.highlight.HighlightType as RecitationType
import com.quran.labs.androidquran.extra.feature.linebyline.model.HighlightType as LineType

class RecitationHighlightsTest {

  private val hidden = RecitationType(1001, 1, RecitationType.Mode.HIDE)
  private val mistake = RecitationType(1002, 2, RecitationType.Mode.UNDERLAY, nightColorResId = 3)

  // 1:1 on the first line, 1:2 over the end of it and the second line
  private val ayah1 = AyahHighlight(1, 1, 1, 1, lineId = 0, left = 0.5f, right = 1f)
  private val ayah2Line0 = AyahHighlight(2, 1, 1, 2, lineId = 0, left = 0f, right = 0.5f)
  private val ayah2Line1 = AyahHighlight(2, 1, 1, 2, lineId = 1, left = 0.2f, right = 1f)
  private val ayahBounds = listOf(ayah1, ayah2Line0, ayah2Line1)

  @Test
  fun `a hidden ayah leaves out its parts of lines`() {
    val result = RecitationHighlights.of(listOf(mark(1, 2, -1, hidden)), ayahBounds, emptyList())
    assertThat(result.hidden).containsExactly(ayah2Line0, ayah2Line1)
    assertThat(result.underText).isEmpty()
  }

  @Test
  fun `without word bounds a word is hidden and marked with its whole ayah`() {
    val marks = listOf(mark(1, 2, 3, hidden), mark(1, 2, 4, hidden), mark(1, 2, 3, mistake))
    val result = RecitationHighlights.of(marks, ayahBounds, emptyList())
    assertThat(result.hidden).containsExactly(ayah2Line0, ayah2Line1)
    assertThat(result.underText).containsExactly(HighlightAyah(LineType.Recitation(2, 3), listOf(ayah2Line0, ayah2Line1)))
  }

  @Test
  fun `with word bounds a word is hidden alone`() {
    val words = listOf(
      WordHighlight(1, 1, 0, 1, 2, 1, left = 0.3f, right = 0.5f),
      WordHighlight(2, 1, 0, 1, 2, 2, left = 0f, right = 0.3f),
    )
    val result = RecitationHighlights.of(listOf(mark(1, 2, 2, hidden)), ayahBounds, words)
    assertThat(result.hidden).containsExactly(AyahHighlight(2, 1, 1, 2, 0, 0f, 0.3f))
  }

  @Test
  fun `marks of ayat elsewhere are not drawn`() {
    val result = RecitationHighlights.of(listOf(mark(2, 1, -1, hidden), mark(2, 1, 1, mistake)), ayahBounds, emptyList())
    assertThat(result.hidden).isEmpty()
    assertThat(result.underText).isEmpty()
  }

  private fun mark(sura: Int, ayah: Int, word: Int, type: RecitationType) =
    HighlightInfo(sura, ayah, word, type, scrollToAyah = false)
}
