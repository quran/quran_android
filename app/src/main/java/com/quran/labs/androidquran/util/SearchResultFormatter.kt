package com.quran.labs.androidquran.util

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.collection.LruCache
import androidx.core.text.HtmlCompat
import com.quran.common.search.RustArabicSearchEngine
import com.quran.common.search.arabic.ArabicCompiledQuery

/**
 * Lightweight, bounded-cache formatter for search result snippets.
 *
 * Avoids running TagSoup (`HtmlCompat.fromHtml`) on the main thread during scrolling for the
 * common search markup (`<font color="...">...</font>` and `<b>...</b>`), while falling back to
 * `HtmlCompat.fromHtml` for any other HTML tags, entities, or non-standard markup.
 *
 * Also supports direct zero-HTML highlighting for raw Arabic search results via [formatArabic].
 */
object SearchResultFormatter {
  private const val MAX_CACHE_ENTRIES = 128
  private const val TAG_FONT_PREFIX = "<font "
  private const val TAG_FONT_CLOSE = "</font>"
  private const val TAG_BOLD_OPEN = "<b>"
  private const val TAG_BOLD_CLOSE = "</b>"

  private val cache = LruCache<String, CharSequence>(MAX_CACHE_ENTRIES)
  private val recoloredCache = LruCache<String, CharSequence>(MAX_CACHE_ENTRIES)
  private var lastRecoloredColor: Int = 0
  private val arabicCache = LruCache<String, CharSequence>(MAX_CACHE_ENTRIES)
  private var lastArabicQuery: String? = null
  private var lastArabicColor: Int = 0

  fun format(text: String, @ColorInt overrideHighlightColor: Int? = null): CharSequence {
    if (text.isEmpty()) {
      return text
    }
    if (overrideHighlightColor == null) {
      cache[text]?.let { return it }

      val formatted = parseFastPath(text, overrideHighlightColor = null)
        ?: HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT)
      val immutable: CharSequence = if (formatted is String || formatted is SpannedString) {
        formatted
      } else {
        SpannedString.valueOf(formatted)
      }
      cache.put(text, immutable)
      return immutable
    }

    synchronized(recoloredCache) {
      if (lastRecoloredColor != overrideHighlightColor) {
        recoloredCache.evictAll()
        lastRecoloredColor = overrideHighlightColor
      } else {
        recoloredCache[text]?.let { return it }
      }
    }

