package com.quran.labs.androidquran.tv.juz

/**
 * Represents a Juz' (section) of the Quran
 */
data class JuzModel(
  val number: Int,
  val name: String,
  val startingPage: Int,
  val endingPage: Int
)

/**
 * Helper to get all 30 Juz with their basic information
 */
fun getAllJuz(): List<JuzModel> {
  val juzStartingPages = listOf(
    1, 22, 42, 62, 82, 102, 121, 142, 162, 182, 201, 222, 242, 262, 282,
    302, 322, 342, 362, 382, 402, 422, 442, 462, 482, 502, 522, 542, 562, 582
  )

  val juzEndingPages = listOf(
    21, 41, 61, 81, 101, 121, 141, 161, 181, 200, 221, 241, 261, 281, 301,
    321, 341, 361, 381, 401, 421, 441, 461, 481, 501, 521, 541, 561, 581, 604
  )

  return (1..30).map { index ->
    JuzModel(
      number = index,
      name = "Juz' ${getArabicNumber(index)}",
      startingPage = juzStartingPages[index - 1],
      endingPage = juzEndingPages[index - 1]
    )
  }
}

/**
 * Convert number to Arabic numerals
 */
private fun getArabicNumber(number: Int): String {
  val arabicNumerals = mapOf(
    0 to '\u0660', 1 to '\u0661', 2 to '\u0662', 3 to '\u0663', 4 to '\u0664',
    5 to '\u0665', 6 to '\u0666', 7 to '\u0667', 8 to '\u0668', 9 to '\u0669'
  )

  return number.toString().map { arabicNumerals[it.toString().toInt()] }.joinToString("")
}
