package com.quran.page.common.data.coordinates

import android.graphics.RectF
import androidx.core.graphics.plus
import com.quran.data.model.AyahGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.AyahWord
import com.quran.data.model.SuraAyah

class PageGlyphsCoords(val page: Int, glyphCoords: List<GlyphCoords>) {

  // key: line number, val: bounds
  private val ayahGlyphs: Map<SuraAyah, List<GlyphCoords>> = glyphCoords.groupBy { it.glyph.ayah }

  // key: line number, val: bounds
  private val lineBounds: Map<Int, RectF> = glyphCoords.groupingBy { it.line }
    .aggregate { _, bounds, glyph, _ -> bounds?.plus(glyph.bounds) ?: RectF(glyph.bounds) }

  /** Get the glyph at the specified position  */
  fun glyph(ayah: SuraAyah, glyphPos: Int): AyahGlyph? =
    ayahGlyphs[ayah]?.find { it.glyph.position == glyphPos }?.glyph

  /** Get all the glyphs on the specified line  */
  fun getAyahGlyphsOnLine(ayah: SuraAyah, line: Int): List<GlyphCoords> =
    ayahGlyphs[ayah]?.filter { it.line == line } ?: emptyList()

  /**
   * Gets the bounds for the ayah or glyph specified by the key
   *
   * @param key an ayah key "[sura]:[ayah]" or glyph key "[sura]:[ayah]:[word]".
   * @param expandVertically whether to expand the bounds vertically to surrounding lines
   *                         such that there are no gaps
   * @param expandHorizontally whether to expand the bounds horizontally to align with other lines
   *                           (glyphs on the edges would be expanded to match the longest line)
   * @return a list of the bounds for the specified key or null
   */
  fun getBounds(key: String, expandVertically: Boolean, expandHorizontally: Boolean): List<RectF>? {
    val split = key.split(":").toTypedArray()
    val ayah = SuraAyah(Integer.valueOf(split[0]), Integer.valueOf(split[1]))
    return when (split.size) {
        2 -> getBoundsForAyah(ayah, expandVertically, expandHorizontally)
        3 -> getBoundsForWord(ayah, Integer.valueOf(split[2]))
        else -> throw IllegalArgumentException("invalid key: $key, expecting _:_ or _:_:_")
    }
  }

  /** Get the bounds for the specified ayah word (not including non-word glyphs)  */
  fun getBoundsForWord(word: AyahWord): List<RectF>? =
    getBoundsForWord(word.ayah, word.wordPosition)

  /** Get the bounds for the specified ayah word (not including non-word glyphs)  */
  fun getBoundsForWord(ayah: SuraAyah, wordPosition: Int): List<RectF>? =
    ayahGlyphs[ayah]
      ?.find { it.glyph is WordGlyph && it.glyph.wordPosition == wordPosition }
      ?.let { getBoundsForGlyph(it) }

  /** Get the bounds for the glyph at the specified position.  */
  fun getBoundsForGlyph(glyph: AyahGlyph): List<RectF>? =
    getBoundsForGlyph(glyph.ayah, glyph.position)

  /** Get the bounds for the glyph at the specified position. Note: glyphPosition here is
   * the position of the glyph (including non-word glyphs like pauses)  */
  fun getBoundsForGlyph(ayah: SuraAyah, glyphPosition: Int): List<RectF>? =
    ayahGlyphs[ayah]
      ?.firstOrNull { it.glyph.position == glyphPosition }
      ?.let { getBoundsForGlyph(it) }

  private fun getBoundsForGlyph(glyph: GlyphCoords): List<RectF> = glyph
    .let { alignVerticalBoundsWithRestOfLine(RectF(it.bounds), it.line) }
    .let { listOf(it) }

  /**
   * Gets the bounds for the specified ayah
   *
   * @param ayah the SuraAyah to get the bounds for
   * @param expandVertically whether to expand the bounds vertically to surrounding lines
   *                         such that there are no gaps
   * @param expandHorizontally whether to expand the bounds horizontally to align with
   *                           other lines (i.e. glyphs on the edges would be expanded
   *                           to match the longest line
   * @return a list of the bounds for the specified ayah or null
   */
  fun getBoundsForAyah(ayah: SuraAyah, expandVertically: Boolean, expandHorizontally: Boolean): List<RectF>? =
    ayahGlyphs[ayah]
      ?.groupingBy { it.line }
      ?.fold(RectF()) { bounds, glyph -> bounds + glyph.bounds }
      ?.mapValues { alignVerticalBoundsWithRestOfLine(it.value, it.key) }
      ?.mapValues { if (expandVertically) expandBoundsVerticallyToSurroundingLines(it.value, it.key) else it.value }
      ?.apply { if (expandHorizontally) expandBoundsHorizontallyToAlignWithOtherLines(this) }
      ?.values?.toList()

  /** Try to align the ayah line bounds with the rest of the line  */
  private fun alignVerticalBoundsWithRestOfLine(bounds: RectF, line: Int): RectF {
    lineBounds[line]?.let { lineCoords ->
      bounds.top = lineCoords.top
      bounds.bottom = lineCoords.bottom
    }
    return bounds
  }

  /** Expand the ayah line bounds to the lines above and below so that there are no gaps  */
  private fun expandBoundsVerticallyToSurroundingLines(bounds: RectF, line: Int): RectF {
    lineBounds[line]?.let { curLine ->
      lineBounds[line - 1]?.let { prevLine -> bounds.top = midpoint(prevLine, curLine) }
      lineBounds[line + 1]?.let { nextLine -> bounds.bottom = midpoint(curLine, nextLine) }
    }
    return bounds
  }

  /** Find the midpoint between two lines (to expand both lines to that midpoint)  */
  private fun midpoint(topLine: RectF, bottomLine: RectF): Float =
    topLine.bottom + (bottomLine.top - topLine.bottom) / 2f

  /** Expands the bounds of the lines in this map such that all lines on the page have the same width */
  private fun expandBoundsHorizontallyToAlignWithOtherLines(ayahLineBoundsMap: Map<Int, RectF>) {
    val pageMinX = pageMinX() ?: return
    val pageMaxX = pageMaxX() ?: return

    ayahLineBoundsMap.forEach {
      lineBounds[it.key]?.let { lineBound ->
        // if the left/right bounds match that of the full line we expand it to the start/end of the page
        if (it.value.left == lineBound.left) it.value.left = pageMinX
        if (it.value.right == lineBound.right) it.value.right = pageMaxX
      }
    }
  }

  /** Get the leftmost bound on this page  */
  private fun pageMinX(): Float? = lineBounds.values.minOfOrNull { it.left }

  /** Get the rightmost bound on this page  */
  private fun pageMaxX(): Float? = lineBounds.values.maxOfOrNull { it.right }

}
