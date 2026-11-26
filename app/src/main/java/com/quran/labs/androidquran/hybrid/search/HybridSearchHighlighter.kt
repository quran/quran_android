package com.quran.labs.androidquran.hybrid.search

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.quran.common.search.arabic.ArabicCompiledQuery
import com.quran.labs.androidquran.util.SearchResultFormatter

object HybridSearchHighlighter {

  fun computeArabicRanges(
    text: String,
    compiledQuery: ArabicCompiledQuery?,
    rustVerseIndex: Int = -1,
    rustGeneration: Int = -1
  ): List<HighlightSpanRange> {
    if (text.isEmpty() || compiledQuery == null) {
      return emptyList()
    }
    val packed = SearchResultFormatter.extractArabicMatchRanges(
      text = text,
      compiledQuery = compiledQuery,
      rustVerseIndex = rustVerseIndex,
      rustGeneration = rustGeneration
    )
    return toHighlightRanges(packed, text.length)
  }

  fun extractPlainTextAndRanges(
    rawText: String,
    arabicQueryFallback: ArabicCompiledQuery? = null
  ): Pair<String, List<HighlightSpanRange>> {
    if (rawText.isEmpty()) {
      return "" to emptyList()
    }
    val (plainText, packed) = SearchResultFormatter.extractHtmlHighlightRanges(rawText)
    if (packed.isNotEmpty()) {
      return plainText to toHighlightRanges(packed, plainText.length)
    }
    if (arabicQueryFallback != null) {
      val fallbackPacked = SearchResultFormatter.extractArabicMatchRanges(
        text = plainText,
        compiledQuery = arabicQueryFallback
      )
      return plainText to toHighlightRanges(fallbackPacked, plainText.length)
    }
    return plainText to emptyList()
  }

  fun toHighlightRanges(packed: IntArray, textLength: Int): List<HighlightSpanRange> {
    if (packed.size < 2 || textLength <= 0) {
      return emptyList()
    }
    val ranges = ArrayList<HighlightSpanRange>(packed.size / 2)
    var i = 0
    while (i + 1 < packed.size) {
      val start = packed[i]
      val end = packed[i + 1]
      if (start >= 0 && end > start && end <= textLength) {
        ranges.add(HighlightSpanRange(start = start, end = end))
      }
      i += 2
    }
    return ranges
  }

  /**
   * Builds an [AnnotatedString] with color + subtle background tint ONLY.
   *
   * CRITICAL: Never override `fontWeight` or `fontFamily` inside [SpanStyle], because splitting
   * font weight across a match breaks `UthmanTN1Ver10.otf`'s OpenType GSUB ligature substitution
   * for "الله" (shadda + dagger alif).
   */
  fun buildHighlightedAnnotatedString(
    text: String,
    ranges: List<HighlightSpanRange>,
    highlightColor: Color,
    highlightBackgroundColor: Color
  ): AnnotatedString {
    if (text.isEmpty() || ranges.isEmpty()) {
      return AnnotatedString(text)
    }
    val style = SpanStyle(
      color = highlightColor,
      background = highlightBackgroundColor
    )
    val builder = AnnotatedString.Builder(text)
    for (range in ranges) {
      if (range.start >= 0 && range.end > range.start && range.end <= text.length) {
        builder.addStyle(style, range.start, range.end)
      }
    }
    return builder.toAnnotatedString()
  }

  /**
   * Builds a native [CharSequence] using ONLY [ForegroundColorSpan] (never [android.text.style.BackgroundColorSpan],
   * [android.text.style.StyleSpan], or [android.text.style.TypefaceSpan]) so Uthmani OpenType
   * GSUB ligatures (such as "الله") stay intact while multi-line verse views draw compact rounded
   * highlight backgrounds clamped strictly to glyph bounds (`ascent..descent`).
   */
  fun buildForegroundHighlightedSpan(
    text: String,
    ranges: List<HighlightSpanRange>,
    @ColorInt highlightColor: Int
  ): CharSequence {
    if (ranges.isEmpty() || text.isEmpty()) return text
    val sb = SpannableStringBuilder(text)
    val len = text.length
    for (range in ranges) {
      val start = range.start.coerceIn(0, len)
      val end = range.end.coerceIn(start, len)
      if (start < end) {
        sb.setSpan(
          ForegroundColorSpan(highlightColor),
          start,
          end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
      }
    }
    return sb
  }
}
