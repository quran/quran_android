package com.quran.labs.androidquran.util

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.text.HtmlCompat
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class SearchResultFormatterTest {

  @Before
  fun setUp() {
    SearchResultFormatter.clearCache()
  }

  @After
  fun tearDown() {
    SearchResultFormatter.clearCache()
  }

  @Test
  fun `plain text uses fast path without creating unnecessary spans`() {
    val input = "بسم الله الرحمن الرحيم"

    val fast = SearchResultFormatter.parseFastPath(input)
    assertThat(fast).isEqualTo(input)

    val formatted = SearchResultFormatter.format(input)
    assertThat(formatted.toString()).isEqualTo(input)
  }

  @Test
  fun `arabic signed int font color matches HtmlCompat exactly`() {
    val input = "بسم <font color=\"-13388315\">الله</font> الرحمن الرحيم"

    val fast = SearchResultFormatter.parseFastPath(input)
    assertThat(fast).isNotNull()
    assertMatchesHtmlCompat(input, SearchResultFormatter.format(input))
  }

  @Test
  fun `multiple highlights in RTL arabic verse match HtmlCompat exactly`() {
    val input =
      "<font color=\"-13388315\">الله</font> لا إله إلا هو الحي القيوم و<font color=\"-13388315\">الله</font> عليم"

    assertThat(SearchResultFormatter.parseFastPath(input)).isNotNull()
    assertMatchesHtmlCompat(input, SearchResultFormatter.format(input))
  }

  @Test
  fun `hex colors single quotes and bold ellipses match HtmlCompat exactly`() {
    val inputs = listOf(
      "<b>...</b>In the name of <font color=\"#33B5E5\">Allah</font>, the Merciful<b>...</b>",
      "<font color='#33B5E5'>Mercy</font> and <b><font color=\"-13388315\">Guidance</font></b>"
    )

    for (input in inputs) {
      assertThat(SearchResultFormatter.parseFastPath(input)).isNotNull()
      assertMatchesHtmlCompat(input, SearchResultFormatter.format(input))
    }
  }

  @Test
  fun `entities unsupported tags malformed html and whitespace runs fall back to HtmlCompat`() {
    val fallbackInputs = listOf(
      "Mercy &amp; <font color=\"-13388315\">Guidance</font>",
      "<i>Footnote</i> <font color=\"-13388315\">text</font>",
      "Unclosed <font color=\"-13388315\">tag",
      "Mismatched <b><font color=\"-13388315\">tag</b></font>",
      "Named <font color=\"red\">color</font>",
      "<font color=\"#00112233\">EightDigitHex</font>",
      "Two  spaces <font color=\"-13388315\">here</font>",
      "Line\nbreak <font color=\"-13388315\">here</font>",
      " <font color=\"-13388315\">leading</font>",
      "<font color=\"-13388315\">trailing</font> ",
      "<b> leadingInsideTag</b>",
      "<b>trailingInsideTag </b>",
      "a <b> </b>b",
      "a <b> b</b>",
      "<b>a </b> b"
    )

    for (input in fallbackInputs) {
      assertThat(SearchResultFormatter.parseFastPath(input)).isNull()
      assertMatchesHtmlCompat(input, SearchResultFormatter.format(input))
    }
  }

  @Test
  fun `format caches results and clearCache evicts all entries`() {
    val input = "بسم <font color=\"-13388315\">الله</font> الرحمن الرحيم"

    val first = SearchResultFormatter.format(input)
    val second = SearchResultFormatter.format(input)

    assertThat(second).isSameInstanceAs(first)
    assertThat(SearchResultFormatter.cacheSize()).isEqualTo(1)

    SearchResultFormatter.clearCache()
    assertThat(SearchResultFormatter.cacheSize()).isEqualTo(0)
  }

  @Test
  fun `format with overrideHighlightColor recolors font spans on fast zero-TagSoup path`() {
    val input = "بسم <font color=\"-13388315\">الله</font> الرحمن الرحيم"
    val calmGreen = android.graphics.Color.parseColor("#2E6A4E")

    val fast = SearchResultFormatter.parseFastPath(input, overrideHighlightColor = calmGreen)
    assertThat(fast).isNotNull()

    val formatted = SearchResultFormatter.format(input, overrideHighlightColor = calmGreen) as Spanned
    assertThat(formatted.toString()).isEqualTo("بسم الله الرحمن الرحيم")
    val spans = formatted.getSpans(0, formatted.length, ForegroundColorSpan::class.java)
    assertThat(spans).hasLength(1)
    assertThat(spans[0].foregroundColor).isEqualTo(calmGreen)
    assertThat(formatted.getSpanStart(spans[0])).isEqualTo(4)
    assertThat(formatted.getSpanEnd(spans[0])).isEqualTo(8)
  }

  private fun assertMatchesHtmlCompat(rawHtml: String, actual: CharSequence) {
    val expected = HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
    assertThat(actual.toString()).isEqualTo(expected.toString())

    val expectedColorSpans = expected
      .getSpans(0, expected.length, ForegroundColorSpan::class.java)
      .map { Triple(expected.getSpanStart(it), expected.getSpanEnd(it), it.foregroundColor) }
    val actualSpanned = actual as Spanned
    val actualColorSpans = actualSpanned
      .getSpans(0, actualSpanned.length, ForegroundColorSpan::class.java)
      .map { Triple(actualSpanned.getSpanStart(it), actualSpanned.getSpanEnd(it), it.foregroundColor) }
    assertThat(actualColorSpans).isEqualTo(expectedColorSpans)

    val expectedBoldSpans = expected
      .getSpans(0, expected.length, StyleSpan::class.java)
      .filter { it.style == Typeface.BOLD }
      .map { expected.getSpanStart(it) to expected.getSpanEnd(it) }
    val actualBoldSpans = actualSpanned
      .getSpans(0, actualSpanned.length, StyleSpan::class.java)
      .filter { it.style == Typeface.BOLD }
      .map { actualSpanned.getSpanStart(it) to actualSpanned.getSpanEnd(it) }
    assertThat(actualBoldSpans).isEqualTo(expectedBoldSpans)
  }
}
