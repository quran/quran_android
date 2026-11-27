package com.quran.labs.androidquran.presenter.quran.hifz

import com.quran.data.model.SuraAyah

/**
 * An ayah on the hifz page with its hideable word positions (1-based, reading
 * order). An empty [words] list means the ayah can only be hidden or revealed
 * as a whole (no per-word glyph data for it).
 */
data class HifzVerse(val ayah: SuraAyah, val words: List<Int>)

/** Granular view operations emitted by [HifzModeState]. */
sealed interface HifzDiff {
  data class HideAyah(val ayah: SuraAyah) : HifzDiff
  data class RevealAyah(val ayah: SuraAyah) : HifzDiff
  data class HideWord(val ayah: SuraAyah, val word: Int) : HifzDiff
  data class RevealWord(val ayah: SuraAyah, val word: Int) : HifzDiff
}

private sealed interface RevealUnit {
  data class Ayah(val ayah: SuraAyah) : RevealUnit
  data class Word(val ayah: SuraAyah, val word: Int) : RevealUnit
}

/**
 * Pure state machine for Hifz (memorization) mode: every verse starts hidden
 * (ayah markers stay visible — they are drawn by a separate layer) and the
 * user reveals content forward word-by-word or verse-by-verse, or re-hides
 * the last revealed units. Emits [HifzDiff]s; rendering stays with the caller.
 */
class HifzModeState {
  var isEnabled: Boolean = false
    private set

  private var verses: List<HifzVerse> = emptyList()
  private val revealedWords = mutableMapOf<SuraAyah, MutableSet<Int>>()
  private val revealedAyahs = mutableSetOf<SuraAyah>()
  private val revealStack = ArrayDeque<RevealUnit>()

  /** Hides every verse; returns the hide operations to apply. */
  fun enable(verses: List<HifzVerse>): List<HifzDiff> {
    isEnabled = true
    this.verses = verses.sortedBy { it.ayah }
    revealedWords.clear()
    revealedAyahs.clear()
    revealStack.clear()
    return this.verses.flatMap { verse ->
      if (verse.words.isEmpty()) {
        listOf(HifzDiff.HideAyah(verse.ayah))
      } else {
        verse.words.map { HifzDiff.HideWord(verse.ayah, it) }
      }
    }
  }

  /**
   * Resets all state. Returns nothing — the caller is expected to clear the
   * whole HIFZ highlight type at once instead of replaying per-unit diffs.
   */
  fun disable(): List<HifzDiff> {
    isEnabled = false
    verses = emptyList()
    revealedWords.clear()
    revealedAyahs.clear()
    revealStack.clear()
    return emptyList()
  }

  private fun hiddenWords(verse: HifzVerse): List<Int> {
    val revealed = revealedWords[verse.ayah].orEmpty()
    return verse.words.filter { it !in revealed }
  }

  private fun hasHiddenContent(verse: HifzVerse): Boolean =
    if (verse.words.isEmpty()) {
      verse.ayah !in revealedAyahs
    } else {
      hiddenWords(verse).isNotEmpty()
    }

  /** Reveals the next hidden word in reading order. */
  fun revealNextWord(): List<HifzDiff> {
    if (!isEnabled) return emptyList()
    for (verse in verses) {
      if (verse.words.isEmpty()) {
        if (verse.ayah !in revealedAyahs) {
          revealedAyahs.add(verse.ayah)
          revealStack.addLast(RevealUnit.Ayah(verse.ayah))
          return listOf(HifzDiff.RevealAyah(verse.ayah))
        }
      } else {
        val next = hiddenWords(verse).firstOrNull() ?: continue
        revealedWords.getOrPut(verse.ayah) { mutableSetOf() }.add(next)
        revealStack.addLast(RevealUnit.Word(verse.ayah, next))
        return listOf(HifzDiff.RevealWord(verse.ayah, next))
      }
    }
    return emptyList()
  }

  /** Reveals the next verse with hidden content in full. */
  fun revealNextVerse(): List<HifzDiff> {
    if (!isEnabled) return emptyList()
    val verse = verses.firstOrNull { hasHiddenContent(it) } ?: return emptyList()
    if (verse.words.isEmpty()) {
      revealedAyahs.add(verse.ayah)
      revealStack.addLast(RevealUnit.Ayah(verse.ayah))
      return listOf(HifzDiff.RevealAyah(verse.ayah))
    }
    return hiddenWords(verse).map { word ->
      revealedWords.getOrPut(verse.ayah) { mutableSetOf() }.add(word)
      revealStack.addLast(RevealUnit.Word(verse.ayah, word))
      HifzDiff.RevealWord(verse.ayah, word)
    }
  }

  /** Re-hides the last revealed unit (undoes one reveal step). */
  fun rehideLastWord(): List<HifzDiff> {
    if (!isEnabled) return emptyList()
    return when (val unit = revealStack.removeLastOrNull()) {
      null -> emptyList()
      is RevealUnit.Ayah -> {
        revealedAyahs.remove(unit.ayah)
        listOf(HifzDiff.HideAyah(unit.ayah))
      }
      is RevealUnit.Word -> {
        revealedWords[unit.ayah]?.remove(unit.word)
        listOf(HifzDiff.HideWord(unit.ayah, unit.word))
      }
    }
  }

  /** Re-hides every revealed unit of the most recently touched verse. */
  fun rehideLastVerse(): List<HifzDiff> {
    if (!isEnabled) return emptyList()
    val last = revealStack.lastOrNull() ?: return emptyList()
    val ayah = when (last) {
      is RevealUnit.Ayah -> last.ayah
      is RevealUnit.Word -> last.ayah
    }
    val diffs = mutableListOf<HifzDiff>()
    while (true) {
      val unit = revealStack.lastOrNull() ?: break
      val unitAyah = when (unit) {
        is RevealUnit.Ayah -> unit.ayah
        is RevealUnit.Word -> unit.ayah
      }
      if (unitAyah != ayah) break
      revealStack.removeLast()
      when (unit) {
        is RevealUnit.Ayah -> {
          revealedAyahs.remove(ayah)
          diffs.add(HifzDiff.HideAyah(ayah))
        }
        is RevealUnit.Word -> {
          revealedWords[ayah]?.remove(unit.word)
          diffs.add(HifzDiff.HideWord(ayah, unit.word))
        }
      }
    }
    return diffs
  }

  fun hiddenVerseCount(): Int = verses.count { hasHiddenContent(it) }

  /** Whether any verses were collected (false when enabled before page data arrived). */
  fun hasVerses(): Boolean = verses.isNotEmpty()
}
