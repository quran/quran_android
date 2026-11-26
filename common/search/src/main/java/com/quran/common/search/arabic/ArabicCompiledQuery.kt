package com.quran.common.search.arabic

/**
 * Fixed-length compiled matcher equivalent to `Pattern.compile(ArabicCharacterHelper.generateRegex(query))`,
 * eliminating `java.util.regex.Pattern` / ICU backtracking overhead during full-Quran Arabic search.
 *
 * Uses the first exact character (preferring a non-space character) as an anchor scanned with
 * [String.indexOf] (an ART SIMD intrinsic), then verifies the remaining positions inline.
 */
class ArabicCompiledQuery private constructor(
  val query: String,
  private val exactChars: CharArray,
  private val charGroups: Array<CharArray?>,
  private val anchorOffset: Int,
  private val anchorChar: Char,
  private val morphological: ArabicMorphologicalQuery?
) {
  val length: Int = exactChars.size

  /**
   * Returns packed variable-length UTF-16 highlight spans `[start0, end0, ...]` using full
   * morphological/orthographic Arabic matching (100% parity with Rust `nativeFindMatchSpans`).
   */
  fun findAllMatchSpans(text: String): IntArray {
    val morph = morphological
    if (morph != null) {
      return morph.findAllSpans(text, allowSubstringFallback = true)
    }
    var start = findFirst(text, 0)
    if (start < 0) return IntArray(0)
    val matchLen = length
    val ranges = ArrayList<Int>(4)
    while (start >= 0) {
      val end = start + matchLen
      if (end <= text.length) {
        ranges.add(start)
        ranges.add(end)
      }
      start = findFirst(text, end)
    }
    return ranges.toIntArray()
  }

  fun findFirst(text: String, fromIndex: Int = 0): Int {
    val startFrom = if (fromIndex < 0) 0 else fromIndex
    val textLen = text.length
    val patternLen = length
    val maxStart = textLen - patternLen
    if (startFrom > maxStart) {
      return -1
    }

    if (anchorOffset >= 0) {
      val anchor = anchorChar
      val offset = anchorOffset
      var anchorPos = text.indexOf(anchor, startFrom + offset)
      while (anchorPos >= 0) {
        val candidateStart = anchorPos - offset
        if (candidateStart > maxStart) {
          return -1
        }
        if (matchesAt(text, candidateStart, skipIndex = offset)) {
          return candidateStart
        }
        anchorPos = text.indexOf(anchor, anchorPos + 1)
      }
      return -1
    }

    val firstGroup = charGroups[0]!!
    for (candidateStart in startFrom..maxStart) {
      if (containsChar(firstGroup, text[candidateStart]) &&
        matchesAt(text, candidateStart, skipIndex = 0)
      ) {
        return candidateStart
      }
    }
    return -1
  }

  fun highlightTo(
    text: String,
    matchStart: String,
    matchEnd: String,
    buffer: StringBuilder
  ): String? {
    var start = findFirst(text, 0)
    if (start < 0) {
      return null
    }
    buffer.setLength(0)
    var lastEnd = 0
    val patternLen = length
    while (start >= 0) {
      val end = start + patternLen
      buffer.append(text, lastEnd, start)
      buffer.append(matchStart)
      buffer.append(text, start, end)
      buffer.append(matchEnd)
      lastEnd = end
      start = findFirst(text, lastEnd)
    }
    buffer.append(text, lastEnd, text.length)
    return buffer.toString()
  }

  private fun matchesAt(text: String, start: Int, skipIndex: Int): Boolean {
    val exact = exactChars
    val groups = charGroups
    val len = exact.size
    for (i in 0 until len) {
      if (i == skipIndex) continue
      val ch = text[start + i]
      val group = groups[i]
      if (group == null) {
        if (ch != exact[i]) return false
      } else {
        if (!containsChar(group, ch)) return false
      }
    }
    return true
  }

  private fun containsChar(chars: CharArray, target: Char): Boolean {
    for (c in chars) {
      if (c == target) return true
    }
    return false
  }

  private class CacheEntry(val query: String, val compiled: ArabicCompiledQuery?)

  companion object {
    @Volatile
    private var lastEntry: CacheEntry? = null

    /**
     * Compiles [query] into a fast matcher, or returns `null` if the query is empty after
     * stripping parentheses or contains UTF-16 surrogates (caller falls back to `Pattern`).
     */
    fun compile(query: String): ArabicCompiledQuery? {
      val cached = lastEntry
      if (cached != null && cached.query == query) {
        return cached.compiled
      }

      val compiled = compileUncached(query)
      lastEntry = CacheEntry(query, compiled)
      return compiled
    }

    internal fun compileLiteralOnly(query: String): ArabicCompiledQuery? {
      return compileUncached(query, attachMorphological = false)
    }

    private fun compileUncached(
      query: String,
      attachMorphological: Boolean = true
    ): ArabicCompiledQuery? {
      if (query.isEmpty()) {
        return null
      }

      val exactList = CharArray(query.length)
      val groupList = arrayOfNulls<CharArray>(query.length)
      var count = 0
      var bestAnchorOffset = -1
      var bestAnchorChar = '\u0000'
      var fallbackAnchorOffset = -1
      var fallbackAnchorChar = '\u0000'

      for (i in query.indices) {
        val ch = query[i]
        if (ch == '(' || ch == ')') {
          continue
        }
        if (Character.isSurrogate(ch)) {
          return null
        }
        val equivalent = ArabicCharacterHelper.equivalentChars(ch)
        if (equivalent != null) {
          groupList[count] = equivalent.toCharArray()
        } else {
          exactList[count] = ch
          if (bestAnchorOffset < 0 && ch != ' ') {
            bestAnchorOffset = count
            bestAnchorChar = ch
          } else if (fallbackAnchorOffset < 0) {
            fallbackAnchorOffset = count
            fallbackAnchorChar = ch
          }
        }
        count++
      }

      if (count == 0) {
        return null
      }

      val anchorOffset = if (bestAnchorOffset >= 0) bestAnchorOffset else fallbackAnchorOffset
      val anchorChar = if (bestAnchorOffset >= 0) bestAnchorChar else fallbackAnchorChar
      return ArabicCompiledQuery(
        query = query,
        exactChars = if (count == exactList.size) exactList else exactList.copyOf(count),
        charGroups = if (count == groupList.size) groupList else groupList.copyOf(count),
        anchorOffset = anchorOffset,
        anchorChar = anchorChar,
        morphological = if (attachMorphological) ArabicMorphologicalQuery.compile(query) else null
      )
    }
  }
}
