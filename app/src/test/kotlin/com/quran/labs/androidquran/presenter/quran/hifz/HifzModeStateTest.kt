package com.quran.labs.androidquran.presenter.quran.hifz

import com.quran.data.model.SuraAyah
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HifzModeStateTest {

  private val first = SuraAyah(2, 255)
  private val second = SuraAyah(2, 256)
  private val state = HifzModeState()

  private fun twoVerses() = listOf(
    HifzVerse(first, listOf(1, 2, 3)),
    HifzVerse(second, listOf(1, 2))
  )

  @Test
  fun `enable hides every word and verse`() {
    val diffs = state.enable(twoVerses())

    assertTrue(state.isEnabled)
    assertEquals(
      listOf(
        HifzDiff.HideWord(first, 1),
        HifzDiff.HideWord(first, 2),
        HifzDiff.HideWord(first, 3),
        HifzDiff.HideWord(second, 1),
        HifzDiff.HideWord(second, 2)
      ),
      diffs
    )
    assertEquals(2, state.hiddenVerseCount())
  }

  @Test
  fun `wordless ayahs hide and reveal as a whole`() {
    val diffs = state.enable(listOf(HifzVerse(first, emptyList())))

    assertEquals(listOf(HifzDiff.HideAyah(first)), diffs)
    assertEquals(
      listOf(HifzDiff.RevealAyah(first)),
      state.revealNextWord()
    )
    assertEquals(0, state.hiddenVerseCount())
  }

  @Test
  fun `reveal next word walks verses in reading order`() {
    state.enable(twoVerses())

    assertEquals(listOf(HifzDiff.RevealWord(first, 1)), state.revealNextWord())
    assertEquals(listOf(HifzDiff.RevealWord(first, 2)), state.revealNextWord())
    assertEquals(listOf(HifzDiff.RevealWord(first, 3)), state.revealNextWord())
    // first verse fully revealed: moves to the second one
    assertEquals(listOf(HifzDiff.RevealWord(second, 1)), state.revealNextWord())
    assertEquals(1, state.hiddenVerseCount())
  }

  @Test
  fun `reveal next verse reveals the whole verse at once`() {
    state.enable(twoVerses())

    assertEquals(
      listOf(
        HifzDiff.RevealWord(first, 1),
        HifzDiff.RevealWord(first, 2),
        HifzDiff.RevealWord(first, 3)
      ),
      state.revealNextVerse()
    )
    assertEquals(1, state.hiddenVerseCount())
  }

  @Test
  fun `rehide last word undoes one reveal step`() {
    state.enable(twoVerses())
    state.revealNextWord()
    state.revealNextWord()

    assertEquals(listOf(HifzDiff.HideWord(first, 2)), state.rehideLastWord())
    assertEquals(listOf(HifzDiff.RevealWord(first, 2)), state.revealNextWord())
    assertEquals(2, state.hiddenVerseCount())
  }

  @Test
  fun `rehide last verse hides the most recently touched verse`() {
    state.enable(twoVerses())
    state.revealNextVerse()
    state.revealNextWord()

    // last touched verse is the second one: only its word goes back to hidden
    assertEquals(listOf(HifzDiff.HideWord(second, 1)), state.rehideLastVerse())
    assertEquals(1, state.hiddenVerseCount())
    // revealing again continues where it left off
    assertEquals(listOf(HifzDiff.RevealWord(second, 1)), state.revealNextWord())
  }

  @Test
  fun `rehide on empty history is a no-op`() {
    state.enable(twoVerses())

    assertTrue(state.rehideLastWord().isEmpty())
    assertTrue(state.rehideLastVerse().isEmpty())
    assertEquals(2, state.hiddenVerseCount())
  }

  @Test
  fun `operations are no-ops while disabled`() {
    assertTrue(state.revealNextWord().isEmpty())
    assertTrue(state.revealNextVerse().isEmpty())
    assertTrue(state.rehideLastWord().isEmpty())
    assertTrue(state.rehideLastVerse().isEmpty())
    assertFalse(state.isEnabled)
  }

  @Test
  fun `disable resets everything`() {
    state.enable(twoVerses())
    state.revealNextWord()

    assertTrue(state.disable().isEmpty())
    assertFalse(state.isEnabled)
    assertEquals(0, state.hiddenVerseCount())
    assertTrue(state.revealNextWord().isEmpty())
  }

  @Test
  fun `revealing past the end is a no-op`() {
    state.enable(listOf(HifzVerse(first, listOf(1))))

    assertEquals(listOf(HifzDiff.RevealWord(first, 1)), state.revealNextWord())
    assertTrue(state.revealNextWord().isEmpty())
    assertTrue(state.revealNextVerse().isEmpty())
  }
}
