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
