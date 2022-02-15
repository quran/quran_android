package com.quran.labs.androidquran.data

import android.graphics.RectF
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.AyahGlyph.HizbGlyph
import com.quran.data.model.AyahGlyph.PauseGlyph
import com.quran.data.model.AyahGlyph.SajdahGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.page.common.data.coordinates.GlyphCoords

/**
 * Helper to convert the ordered glyph rows in the database to structured [GlyphCoords] classes.
 *
 * Usage: add glyphs to the builder in sequence so that word positions can be calculated correctly.
 *
 * Note: It is important that the glyphs are appended in sequence with no gaps,
 *       otherwise the `wordPosition` for [WordGlyph] may be incorrect.
 */
class GlyphsBuilder {
  private val glyphs = mutableListOf<GlyphCoords>()

  private var curAyah: SuraAyah? = null
  private var nextWordPos: Int = 1

  fun append(sura: Int, ayah: Int, glyphPosition: Int, line: Int, type: String, bounds: RectF) {
    val suraAyah = SuraAyah(sura, ayah)

    // If we're on a different ayah, reset word position to 1
    if (curAyah == null || curAyah != suraAyah) {
      curAyah = suraAyah
      nextWordPos = 1
    }

    val glyph = when (type) {
      HIZB   ->    HizbGlyph(suraAyah, glyphPosition)
      SAJDAH ->  SajdahGlyph(suraAyah, glyphPosition)
      PAUSE  ->   PauseGlyph(suraAyah, glyphPosition)
      END    -> AyahEndGlyph(suraAyah, glyphPosition)
      WORD   ->    WordGlyph(suraAyah, glyphPosition, nextWordPos++)
      else   -> throw IllegalArgumentException("Unknown glyph type $type")
    }

    glyphs.add(GlyphCoords(glyph, line, bounds))
  }

  fun build(): List<GlyphCoords> = glyphs.toList()

  // Glyph Type keys
  // Note: it's important these types match what is defined in the ayahinfo db
  private companion object {
    const val HIZB = "hizb"
    const val SAJDAH = "sajdah"
    const val PAUSE = "pause"
    const val END = "end"
    const val WORD = "word"
  }
}
