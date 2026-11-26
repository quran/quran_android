package com.quran.mobile.mapper.imlaei.data

data class WordAlignment(
  val sura: Int,
  val ayah: Int,
  val audioWordNumber: Int,
  val glyphWordNumbers: List<Int>
)

fun Word_alignment.asWordAlignment(): WordAlignment {
  val parsedGlyphWordNumbers = if (',' !in glyph_word_numbers) {
    glyph_word_numbers.toIntOrNull()?.let(::listOf) ?: emptyList()
  } else {
    glyph_word_numbers.split(",").mapNotNull { it.toIntOrNull() }
  }
  return WordAlignment(
    sura = sura.toInt(),
    ayah = ayah.toInt(),
    audioWordNumber = audio_word_number.toInt(),
    glyphWordNumbers = parsedGlyphWordNumbers
  )
}
