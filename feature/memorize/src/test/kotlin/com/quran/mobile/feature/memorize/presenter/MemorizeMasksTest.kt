package com.quran.mobile.feature.memorize.presenter

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.VerseRange
import com.quran.mobile.feature.memorize.data.Passage
import com.quran.mobile.feature.memorize.data.PassageWord
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.mobile.feature.memorize.tracker.WordId
import org.junit.Test

class MemorizeMasksTest {

  // Al-Ikhlas: 4, 2, 4 and 5 words; the passage starts at 112:1
  private val passage = Passage(
    listOf(4, 2, 4, 5).flatMapIndexed { ayahIndex, count ->
      (1..count).map { word -> PassageWord(WordId(112, ayahIndex + 1, word), "", "") }
    }
  )
  private val page = VerseRange(109, 1, 114, 6, 0)

  private fun state(cursor: Int = 0, mistake: Int? = null, hinted: Set<Int> = emptySet(), missed: Set<Int> = emptySet(), peeking: Boolean = false) =
    MemorizeState(
      id = "test", passage = passage, startedAt = 0, status = MemorizeState.Status.LISTENING,
      cursor = cursor, mistake = mistake, hinted = hinted, missed = missed, peeking = peeking,
    )

  @Test
  fun `untouched ayat are hidden as a whole`() {
    val marks = MemorizeMasks.marks(state(), listOf(page))

    assertThat(marks).containsExactly(
      Mark(112, 1, -1, MemorizeHighlightTypes.HIDDEN),
      Mark(112, 2, -1, MemorizeHighlightTypes.HIDDEN),
      Mark(112, 3, -1, MemorizeHighlightTypes.HIDDEN),
      Mark(112, 4, -1, MemorizeHighlightTypes.HIDDEN),
    )
  }

  @Test
  fun `the ayah being recited is hidden word by word`() {
    val marks = MemorizeMasks.marks(state(cursor = 2), listOf(page))

    assertThat(marks).containsAtLeast(
      Mark(112, 1, 3, MemorizeHighlightTypes.HIDDEN),
      Mark(112, 1, 4, MemorizeHighlightTypes.HIDDEN),
      Mark(112, 2, -1, MemorizeHighlightTypes.HIDDEN),
    )
    assertThat(marks.filter { it.ayah == 1 && it.word in 1..2 }).isEmpty()
  }

  @Test
  fun `a mistake is marked on a word that stays hidden`() {
    val marks = MemorizeMasks.marks(state(cursor = 1, mistake = 1), listOf(page))

    assertThat(marks).containsAtLeast(
      Mark(112, 1, 2, MemorizeHighlightTypes.HIDDEN),
      Mark(112, 1, 2, MemorizeHighlightTypes.MISTAKE),
    )
  }

  @Test
  fun `hinted and skipped words are styled once revealed`() {
    val marks = MemorizeMasks.marks(state(cursor = 4, hinted = setOf(1), missed = setOf(2)), listOf(page))

    assertThat(marks).containsAtLeast(
      Mark(112, 1, 2, MemorizeHighlightTypes.HINTED),
      Mark(112, 1, 3, MemorizeHighlightTypes.MISSED),
    )
    assertThat(marks.none { it.ayah == 1 && it.type == MemorizeHighlightTypes.HIDDEN }).isTrue()
  }

  @Test
  fun `peeking shows everything`() {
    assertThat(MemorizeMasks.marks(state(peeking = true), listOf(page))).isEmpty()
  }

  @Test
  fun `only ayat on the page are masked`() {
    val otherPage = VerseRange(112, 3, 112, 4, 0)

    val marks = MemorizeMasks.marks(state(), listOf(otherPage))

    assertThat(marks.map { it.ayah }.toSet()).containsExactly(3, 4)
  }

  @Test
  fun `an ayah continuing onto the next page is masked on both pages`() {
    val firstPage = VerseRange(112, 1, 112, 2, 0)
    val secondPage = VerseRange(112, 2, 112, 4, 0)

    val first = MemorizeMasks.marks(state(), listOf(firstPage))
    val second = MemorizeMasks.marks(state(), listOf(secondPage))

    assertThat(first).contains(Mark(112, 2, -1, MemorizeHighlightTypes.HIDDEN))
    assertThat(second).contains(Mark(112, 2, -1, MemorizeHighlightTypes.HIDDEN))
  }
}