    val fast = parseFastPath(text, overrideHighlightColor = overrideHighlightColor)
    val immutable: CharSequence = if (fast != null) {
      if (fast is String || fast is SpannedString) fast else SpannedString.valueOf(fast)
    } else {
      val parsed = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT)
      val builder = SpannableStringBuilder(parsed)
      val existingSpans = builder.getSpans(0, builder.length, ForegroundColorSpan::class.java)
      for (span in existingSpans) {
        val start = builder.getSpanStart(span)
        val end = builder.getSpanEnd(span)
        val flags = builder.getSpanFlags(span)
        builder.removeSpan(span)
        if (start >= 0 && end > start) {
          builder.setSpan(ForegroundColorSpan(overrideHighlightColor), start, end, flags)
        }
      }
      SpannedString(builder)
    }

    synchronized(recoloredCache) {
      if (lastRecoloredColor == overrideHighlightColor) {
        recoloredCache.put(text, immutable)
      }
    }
    return immutable
  }

  /**
   * Highlights raw Arabic verse [text] directly with [ForegroundColorSpan] using native Rust
   * UTF-16 spans when available or [compiledQuery] fallback, avoiding upfront `<font>` HTML
   * serialization and subsequent HTML parsing.
   *
   * Falls back to [format] (preserving [highlightColor]) if [text] already contains HTML markup
   * or [compiledQuery] is null.
   */
  fun formatArabic(
    text: String,
    compiledQuery: ArabicCompiledQuery?,
    @ColorInt highlightColor: Int,
    rustVerseIndex: Int = -1,
    rustGeneration: Int = -1
  ): CharSequence {
    if (text.isEmpty()) {
      return text
    }
    if (compiledQuery == null || text.indexOf('<') >= 0) {
      return format(text, overrideHighlightColor = highlightColor)
    }

    val queryKey = compiledQuery.query
    synchronized(arabicCache) {
      if (lastArabicQuery != queryKey || lastArabicColor != highlightColor) {
        arabicCache.evictAll()
        lastArabicQuery = queryKey
        lastArabicColor = highlightColor
      } else {
        arabicCache[text]?.let { return it }
      }
    }

    val nativeSpans = if (rustVerseIndex >= 0 && rustGeneration > 0) {
      RustArabicSearchEngine.findMatchSpans(rustVerseIndex, queryKey, rustGeneration)
    } else {
      null
    }

    val result: CharSequence = if (nativeSpans != null) {
      if (nativeSpans.isEmpty()) {
        text
      } else {
        val builder = SpannableStringBuilder(text)
        var applied = false
        var i = 0
        while (i + 1 < nativeSpans.size) {
          val start = nativeSpans[i]
          val end = nativeSpans[i + 1]
          if (start >= 0 && end > start && end <= text.length) {
            builder.setSpan(
              ForegroundColorSpan(highlightColor),
              start,
              end,
              Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            applied = true
          }
          i += 2
        }
        if (applied) SpannedString(builder) else text
      }
    } else {
      var start = compiledQuery.findFirst(text, 0)
      if (start < 0) {
        text
      } else {
        val builder = SpannableStringBuilder(text)
        val matchLen = compiledQuery.length
        while (start >= 0) {
          val end = start + matchLen
          builder.setSpan(
            ForegroundColorSpan(highlightColor),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
          )
          start = compiledQuery.findFirst(text, end)
        }
        SpannedString(builder)
      }
    }

    synchronized(arabicCache) {
      if (lastArabicQuery == queryKey && lastArabicColor == highlightColor) {
        arabicCache.put(text, result)
      }
    }
    return result
  }

  /**
   * Returns packed UTF-16 match spans `[start0, end0, start1, end1, ...]` for raw Arabic [text],
   * preferring native Rust spans when [rustVerseIndex] and [rustGeneration] are valid and falling
   * back to [compiledQuery].
   */
  fun extractArabicMatchRanges(
    text: String,
    compiledQuery: ArabicCompiledQuery?,
    rustVerseIndex: Int = -1,
    rustGeneration: Int = -1
  ): IntArray {
    if (text.isEmpty() || compiledQuery == null || text.indexOf('<') >= 0) {
      return IntArray(0)
    }
    val nativeSpans = if (rustVerseIndex >= 0 && rustGeneration > 0) {
      RustArabicSearchEngine.findMatchSpans(rustVerseIndex, compiledQuery.query, rustGeneration)
    } else {
      null
    }
    if (nativeSpans != null) {
      return nativeSpans
    }
    var start = compiledQuery.findFirst(text, 0)
    if (start < 0) {
      return IntArray(0)
    }
    val matchLen = compiledQuery.length
    val ranges = ArrayList<Int>(4)
    while (start >= 0) {
      val end = start + matchLen
      if (end <= text.length) {
        ranges.add(start)
        ranges.add(end)
      }
      start = compiledQuery.findFirst(text, end)
    }
    return ranges.toIntArray()
  }

  /**
   * Parses HTML search snippets via [format] (TagSoup-free fast path + bounded cache) and
   * returns the plain text paired with packed UTF-16 `[start, end, ...]` highlight ranges.
   */
  fun extractHtmlHighlightRanges(text: String): Pair<String, IntArray> {
    if (text.isEmpty()) {
      return "" to IntArray(0)
    }
    if (text.indexOf('<') < 0 && text.indexOf('&') < 0) {
      return text to IntArray(0)
    }
    val formatted = format(text)
    val plain = formatted.toString()
    if (formatted !is Spanned) {
      return plain to IntArray(0)
    }
    val spans = formatted.getSpans(0, formatted.length, ForegroundColorSpan::class.java)
    if (spans.isEmpty()) {
      return plain to IntArray(0)
    }
    val packed = IntArray(spans.size * 2)
    var count = 0
    for (span in spans) {
      val s = formatted.getSpanStart(span)
      val e = formatted.getSpanEnd(span)
      if (s >= 0 && e > s && e <= plain.length) {
        packed[count++] = s
        packed[count++] = e
      }
    }
    val finalPacked = if (count == packed.size) packed else packed.copyOf(count)
    return plain to finalPacked
  }

  fun clearCache() {
    cache.evictAll()
    synchronized(recoloredCache) {
      recoloredCache.evictAll()
      lastRecoloredColor = 0
    }
    synchronized(arabicCache) {
      arabicCache.evictAll()
      lastArabicQuery = null
      lastArabicColor = 0
    }
  }

  @VisibleForTesting
  internal fun cacheSize(): Int = cache.size()

  @VisibleForTesting
  internal fun arabicCacheSize(): Int = synchronized(arabicCache) { arabicCache.size() }

  /**
   * Parses plain text plus `<font color="...">...</font>` and `<b>...</b>` in a single pass.
   * Returns `null` if the text contains HTML entities, unsupported tags, malformed/unbalanced
   * markup, or whitespace runs that require full HTML processing.
   */
  @VisibleForTesting
  internal fun parseFastPath(
    text: String,
    @ColorInt overrideHighlightColor: Int? = null
  ): CharSequence? {
    val len = text.length
    if (len == 0) {
      return text
    }
    if (text[0] == ' ' || text[len - 1] == ' ') {
      return null
    }
    var hasMarkup = false
    for (i in 0 until len) {
      val ch = text[i]
      if (ch == '&' || ch == '\n' || ch == '\r' || ch == '\t') {
        return null
      }
      if (ch == '<') {
        hasMarkup = true
      } else if (ch == ' ' && i > 0 && text[i - 1] == ' ') {
        return null
      }
    }

    if (!hasMarkup) {
      return text
    }

    val builder = SpannableStringBuilder()
    val openTags = ArrayList<OpenTag>(4)
    var index = 0

    while (index < len) {
      val lt = text.indexOf('<', index)
      if (lt == -1) {
        if ((builder.isEmpty() || builder[builder.length - 1] == ' ') && text[index] == ' ') {
          return null
        }
        builder.append(text, index, len)
        break
      }
      if (lt > index) {
        if ((builder.isEmpty() || builder[builder.length - 1] == ' ') && text[index] == ' ') {
          return null
        }
        builder.append(text, index, lt)
      }

      val gt = text.indexOf('>', lt + 1)
      if (gt == -1) {
        return null
      }

      when {
        text.regionMatches(lt, TAG_BOLD_OPEN, 0, TAG_BOLD_OPEN.length, ignoreCase = true) &&
          gt == lt + TAG_BOLD_OPEN.length - 1 -> {
          openTags.add(OpenTag(TagType.BOLD, builder.length, 0))
        }

        text.regionMatches(lt, TAG_BOLD_CLOSE, 0, TAG_BOLD_CLOSE.length, ignoreCase = true) &&
          gt == lt + TAG_BOLD_CLOSE.length - 1 -> {
          if (openTags.isEmpty()) {
            return null
          }
          val open = openTags.removeAt(openTags.size - 1)
          if (open.type != TagType.BOLD) {
            return null
          }
          if (builder.length > open.start) {
            builder.setSpan(
              StyleSpan(Typeface.BOLD),
              open.start,
              builder.length,
              Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
          }
        }

        text.regionMatches(lt, TAG_FONT_CLOSE, 0, TAG_FONT_CLOSE.length, ignoreCase = true) &&
          gt == lt + TAG_FONT_CLOSE.length - 1 -> {
          if (openTags.isEmpty()) {
            return null
          }
          val open = openTags.removeAt(openTags.size - 1)
          if (open.type != TagType.FONT) {
            return null
          }
          if (builder.length > open.start) {
            builder.setSpan(
              ForegroundColorSpan(open.color),
              open.start,
              builder.length,
              Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
          }
        }

        text.regionMatches(lt, TAG_FONT_PREFIX, 0, TAG_FONT_PREFIX.length, ignoreCase = true) -> {
          val color = parseFontTagColor(text, lt + TAG_FONT_PREFIX.length, gt) ?: return null
          openTags.add(OpenTag(TagType.FONT, builder.length, overrideHighlightColor ?: color))
        }

        else -> return null
      }

      index = gt + 1
    }

    if (openTags.isNotEmpty()) {
      return null
    }
    if (builder.isNotEmpty() && builder[builder.length - 1] == ' ') {
      return null
    }
    return SpannedString(builder)
  }

  private fun parseFontTagColor(text: String, attrStart: Int, tagEnd: Int): Int? {
    var pos = attrStart
    while (pos < tagEnd && text[pos] == ' ') {
      pos++
    }
    if (!text.regionMatches(pos, "color=", 0, 6, ignoreCase = true)) {
      return null
    }
    pos += 6
    if (pos >= tagEnd) {
      return null
    }

    val quote = text[pos]
    val valueStart: Int
    val valueEnd: Int
    if (quote == '"' || quote == '\'') {
      valueStart = pos + 1
      val closingQuote = text.indexOf(quote, valueStart)
      if (closingQuote == -1 || closingQuote >= tagEnd) {
        return null
      }
      valueEnd = closingQuote
      pos = closingQuote + 1
    } else {
      valueStart = pos
      while (pos < tagEnd && text[pos] != ' ') {
        pos++
      }
      valueEnd = pos
    }

    while (pos < tagEnd && text[pos] == ' ') {
      pos++
    }
    // Reject extra attributes on <font> so fallback handles face/size/etc. accurately.
    if (pos != tagEnd) {
      return null
    }

    return parseHtmlColor(text, valueStart, valueEnd)
  }

  internal fun parseHtmlColor(text: CharSequence, start: Int, end: Int): Int? {
    var s = start
    var e = end
    while (s < e && text[s] == ' ') s++
    while (e > s && text[e - 1] == ' ') e--
    val length = e - s
    if (length == 0) {
      return null
    }

    return if (text[s] == '#') {
      // Match Android's HtmlToSpannedConverter: only 6-digit #RRGGBB hex colors are accepted;
      // other lengths fall back to HtmlCompat for exact platform parity.
      if (length != 7) {
        return null
      }
      var acc = 0L
      for (i in s + 1 until e) {
        val digit = hexDigit(text[i])
        if (digit < 0) return null
        acc = (acc shl 4) or digit.toLong()
      }
      acc.toInt() or 0xFF000000.toInt()
    } else {
      val value = parseSignedInt(text, s, e) ?: return null
      value or 0xFF000000.toInt()
    }
  }

  private fun hexDigit(ch: Char): Int = when (ch) {
    in '0'..'9' -> ch - '0'
    in 'a'..'f' -> ch - 'a' + 10
    in 'A'..'F' -> ch - 'A' + 10
    else -> -1
  }

  private fun parseSignedInt(text: CharSequence, start: Int, end: Int): Int? {
    var i = start
    val negative = text[i] == '-'
    if (negative || text[i] == '+') {
      i++
      if (i == end) return null
    }
    var acc = 0L
    while (i < end) {
      val ch = text[i]
      if (ch !in '0'..'9') return null
      acc = acc * 10L + (ch - '0')
      if (acc > 0x80000000L) return null
      i++
    }
    val signed = if (negative) -acc else acc
    if (signed < Int.MIN_VALUE || signed > Int.MAX_VALUE) return null
    return signed.toInt()
  }

  private enum class TagType {
    FONT,
    BOLD
  }

  private class OpenTag(
    val type: TagType,
    val start: Int,
    val color: Int
  )
}
