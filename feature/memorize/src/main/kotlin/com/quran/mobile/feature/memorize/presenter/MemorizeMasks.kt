package com.quran.mobile.feature.memorize.presenter

import com.quran.data.model.VerseRange
import com.quran.data.model.highlight.HighlightType
import com.quran.mobile.feature.memorize.session.MemorizeState

/** One highlight on a page: a whole ayah when [word] is -1, otherwise a single word. */
internal data class Mark(val sura: Int, val ayah: Int, val word: Int, val type: HighlightType)

/**
 * What a memorization session looks like on the pages showing [ranges]: hidden words (whole ayat
 * as one mask per line when nothing of them has been recited), the mistake marker (on a word that
 * stays hidden), and the styling of hinted and skipped words.
 */
internal object MemorizeMasks {

  fun marks(state: MemorizeState, ranges: Collection<VerseRange>): Set<Mark> {
    val marks = HashSet<Mark>()
    val words = state.passage.words
    for (range in ranges) {
      var index = 0
      while (index < words.size) {
        val id = words[index].id
        if (compare(id.sura, id.ayah, range.endingSura, range.endingAyah) > 0) break
        if (compare(id.sura, id.ayah, range.startSura, range.startAyah) < 0) {
          index++
          continue
        }
        // all the passage words of this ayah
        var end = index
        while (end < words.size && words[end].id.sura == id.sura && words[end].id.ayah == id.ayah) end++
        val ayahWords = index until end
        if (ayahWords.all { state.isHidden(it) }) {
          marks += Mark(id.sura, id.ayah, -1, MemorizeHighlightTypes.HIDDEN)
        } else {
          for (i in ayahWords) {
            val word = words[i].id.word
            when {
              state.isHidden(i) -> marks += Mark(id.sura, id.ayah, word, MemorizeHighlightTypes.HIDDEN)
              i in state.hinted -> marks += Mark(id.sura, id.ayah, word, MemorizeHighlightTypes.HINTED)
              i in state.missed -> marks += Mark(id.sura, id.ayah, word, MemorizeHighlightTypes.MISSED)
            }
          }
        }
        state.mistake?.takeIf { it in ayahWords && state.isHidden(it) }?.let {
          marks += Mark(id.sura, id.ayah, words[it].id.word, MemorizeHighlightTypes.MISTAKE)
        }
        index = end
      }
    }
    return marks
  }

  private fun compare(sura: Int, ayah: Int, otherSura: Int, otherAyah: Int): Int =
    if (sura != otherSura) sura.compareTo(otherSura) else ayah.compareTo(otherAyah)
}
