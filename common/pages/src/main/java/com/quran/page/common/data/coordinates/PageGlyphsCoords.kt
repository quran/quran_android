package com.quran.page.common.data.coordinates

import android.graphics.RectF
import androidx.core.graphics.plus
import com.quran.data.model.AyahGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.AyahWord
import com.quran.data.model.SuraAyah

class PageGlyphsCoords(val page: Int, glyphCoords: List<GlyphCoords>) {

  // key: line number, val: bounds
  val glyphsByAyah: Map<SuraAyah, List<GlyphCoords>> =
    glyphCoords.groupBy { it.glyph.ayah }

  // key: line number, val: all glyphs on that line
  val glyphsByLine: Map<Int, List<GlyphCoords>> =
    glyphCoords.groupBy { it.line }

  // key: line number, val: bounds
  val lineBounds: Map<Int, RectF> =
    glyphsByLine.mapValues { it.value.fold(RectF()) { line, glyph -> line.plus(glyph.bounds) } }

  // Maximum bounds of all glyphs on the page (union of all glyph bounds)
  val pageBounds: RectF =
    lineBounds.values.fold(RectF()) { page, line -> page.plus(line) }

  // EXPANDED BOUNDS

  private fun suraOfLine(line: Int): Int? =
    glyphsByLine[line]?.firstOrNull()?.glyph?.ayah?.sura

  val expandedLineBounds: Map<Int, RectF> by lazy {
    val firstLine = lineBounds.keys.minOrNull()
    val lastLine = lineBounds.keys.maxOrNull()
    lineBounds.mapValues { (line, bounds) ->
      // Get prev/next lines (unless they are in a different sura)
      val sura = suraOfLine(line)
      val prev = lineBounds[line - 1]?.takeIf { suraOfLine(line - 1) == sura }
      val next = lineBounds[line + 1]?.takeIf { suraOfLine(line + 1) == sura }
      // Expand the bounds horizontally to edges of page, and vertically to adjacent lines
      RectF(bounds).apply {
        // Expand horizontally to page bounds
        left = pageBounds.left
        right = pageBounds.right
        // Expand vertically to adjacent lines
        top = when {
          line == firstLine -> pageBounds.top       // first line -> expand to top edge of page
          prev != null -> midpointY(prev, this)     // expand to adjacent line
          else -> bounds.top                        // leave as is
        }
        bottom = when {
          line == lastLine -> pageBounds.bottom     // last line -> expand to bottom edge of page
          next != null -> midpointY(this, next)     // expand to adjacent line
          else -> bounds.bottom                     // leave as is
        }
      }
    }
  }

  val expandedGlyphsByLine: Map<Int, List<GlyphCoords>> by lazy {
    glyphsByLine.mapValues { (line, glyphs) ->
      glyphs.mapIndexed { idx, glyph ->
        // Get prev/next glyphs
        val prev = glyphs.getOrNull(idx - 1)
        val next = glyphs.getOrNull(idx + 1)
        // Expand the bounds vertically to adjacent lines and horizontally to adjacent glyphs
        val expandedBounds = RectF(glyph.bounds).apply {
          // Expand vertically to adjacent lines
          top = expandedLineBounds[line]!!.top
          bottom = expandedLineBounds[line]!!.bottom
          // Expand horizontally to adjacent glyphs (or edge of line)
          right = when {
            idx == 0 -> lineBounds[line]!!.right                // first glyph -> expand to right edge
            prev != null -> midpointX(this, prev.bounds)        // expand to adjacent glyph
            else -> glyph.bounds.right                          // leave as is
          }
          left = when {
            idx == glyphs.size - 1 -> lineBounds[line]!!.left   // last glyph -> expand to left edge
            next != null -> midpointX(next.bounds, this)        // expand to adjacent glyph
            else -> glyph.bounds.left                           // leave as is
          }
        }
        glyph.copy(bounds = expandedBounds)
      }
    }
  }

  val expandedGlyphs: List<GlyphCoords>
    get() = expandedGlyphsByLine.values.flatten()

  // GLYPH BOUNDS (HIGHLIGHTING)

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
    glyphsByAyah[ayah]
      ?.find { it.glyph is WordGlyph && it.glyph.wordPosition == wordPosition }
      ?.let { getBoundsForGlyph(it) }

  /** Get the bounds for the glyph at the specified position.  */
  fun getBoundsForGlyph(glyph: AyahGlyph): List<RectF>? =
    getBoundsForGlyph(glyph.ayah, glyph.position)

