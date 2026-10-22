package com.quran.mobile.feature.memorize.glyph

import com.quran.page.common.data.coordinates.GlyphSize
import com.quran.page.common.data.coordinates.GlyphTypeResolver.Companion.END
import com.quran.page.common.data.coordinates.GlyphTypeResolver.Companion.HIZB
import com.quran.page.common.data.coordinates.GlyphTypeResolver.Companion.PAUSE
import com.quran.page.common.data.coordinates.GlyphTypeResolver.Companion.SAJDAH
import com.quran.page.common.data.coordinates.GlyphTypeResolver.Companion.WORD

/**
 * Tells an ayah's glyphs apart in any page type's layout, given what the text has in that ayah.
 *
 * The rules of tools/memorize/build_glyph_types.py, which hold for every ayah of the madani pages at
 * every width: the last glyph is the ayah end marker; an ayah with a hizb marker starts with it; one
 * with a sajdah marker has it just before the end; of the other glyphs, as many as the text has words
 * are words and the rest are waqf marks, which are always the shortest glyphs (they float above the
 * line). The number of waqf marks comes from the layout, since fonts don't all place the same ones.
 */
internal object GlyphTypes {

  /** Types for [glyphs] given the text's [sequence] for the ayah, or null if they don't fit. */
  fun derive(sequence: String, glyphs: List<GlyphSize>): String? {
    val words = sequence.count { it == WORD }
    val hizb = sequence.firstOrNull() == HIZB
    val sajdah = SAJDAH in sequence
    // a font may draw a marker in the margin rather than as a glyph of the ayah
    return derive(words, hizb, sajdah, glyphs)
      ?: (if (sajdah) derive(words, hizb, false, glyphs) else null)
      ?: (if (hizb) derive(words, false, sajdah, glyphs) else null)
      ?: (if (hizb && sajdah) derive(words, hizb = false, sajdah = false, glyphs) else null)
  }

  private fun derive(words: Int, hizb: Boolean, sajdah: Boolean, glyphs: List<GlyphSize>): String? {
    val count = glyphs.size
    val first = if (hizb) 1 else 0
    val end = count - 1 - if (sajdah) 1 else 0 // the glyphs between the markers: first until end
    val pauses = end - first - words
    if (count == 0 || end < first || pauses < 0) return null
    val pauseAt = (first until end)
      .sortedWith(compareBy({ glyphs[it].height }, { glyphs[it].width }))
      .take(pauses)
      .toSet()
    return buildString(count) {
      if (hizb) append(HIZB)
      for (i in first until end) append(if (i in pauseAt) PAUSE else WORD)
      if (sajdah) append(SAJDAH)
      append(END)
    }
  }
}
