package com.quran.common.search

import java.text.Normalizer

object SearchTextUtil {
  // via https://stackoverflow.com/questions/51731574/
  private val nonSpacingCombiningCharactersRegex = "\\p{Mn}+".toRegex()
  // extra characters to remove when comparing non-Arabic strings
  private val charactersToReplaceRegex = "['`]".toRegex()

  // alifs with hamzas that we'll replace with \u0627
  private val alifReplacementsRegex = "[\\u0622\\u0623\\u0625\\u0649]".toRegex()
  // waw with hamza to replace with \u0648
  private val wawReplacementsRegex = "\\u0624".toRegex()
  // via https://stackoverflow.com/questions/25562974/
  private val tashkeelRegex = "[\\x{064B}-\\x{065B}]|[\\x{063B}-\\x{063F}]|[\\x{064B}-\\x{065E}]|[\\x{066A}-\\x{06FF}]".toRegex()

  fun asSearchableString(string: String, isRtl: Boolean): String {
    return if (isRtl) {
      string
        .replace(tashkeelRegex, "")
        .replace(alifReplacementsRegex, "\u0627")
        .replace(wawReplacementsRegex, "\u0648")
    } else {
      val normalizedString = Normalizer.normalize(string, Normalizer.Form.NFKD)
      normalizedString
        .replace(nonSpacingCombiningCharactersRegex, "")
        .replace(charactersToReplaceRegex, "")
        .lowercase()
    }
  }

  // Unicode ranges for Arabic diacritics (tashkeel) - more comprehensive than tashkeelRegex above
  private val DIACRITICS_REGEX = Regex("[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06DC\u06DF-\u06E4\u06E7\u06E8\u06EA-\u06ED]")

  // Tatweel (kashida)
  private const val TATWEEL = '\u0640'

  fun normalizeArabic(text: String): String {
    var result = text

    // Remove diacritics (harakat)
    result = DIACRITICS_REGEX.replace(result, "")

    // Remove tatweel
    result = result.replace(TATWEEL.toString(), "")

    // Normalize alif variants to plain alif
    result = result.replace('\u0622', '\u0627') // Alif madda -> Alif
    result = result.replace('\u0623', '\u0627') // Alif hamza above -> Alif
    result = result.replace('\u0625', '\u0627') // Alif hamza below -> Alif
    result = result.replace('\u0671', '\u0627') // Alif wasla -> Alif

    // Normalize taa marbuta to haa
    result = result.replace('\u0629', '\u0647') // Taa marbuta -> Haa

    // Normalize alif maksura to yaa
    result = result.replace('\u0649', '\u064A') // Alif maksura -> Yaa

    // Normalize hamza variants
    result = result.replace('\u0624', '\u0648') // Waw hamza -> Waw
    result = result.replace('\u0626', '\u064A') // Yaa hamza -> Yaa

    return result.trim()
  }

  fun tokenizeArabic(normalizedText: String): List<String> {
    return normalizedText.split(Regex("\\s+")).filter { it.isNotBlank() }
  }

  fun isRtl(s: String): Boolean {
    val characters = s.toCharArray()
    for (character in characters) {
      val directionality = Character.getDirectionality(character).toInt()
      if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT.toInt() ||
        directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC.toInt()
      ) {
        return true
      } else if (directionality == Character.DIRECTIONALITY_LEFT_TO_RIGHT.toInt()) {
        return false
      }
    }
    return false
  }
}
