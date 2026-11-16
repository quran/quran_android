package com.quran.mobile.feature.memorize.recording

import com.quran.mobile.feature.memorize.presenter.Mark
import com.quran.mobile.feature.memorize.presenter.MemorizeHighlightTypes

/**
 * What a recorded session looks like on its pages as it plays back: how each word went (hinted,
 * mistaken, skipped) from the start, and once it plays, the word being heard and the trail of
 * the words before it.
 */
internal object SessionMarks {

  /** The word being heard [positionMs] into [recording] (its index), if any yet. */
  fun wordAt(recording: Recording, positionMs: Long): Int? {
    var current: Int? = null
    recording.words.forEachIndexed { index, word ->
      val time = word.startMs ?: word.endMs ?: return@forEachIndexed
      if (time <= positionMs) current = index
    }
    return current
  }

  /**
   * The marks of the words [onPage] keeps; [positionMs] is where the playback is, or null before it
   * has started.
   */
  fun marks(recording: Recording, positionMs: Long?, onPage: (Recording.Word) -> Boolean): Set<Mark> {
    val playing = positionMs?.let { wordAt(recording, it) }
    val marks = HashSet<Mark>()
    recording.words.forEachIndexed { index, word ->
      if (!onPage(word)) return@forEachIndexed
      val type = when {
        index == playing -> MemorizeHighlightTypes.PLAYING
        word.status == Recording.WordStatus.HINTED -> MemorizeHighlightTypes.HINTED
        (word.mistakes ?: 0) > 0 -> MemorizeHighlightTypes.MISTAKE
        word.status == Recording.WordStatus.MISSED -> MemorizeHighlightTypes.MISSED
        playing != null && index < playing -> MemorizeHighlightTypes.RECITED
        else -> null
      }
      if (type != null) marks += Mark(word.id.sura, word.id.ayah, word.id.word, type)
    }
    return marks
  }

  /** Where to play from for a tap on [ayah] (and [word] if known): its time, or the ayah's first heard. */
  fun timeOf(recording: Recording, sura: Int, ayah: Int, word: Int?): Long? {
    val words = recording.words.filter { it.id.sura == sura && it.id.ayah == ayah }
    val tapped = word?.let { position -> words.firstOrNull { it.id.word == position } }
    return (tapped?.let { it.startMs ?: it.endMs } ?: words.firstNotNullOfOrNull { it.startMs ?: it.endMs })
  }
}
