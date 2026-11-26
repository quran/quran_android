package com.quran.labs.androidquran.hybrid.search

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.quran.common.search.arabic.ArabicCompiledQuery
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.util.SearchResultFormatter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridSearchHighlighterTest {

  @Before
  fun setUp() {
    SearchResultFormatter.clearCache()
  }

  @After
  fun tearDown() {
    SearchResultFormatter.clearCache()
  }

  @Test
  fun `computeArabicRanges returns exact UTF-16 ranges for Arabic matches`() {
    val compiled = ArabicCompiledQuery.compile("الله")!!
    val ranges = HybridSearchHighlighter.computeArabicRanges(
      text = "بسم الله الرحمن الرحيم",
      compiledQuery = compiled
    )
    assertThat(ranges).containsExactly(HighlightSpanRange(4, 8))
  }

  @Test
  fun `extractPlainTextAndRanges parses font tags via fast path without TagSoup`() {
    val raw = "In the name of <font color=\"-13388315\">Allah</font>, the Merciful"
    val (plain, ranges) = HybridSearchHighlighter.extractPlainTextAndRanges(raw)
    assertThat(plain).isEqualTo("In the name of Allah, the Merciful")
    assertThat(ranges).containsExactly(HighlightSpanRange(15, 20))
  }

  @Test
  fun `buildHighlightedAnnotatedString preserves FontWeight and FontFamily so Uthmani ligatures stay intact`() {
    val text = "بسم الله الرحمن الرحيم"
    val highlightColor = Color(0xFF96521A)
    val highlightBg = Color(0x2196521A)
    val annotated = HybridSearchHighlighter.buildHighlightedAnnotatedString(
      text = text,
      ranges = listOf(HighlightSpanRange(4, 8)),
      highlightColor = highlightColor,
      highlightBackgroundColor = highlightBg
    )

    assertThat(annotated.text).isEqualTo(text)
    assertThat(annotated.spanStyles).hasSize(1)
    val span = annotated.spanStyles.single()
    assertThat(span.start).isEqualTo(4)
    assertThat(span.end).isEqualTo(8)
    assertThat(span.item.color).isEqualTo(highlightColor)
    assertThat(span.item.background).isEqualTo(highlightBg)
    // CRITICAL: Mutating fontWeight or fontFamily breaks UthmanTN1Ver10.otf GSUB ligature on "الله"
    assertThat(span.item.fontWeight).isNull()
    assertThat(span.item.fontFamily).isNull()
  }
}
