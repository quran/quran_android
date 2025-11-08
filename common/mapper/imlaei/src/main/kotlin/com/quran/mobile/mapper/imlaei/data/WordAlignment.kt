package com.quran.mobile.mapper.imlaei.data

data class WordAlignment(
  val sura: Int,
  val ayah: Int,
  val audioWordNumber: Int,
  val glyphWordNumbers: List<Int>
)

fun Word_alignment.asWordAlignment(): WordAlignment {
  return WordAlignment(
    sura = sura.toInt(),
    ayah = ayah.toInt(),
    audioWordNumber = audio_word_number.toInt(),
    glyphWordNumbers = glyph_word_numbers.split(",").mapNotNull { it.toIntOrNull() }
  )
}
