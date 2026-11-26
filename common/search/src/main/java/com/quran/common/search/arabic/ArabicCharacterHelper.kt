package com.quran.common.search.arabic

object ArabicCharacterHelper {
  fun equivalentChars(ch: Char): String? {
    return when (ch) {
      // given: ا -> match: آأإاﻯٱ
      '\u0627' -> "\u0622\u0623\u0625\u0627\u0649\u0671"
      // given: آ / إ / ٱ -> match: آأإاٱ
      '\u0622', '\u0625', '\u0671' -> "\u0622\u0623\u0625\u0627\u0671"
      // given: و / ؤ -> match: وؤ
      '\u0648', '\u0624' -> "\u0648\u0624"
      // given: ﺃ -> match: ﺃﺀﺆﺋ
      '\u0623' -> "\u0621\u0623\u0624\u0626\u0627"
      // given: ﺀ -> match: ﺀﺃﺆ
      '\u0621' -> "\u0621\u0623\u0624\u0626\u0627"
      // given: ﺕ -> match: ﺕﺓ
      '\u062a' -> "\u062a\u0629"
      // given: ﺓ -> match: ﺓتﻫ
      '\u0629' -> "\u0629\u062a\u0647"
      // given: ه -> match: ةه
      '\u0647' -> "\u0647\u0629"
      // given: ﻯ -> match: ﻯي
      '\u0649' -> "\u0649\u064a"
      // given: ي -> match: يﻯ
      '\u064a' -> "\u064a\u0649"
      // given: ئ -> match: ئﻯي (especially helpful for rewayat Warsh)
      '\u0626' -> "\u0626\u0649\u064a"
      else -> null
    }
  }

  private fun isRegexMetaChar(ch: Char): Boolean {
    return when (ch) {
      '\\', '^', '$', '.', '|', '?', '*', '+', '[', ']', '{', '}' -> true
      else -> false
    }
  }

  fun generateRegex(query: String): String {
    val regexBuilder = StringBuilder(query.length)
    for (i in query.indices) {
      val ch = query[i]
      val result = equivalentChars(ch)
      if (result != null) {
        regexBuilder.append('[')
        regexBuilder.append(result)
        regexBuilder.append(']')
      } else if (ch != '(' && ch != ')') {
        if (isRegexMetaChar(ch)) {
          regexBuilder.append('\\')
        }
        regexBuilder.append(ch)
      }
    }
    return regexBuilder.toString()
  }
}
