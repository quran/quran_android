package com.quran.mobile.feature.memorize.recording

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.presenter.Mark
import com.quran.mobile.feature.memorize.presenter.MemorizeHighlightTypes
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.mobile.feature.memorize.tracker.WordId
import org.junit.Test
import java.io.File

class SessionMarksTest {

  // 112:1 recited (its second word after a mistake), 112:2:1 hinted, 112:2:2 skipped, 113:1:1 on another page
  private val recording = Recording(
    id = "s", session = "s", mode = MemorizeState.Mode.MEMORIZE, startedAt = 0,
    start = SuraAyah(112, 1), end = SuraAyah(113, 1),
    parts = listOf(Recording.Part(File("a"), 5_000), Recording.Part(File("b"), 5_000)),
    words = listOf(
      word(112, 1, 1, 100, 400),
      word(112, 1, 2, 500, 800, mistakes = 1),
      word(112, 2, 1, 1_000, null, Recording.WordStatus.HINTED),
      word(112, 2, 2, null, null, Recording.WordStatus.MISSED),
      word(113, 1, 1, 6_000, 6_300),
    ),
  )
  private val firstPage: (Recording.Word) -> Boolean = { it.id.sura == 112 }

  @Test
  fun `before playing, the page shows how each word went`() {
    assertThat(SessionMarks.marks(recording, null, firstPage)).containsExactly(
      Mark(112, 1, 2, MemorizeHighlightTypes.MISTAKE),
      Mark(112, 2, 1, MemorizeHighlightTypes.HINTED),
      Mark(112, 2, 2, MemorizeHighlightTypes.MISSED),
    )
  }

  @Test
  fun `while playing, the word heard is lit and the words before it leave a trail`() {
    assertThat(SessionMarks.marks(recording, 600, firstPage)).containsExactly(
      Mark(112, 1, 1, MemorizeHighlightTypes.RECITED),
      Mark(112, 1, 2, MemorizeHighlightTypes.PLAYING),
      Mark(112, 2, 1, MemorizeHighlightTypes.HINTED),
      Mark(112, 2, 2, MemorizeHighlightTypes.MISSED),
    )
    // the next page's word is heard: nothing on this one is
    assertThat(SessionMarks.marks(recording, 6_100, firstPage).map { it.type }).doesNotContain(MemorizeHighlightTypes.PLAYING)
  }

  @Test
  fun `a tap plays from the word, or from the ayah's first word heard`() {
    assertThat(SessionMarks.timeOf(recording, 112, 1, 2)).isEqualTo(500)
    assertThat(SessionMarks.timeOf(recording, 112, 2, 2)).isEqualTo(1_000)
    assertThat(SessionMarks.timeOf(recording, 112, 3, null)).isNull()
  }

  @Test
  fun `a position is found in the part that holds it`() {
    assertThat(RecordingPlayer.partAt(recording.parts, 4_999)).isEqualTo(0 to 4_999L)
    assertThat(RecordingPlayer.partAt(recording.parts, 5_000)).isEqualTo(1 to 0L)
    assertThat(RecordingPlayer.partAt(recording.parts, 12_000)).isEqualTo(1 to 5_000L)
  }

  private fun word(
    sura: Int, ayah: Int, word: Int, start: Long?, end: Long?,
    status: Recording.WordStatus = Recording.WordStatus.RECITED, mistakes: Int = 0,
  ) = Recording.Word(WordId(sura, ayah, word), start, end, status, "", mistakes)
}
