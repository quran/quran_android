package com.quran.page.common.data.coordinates

/**
 * Supplies glyph types for ayahinfo databases that only store glyph bounds (no glyph_type column),
 * so features that work on individual words (e.g. hiding words while memorizing) can still find
 * them. Types are returned per ayah as one character per glyph position (1-based position n is
 * index n - 1): [WORD], [PAUSE], [SAJDAH], [HIZB] or [END].
 *
 * Every page type draws the same text with its own font, so the types are worked out from the
 * text together with the [glyphs] of the page type being shown.
 */
interface GlyphTypeResolver {
  /** Types of an ayah's [glyphs] (in position order), or null if they can't be told apart. */
  fun glyphTypes(sura: Int, ayah: Int, glyphs: List<GlyphSize>): String?

  companion object {
    const val WORD = 'w'
    const val PAUSE = 'p'
    const val SAJDAH = 's'
    const val HIZB = 'h'
    const val END = 'e'
  }
}

/** The size of a glyph's bounding box in an ayahinfo database. */
data class GlyphSize(val width: Int, val height: Int)
