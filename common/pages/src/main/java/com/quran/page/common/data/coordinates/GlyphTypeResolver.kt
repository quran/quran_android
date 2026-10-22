package com.quran.page.common.data.coordinates

/**
 * Supplies glyph types for ayahinfo databases that only store glyph bounds (no glyph_type column),
 * so features that work on individual words (e.g. hiding words while memorizing) can still find
 * them. Types are returned per ayah as one character per glyph position (1-based position n is
 * index n - 1): [WORD], [PAUSE], [SAJDAH], [HIZB] or [END].
 */
interface GlyphTypeResolver {
  fun glyphTypes(sura: Int, ayah: Int): String?

  companion object {
    const val WORD = 'w'
    const val PAUSE = 'p'
    const val SAJDAH = 's'
    const val HIZB = 'h'
    const val END = 'e'
  }
}
