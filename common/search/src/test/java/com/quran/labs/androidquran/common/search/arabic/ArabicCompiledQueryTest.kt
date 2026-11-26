package com.quran.labs.androidquran.common.search.arabic

import com.google.common.truth.Truth.assertThat
import com.quran.common.search.arabic.ArabicCharacterHelper
import com.quran.common.search.arabic.ArabicCompiledQuery
import org.junit.Test
import java.util.regex.Pattern

class ArabicCompiledQueryTest {

  @Test
  fun `compiled query matches and highlights identically to Pattern across Arabic equivalence classes`() {
    val verses = listOf(
      "بسم الله الرحمن الرحيم",
      "الله لا إله إلا هو الحي القيوم والله عليم",
      "إياك نعبد وإياك نستعين",
      "وأوحينا إلى موسى أن ألق عصاك",
      "إن الله يحب التوابين ويحب المتطهرين",
      "للل لل للل",
      "قوة توبة هداية هاء ة ت",
      "a.b*c? (literal) a.b*c?"
    )
    val queries = listOf(
      "الله",
      "الرحمن",
      "إياك",
      "موسى",
      "موسي",
      "توبة",
      "لل",
      "اه", // all characters are char-groups (no exact anchor char)
      "(الله)",
      "a.b*c?"
    )

    val matchStart = "<font color=\"-13388315\">"
    val matchEnd = "</font>"
    val buffer = StringBuilder()

    for (query in queries) {
      val compiled = ArabicCompiledQuery.compile(query)
      assertThat(compiled).isNotNull()

      val pattern = Pattern.compile(ArabicCharacterHelper.generateRegex(query))
      val matcher = pattern.matcher("")

      for (verse in verses) {
        val actual = compiled!!.highlightTo(verse, matchStart, matchEnd, buffer)
        matcher.reset(verse)
        val expected = if (matcher.find()) {
          val sb = StringBuilder()
          var lastEnd = 0
          do {
            sb.append(verse, lastEnd, matcher.start())
            sb.append(matchStart)
            sb.append(verse, matcher.start(), matcher.end())
            sb.append(matchEnd)
            lastEnd = matcher.end()
          } while (matcher.find())
          sb.append(verse, lastEnd, verse.length)
          sb.toString()
        } else {
          null
        }
        assertThat(actual).isEqualTo(expected)
      }
    }
  }

  @Test
  fun `empty parentheses-only and surrogate queries return null for Pattern fallback`() {
    assertThat(ArabicCompiledQuery.compile("")).isNull()
    assertThat(ArabicCompiledQuery.compile("()")).isNull()
    assertThat(ArabicCompiledQuery.compile("\uD83D\uDE00")).isNull()
  }

  @Test
  fun `compile caches repeated query instance safely`() {
    val first = ArabicCompiledQuery.compile("الله")
    val second = ArabicCompiledQuery.compile("الله")
    assertThat(second).isSameInstanceAs(first)

    val different = ArabicCompiledQuery.compile("الرحيم")
    assertThat(different).isNotSameInstanceAs(first)
  }
}