  /** Get the bounds for the glyph at the specified position.
   * Note: glyphPosition here is the position of the glyph including non-word glyphs like pauses */
  fun getBoundsForGlyph(ayah: SuraAyah, glyphPosition: Int): List<RectF>? =
    glyphsByAyah[ayah]
      ?.firstOrNull { it.glyph.position == glyphPosition }
      ?.let { getBoundsForGlyph(it) }

  private fun getBoundsForGlyph(glyph: GlyphCoords): List<RectF> = glyph
    .let { RectF(it.bounds).alignWithLine(it.line) }
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
  private fun getBoundsForAyah(ayah: SuraAyah, expandVertically: Boolean, expandHorizontally: Boolean): List<RectF>? =
    glyphsByAyah[ayah]
      ?.groupingBy { it.line }
      ?.fold(RectF()) { bounds, glyph -> bounds + glyph.bounds }
      ?.mapValues { it.value.alignWithLine(it.key) }
      ?.mapValues { if (expandVertically) it.value.expandToAdjacentLines(it.key) else it.value }
      ?.apply { if (expandHorizontally) expandBoundsHorizontallyToAlignWithOtherLines(this) }
      ?.values?.toList()

  /** Set this bound's top and bottom to that of the specified line */
  private fun RectF.alignWithLine(line: Int): RectF {
    lineBounds[line]?.let { lineCoords ->
      top = lineCoords.top
      bottom = lineCoords.bottom
    }
    return this
  }

  /** Expand this bounds' top and bottom to the lines above and below so that there are no gaps */
  private fun RectF.expandToAdjacentLines(line: Int): RectF {
    lineBounds[line]?.let { curLine ->
      lineBounds[line - 1]?.let { prevLine -> top = midpointY(prevLine, curLine) }
      lineBounds[line + 1]?.let { nextLine -> bottom = midpointY(curLine, nextLine) }
    }
    return this
  }

  /** Find the horizontal midpoint between two bounds (to expand both glyphs to that midpoint)  */
  private fun midpointX(leftBounds: RectF, rightBounds: RectF): Float =
    leftBounds.right + (rightBounds.left - leftBounds.right) / 2f

  /** Find the vertical midpoint between two bounds (to expand both lines to that midpoint)  */
  private fun midpointY(topBounds: RectF, bottomBounds: RectF): Float =
    topBounds.bottom + (bottomBounds.top - topBounds.bottom) / 2f

  /** Expands the bounds of the lines in this map such that all lines on the page have the same width */
  private fun expandBoundsHorizontallyToAlignWithOtherLines(ayahLineBoundsMap: Map<Int, RectF>) {
    val pageMinX = pageBounds.left
    val pageMaxX = pageBounds.right

    ayahLineBoundsMap.forEach {
      lineBounds[it.key]?.let { lineBound ->
        // if the left/right bounds match that of the full line we expand it to the start/end of the page
        if (it.value.left == lineBound.left) it.value.left = pageMinX
        if (it.value.right == lineBound.right) it.value.right = pageMaxX
      }
    }
  }

  // GLYPH BOUNDS (CLICKS)

  fun getLineAtPoint(x: Float, y: Float): Int? {
    // Pass #1: Search for exact match
    val exactMatch = lineBounds
      .filterValues { it.contains(x, y) }
      .firstNotNullOfOrNull { it.key }
    if (exactMatch != null) return exactMatch

    // Pass #2: Search exact match within vertically expanded bounds
    val expandedMatch = expandedLineBounds
      .filterValues { it.contains(x, y) }
      .firstNotNullOfOrNull { it.key }
    if (expandedMatch != null) return expandedMatch

    // No match
    return null
  }

  fun getGlyphAtPoint(x: Float, y: Float): AyahGlyph? {
    // Find the line to use
    val line = getLineAtPoint(x, y) ?: return null

    // Pass #1: Search for exact match
    val exactMatch = glyphsByLine[line]
      ?.firstOrNull { it.bounds.contains(x, y) }
    if (exactMatch != null) return exactMatch.glyph

    // Pass #2: Search exact match within horizontally expanded bounds
    val expandedMatch = expandedGlyphsByLine[line]
      ?.firstOrNull { it.bounds.contains(x, y) }
    if (expandedMatch != null) return expandedMatch.glyph

    // No match
    return null
  }

}
