package com.quran.common.search.arabic

/**
 * Pure-Kotlin comprehensive Arabic search matcher with 100% parity to the native Rust engine
 * (`common/search/src/main/rust/src/engine.rs`).
 *
 * Supports:
 * - Full Arabic orthographic normalization (harakat/tashkeel, Quranic marks, dagger alif, tatweel,
 *   zero-width chars, Arabic Presentation Forms-B, Alef/Hamza/Ya/Waw folding).
 * - Complete attached Arabic proclitics (`و/ف/ب/ك/ل/ت/أ/س`) AND `لِـ + ال` elision
 *   (`لله`/`ولله`/`فلله`, `للجنة`, `للناس`, `للرحمن`, `للمؤمنين`, `لليل`, etc.).
 * - Uthmani open-Ta (`ة` <-> `ت`) matching (`جنت`/`رحمت`/`نعمت`/`شجرت`/`سنت`/`امرأت`/`كلمت`).
 * - Ranked 3-tier morphological matching (Tier 1: exact/proclitic/`لل`/open-Ta -> Tier 2:
 *   singular indefinite/possessive -> Tier 3: dual & plural / `ون <-> ين`) with strict word
 *   boundaries for zero false positives.
 * - Multi-word phrases (contiguous exact -> contiguous morphological -> topical all-words) and
 *   mid-typing normalized substring fallback.
 */
class ArabicMorphologicalQuery private constructor(
  val query: String,
  private val mode: Mode
) {

  private sealed interface Mode {
    class SingleWord(
      val matcher: WordMatcher,
      val fallbackNeedle: String
    ) : Mode

    class MultiWord(
      val words: Array<WordMatcher>,
      val normPhrase: String
    ) : Mode

    class ShortArabic(val needle: String) : Mode

    class Literal(val compiled: ArabicCompiledQuery?, val rawStripped: String) : Mode

    data object Empty : Mode
  }

  private class WordMatcher(
    val tier1: HashSet<String>,
    val tier2: HashSet<String>,
    val tier3: HashSet<String>
  ) {
    fun matchesAny(token: String): Boolean =
      tier1.contains(token) || tier2.contains(token) || tier3.contains(token)

    fun matchTier(token: String): Int = when {
      tier1.contains(token) -> TIER_EXACT
      tier2.contains(token) -> TIER_SINGULAR_MORPH
      tier3.contains(token) -> TIER_DUAL_PLURAL_OR_TOPICAL
      else -> NO_MATCH
    }
  }

  fun isWordLevel(): Boolean = mode is Mode.SingleWord || mode is Mode.MultiWord

  /**
   * Matches [text] and appends non-overlapping original UTF-16 `[start, end)` highlight spans
   * (absorbing trailing diacritics/marks) into [startsOut] and [endsOut].
   *
   * Returns the match tier (`1`..`3` for word/phrase matches, `4` for substring/literal fallback,
   * or `0` if no match).
   */
  fun matchAndCollectSpans(
    text: String,
    startsOut: ArrayList<Int>,
    endsOut: ArrayList<Int>,
    allowNonContiguousMultiWord: Boolean = true,
    allowSubstringFallback: Boolean = false
  ): Int {
    startsOut.clear()
    endsOut.clear()
    if (text.isEmpty()) return NO_MATCH

    return when (val m = mode) {
      Mode.Empty -> NO_MATCH

      is Mode.Literal -> {
        if (m.compiled != null) {
          var pos = m.compiled.findFirst(text, 0)
          if (pos < 0) return NO_MATCH
          val patLen = m.compiled.length
          while (pos >= 0) {
            val end = pos + patLen
            if (end <= text.length) {
              startsOut.add(pos)
              endsOut.add(end)
            }
            pos = m.compiled.findFirst(text, end)
          }
          if (startsOut.isEmpty()) NO_MATCH else TIER_EXACT
        } else {
          val needle = m.rawStripped
          if (needle.isEmpty()) return NO_MATCH
          var pos = text.indexOf(needle, 0)
          if (pos < 0) return NO_MATCH
          while (pos >= 0) {
            val end = pos + needle.length
            startsOut.add(pos)
            endsOut.add(end)
            pos = text.indexOf(needle, end)
          }
          TIER_EXACT
        }
      }

      is Mode.ShortArabic -> {
        collectNormSubstringSpans(text, m.needle, startsOut, endsOut)
        if (startsOut.isEmpty()) NO_MATCH else TIER_EXACT
      }

      is Mode.SingleWord -> {
        var bestTier = NO_MATCH
        val tokenBuilder = StringBuilder(24)
        var tokStart = 0
        var tokEnd = 0
        val len = text.length

        fun flushToken() {
          if (tokenBuilder.isEmpty()) return
          val tok = tokenBuilder.toString()
          val tier = m.matcher.matchTier(tok)
          if (tier != NO_MATCH) {
            startsOut.add(tokStart)
            endsOut.add(tokEnd)
            if (bestTier == NO_MATCH || tier < bestTier) {
              bestTier = tier
            }
          }
          tokenBuilder.setLength(0)
        }

        for (i in 0 until len) {
          val ch = text[i]
          if (isArabicIgnorable(ch)) {
            if (tokenBuilder.isNotEmpty()) {
              tokEnd = i + 1
            }
            continue
          }
          val folded = foldArabicLetter(ch)
          if (isArabicLetter(folded)) {
            if (tokenBuilder.isEmpty()) {
              tokStart = i
            }
            tokenBuilder.append(folded)
            tokEnd = i + 1
          } else {
            flushToken()
          }
        }
        flushToken()

        if (bestTier != NO_MATCH) {
          return bestTier
        }
        if (allowSubstringFallback) {
          collectNormSubstringSpans(text, m.fallbackNeedle, startsOut, endsOut)
          if (startsOut.isNotEmpty()) return TIER_FALLBACK_SUBSTR
        }
        NO_MATCH
      }

      is Mode.MultiWord -> {
        val k = m.words.size
        val tokNorms = ArrayList<String>(16)
        val tokStarts = ArrayList<Int>(16)
        val tokEnds = ArrayList<Int>(16)
        val tokenBuilder = StringBuilder(24)
        var curStart = 0
        var curEnd = 0
        val len = text.length

        fun flushToken() {
          if (tokenBuilder.isEmpty()) return
          tokNorms.add(tokenBuilder.toString())
          tokStarts.add(curStart)
          tokEnds.add(curEnd)
          tokenBuilder.setLength(0)
        }

        for (i in 0 until len) {
          val ch = text[i]
          if (isArabicIgnorable(ch)) {
            if (tokenBuilder.isNotEmpty()) {
              curEnd = i + 1
            }
            continue
          }
          val folded = foldArabicLetter(ch)
          if (isArabicLetter(folded)) {
            if (tokenBuilder.isEmpty()) {
              curStart = i
            }
            tokenBuilder.append(folded)
            curEnd = i + 1
          } else {
            flushToken()
          }
        }
        flushToken()

        // 1. Check contiguous phrase matches (merged span from first token start to last token end)
        var bestContiguous = NO_MATCH
        if (tokNorms.size >= k) {
          var i = 0
          while (i + k <= tokNorms.size) {
            var worst = TIER_EXACT
            var ok = true
            for (j in 0 until k) {
              val mt = m.words[j].matchTier(tokNorms[i + j])
              if (mt == NO_MATCH) {
                ok = false
                break
              }
              if (mt > worst) worst = mt
            }
            if (ok) {
              val phraseTier = if (worst == TIER_EXACT) TIER_EXACT else TIER_SINGULAR_MORPH
              if (bestContiguous == NO_MATCH || phraseTier < bestContiguous) {
                bestContiguous = phraseTier
              }
              startsOut.add(tokStarts[i])
              endsOut.add(tokEnds[i + k - 1])
              i += k
            } else {
              i++
            }
          }
        }
        if (bestContiguous != NO_MATCH) {
          return bestContiguous
        }

        // 2. Non-contiguous all-words topical match
        if (allowNonContiguousMultiWord && tokNorms.isNotEmpty()) {
          val foundWord = BooleanArray(k)
          for (ti in tokNorms.indices) {
            val tok = tokNorms[ti]
            var matchedAny = false
            for (wi in 0 until k) {
              if (m.words[wi].matchesAny(tok)) {
                foundWord[wi] = true
                matchedAny = true
              }
            }
            if (matchedAny) {
              startsOut.add(tokStarts[ti])
              endsOut.add(tokEnds[ti])
            }
          }
          var allFound = true
          for (wi in 0 until k) {
            if (!foundWord[wi]) {
              allFound = false
              break
            }
          }
          if (allFound && startsOut.isNotEmpty()) {
            return TIER_DUAL_PLURAL_OR_TOPICAL
          }
          startsOut.clear()
          endsOut.clear()
        }

        // 3. Substring fallback (mid-typing last word)
        if (allowSubstringFallback) {
          collectNormSubstringSpans(text, m.normPhrase, startsOut, endsOut)
          if (startsOut.isNotEmpty()) return TIER_FALLBACK_SUBSTR
        }
        NO_MATCH
      }
    }
  }

  /**
   * Returns packed non-overlapping UTF-16 match spans `[start0, end0, start1, end1, ...]`
   * matching Rust `CompiledArabicQuery::find_all_spans` semantics.
   */
  fun findAllSpans(text: String, allowSubstringFallback: Boolean = true): IntArray {
    if (text.isEmpty()) return IntArray(0)
    val starts = ArrayList<Int>(4)
    val ends = ArrayList<Int>(4)
    val tier = matchAndCollectSpans(
      text = text,
      startsOut = starts,
      endsOut = ends,
      allowNonContiguousMultiWord = true,
      allowSubstringFallback = allowSubstringFallback
    )
    if (tier == NO_MATCH || starts.isEmpty()) return IntArray(0)
    val out = IntArray(starts.size * 2)
    for (i in starts.indices) {
      out[i * 2] = starts[i]
      out[i * 2 + 1] = ends[i]
    }
    return out
  }

  fun highlightTo(
    text: String,
    matchStart: String,
    matchEnd: String,
    buffer: StringBuilder,
    allowSubstringFallback: Boolean = true
  ): String? {
    val starts = ArrayList<Int>(4)
    val ends = ArrayList<Int>(4)
    val tier = matchAndCollectSpans(
      text = text,
      startsOut = starts,
      endsOut = ends,
      allowNonContiguousMultiWord = true,
      allowSubstringFallback = allowSubstringFallback
    )
    if (tier == NO_MATCH || starts.isEmpty()) return null
    return formatHighlighted(text, starts, ends, matchStart, matchEnd, buffer)
  }

  private class CacheEntry(val query: String, val compiled: ArabicMorphologicalQuery?)

  companion object {
    const val NO_MATCH = 0
    const val TIER_EXACT = 1
    const val TIER_SINGULAR_MORPH = 2
    const val TIER_DUAL_PLURAL_OR_TOPICAL = 3
    const val TIER_FALLBACK_SUBSTR = 4

    private const val CH_HAMZA = '\u0621'
    private const val CH_ALEF = '\u0627'
    private const val CH_TA_MARBUTA = '\u0629'
    private const val CH_TA = '\u062A'
    private const val CH_LAM = '\u0644'
    private const val CH_NUN = '\u0646'
    private const val CH_HA = '\u0647'
    private const val CH_WAW = '\u0648'
    private const val CH_YA = '\u064A'
    private const val CH_ALEF_WASLA = '\u0671'

    private val ALLAH_PROCLITICS = arrayOf(
      "", "و", "ف", "ب", "ت", "ك", "ا",
      "وب", "فب", "وت", "فت", "اب", "او", "اف", "فو", "وك", "فك"
    )
    private val DEF_PROCLITICS = arrayOf(
      "", "و", "ف", "ب", "ك", "ا",
      "وب", "فب", "وك", "فك", "او", "اف", "اب"
    )
    private val L_PROCLITICS = arrayOf("", "و", "ف", "ا", "او", "اف")
    private val INDEF_PROCLITICS = arrayOf(
      "", "و", "ف", "ب", "ك", "ل",
      "وب", "فب", "ول", "فل", "وك", "فك"
    )
    private val VERB_PROCLITICS = arrayOf("س", "وس", "فس")
    private val SHORT_PHRASE_PROCLITICS = arrayOf("", "و", "ف", "ب", "ل", "ك")
    private val PRONOUNS = arrayOf("ه", "ها", "هم", "هما", "هن", "ك", "كم", "كما", "كن", "ي", "نا")

    @Volatile
    private var lastEntry: CacheEntry? = null

    fun compile(query: String): ArabicMorphologicalQuery? {
      val cached = lastEntry
      if (cached != null && cached.query == query) {
        return cached.compiled
      }
      val compiled = compileUncached(query)
      lastEntry = CacheEntry(query, compiled)
      return compiled
    }

    fun isArabicIgnorable(ch: Char): Boolean {
      val code = ch.code
      return (code in 0x0610..0x061A) ||
        code == 0x061C ||
        code == 0x0640 ||
        (code in 0x064B..0x065F) ||
        code == 0x0670 ||
        (code in 0x06D6..0x06ED) ||
        (code in 0x200B..0x200F) ||
        code == 0xFEFF
    }

    fun mapPresentationForm(ch: Char): Char {
      val code = ch.code
      if (code !in 0xFE80..0xFEFC) return ch
      return when (code) {
        0xFE80 -> '\u0621'
        0xFE81, 0xFE82 -> '\u0622'
        0xFE83, 0xFE84 -> '\u0623'
        0xFE85, 0xFE86 -> '\u0624'
        0xFE87, 0xFE88 -> '\u0625'
        in 0xFE89..0xFE8C -> '\u0626'
        0xFE8D, 0xFE8E -> '\u0627'
        in 0xFE8F..0xFE92 -> '\u0628'
        0xFE93, 0xFE94 -> '\u0629'
        in 0xFE95..0xFE98 -> '\u062A'
        in 0xFE99..0xFE9C -> '\u062B'
        in 0xFE9D..0xFEA0 -> '\u062C'
        in 0xFEA1..0xFEA4 -> '\u062D'
        in 0xFEA5..0xFEA8 -> '\u062E'
        0xFEA9, 0xFEAA -> '\u062F'
        0xFEAB, 0xFEAC -> '\u0630'
        0xFEAD, 0xFEAE -> '\u0631'
        0xFEAF, 0xFEB0 -> '\u0632'
        in 0xFEB1..0xFEB4 -> '\u0633'
        in 0xFEB5..0xFEB8 -> '\u0634'
        in 0xFEB9..0xFEBC -> '\u0635'
        in 0xFEBD..0xFEC0 -> '\u0636'
        in 0xFEC1..0xFEC4 -> '\u0637'
        in 0xFEC5..0xFEC8 -> '\u0638'
        in 0xFEC9..0xFECC -> '\u0639'
        in 0xFECD..0xFED0 -> '\u063A'
        in 0xFED1..0xFED4 -> '\u0641'
        in 0xFED5..0xFED8 -> '\u0642'
        in 0xFED9..0xFEDC -> '\u0643'
        in 0xFEDD..0xFEE0 -> '\u0644'
        in 0xFEE1..0xFEE4 -> '\u0645'
        in 0xFEE5..0xFEE8 -> '\u0646'
        in 0xFEE9..0xFEEC -> '\u0647'
        0xFEED, 0xFEEE -> '\u0648'
        0xFEEF, 0xFEF0 -> '\u0649'
        in 0xFEF1..0xFEF4 -> '\u064A'
        else -> ch
      }
    }

    fun foldArabicLetter(ch: Char): Char {
      val base = mapPresentationForm(ch)
      return when (base) {
        '\u0622', '\u0623', '\u0625', '\u0671', '\u0672', '\u0673' -> CH_ALEF
        '\u0624' -> CH_WAW
        '\u0626', '\u0649', '\u06CC' -> CH_YA
        else -> base
      }
    }

    fun isArabicLetter(ch: Char): Boolean {
      val code = ch.code
      return code in 0x0621..0x064A
    }

    fun formatHighlighted(
      text: String,
      starts: ArrayList<Int>,
      ends: ArrayList<Int>,
      matchStart: String,
      matchEnd: String,
      buffer: StringBuilder
    ): String {
      buffer.setLength(0)
      var lastEnd = 0
      val len = text.length
      for (i in starts.indices) {
        val s = starts[i]
        val e = ends[i]
        if (s >= lastEnd && e > s && e <= len) {
          buffer.append(text, lastEnd, s)
          buffer.append(matchStart)
          buffer.append(text, s, e)
          buffer.append(matchEnd)
          lastEnd = e
        }
      }
      buffer.append(text, lastEnd, len)
      return buffer.toString()
    }

    private fun compileUncached(query: String): ArabicMorphologicalQuery? {
      if (query.isEmpty()) return null

      var nonParenCount = 0
      var hasArabicLetter = false
      var hasNonArabicSymbol = false

      for (i in query.indices) {
        val ch = query[i]
        if (ch == '(' || ch == ')') continue
        nonParenCount++
        if (isArabicIgnorable(ch) || ch.isWhitespace()) continue
        val folded = foldArabicLetter(ch)
        if (isArabicLetter(folded)) {
          hasArabicLetter = true
        } else {
          hasNonArabicSymbol = true
        }
      }

      if (nonParenCount == 0) return null

      if (!hasArabicLetter || hasNonArabicSymbol) {
        val stripped = buildString(query.length) {
          for (ch in query) {
            if (ch != '(' && ch != ')') append(ch)
          }
        }
        val literalCompiled = ArabicCompiledQuery.compileLiteralOnly(query)
        return ArabicMorphologicalQuery(query, Mode.Literal(literalCompiled, stripped))
      }

      // Pure Arabic query: extract normalized words preserving original first/last chars
      val origFirsts = ArrayList<Char>(4)
      val origLasts = ArrayList<Char>(4)
      val normWords = ArrayList<String>(4)
      val cur = StringBuilder(16)
      var firstCh = '\u0000'
      var lastCh = '\u0000'

      for (i in query.indices) {
        val ch = query[i]
        if (ch == '(' || ch == ')' || isArabicIgnorable(ch)) continue
        if (ch.isWhitespace()) {
          if (cur.isNotEmpty()) {
            origFirsts.add(firstCh)
            origLasts.add(lastCh)
            normWords.add(cur.toString())
            cur.setLength(0)
          }
          continue
        }
        val unpresented = mapPresentationForm(ch)
        val folded = foldArabicLetter(ch)
        if (isArabicLetter(folded)) {
          if (cur.isEmpty()) {
            firstCh = unpresented
          }
          lastCh = unpresented
          cur.append(folded)
        }
      }
      if (cur.isNotEmpty()) {
        origFirsts.add(firstCh)
        origLasts.add(lastCh)
        normWords.add(cur.toString())
      }

      if (normWords.isEmpty()) {
        return ArabicMorphologicalQuery(query, Mode.Empty)
      }

      if (normWords.size == 1) {
        val normW = normWords[0]
        if (normW.length < 3) {
          return ArabicMorphologicalQuery(query, Mode.ShortArabic(normW))
        }
        val wm = buildWordMatcher(origFirsts[0], origLasts[0], normW)
        return ArabicMorphologicalQuery(query, Mode.SingleWord(wm, normW))
      }

      val matchers = Array(normWords.size) { idx ->
        buildWordMatcher(origFirsts[idx], origLasts[idx], normWords[idx])
      }
      val phrase = normWords.joinToString(" ")
      return ArabicMorphologicalQuery(query, Mode.MultiWord(matchers, phrase))
    }

    private fun addDefiniteForms(target: HashSet<String>, stem: String) {
      for (p in DEF_PROCLITICS) {
        target.add(p + "ال" + stem)
      }
      val lElided = if (stem.isNotEmpty() && stem[0] == CH_LAM) "ل" else "لل"
      for (p in L_PROCLITICS) {
        target.add(p + lElided + stem)
      }
    }

    private fun addIndefiniteForms(
      target: HashSet<String>,
      stem: String,
      allowVerbFuture: Boolean = false
    ) {
      for (p in INDEF_PROCLITICS) {
        target.add(p + stem)
      }
      if (allowVerbFuture) {
        for (p in VERB_PROCLITICS) {
          target.add(p + stem)
        }
      }
    }

    private fun buildWordMatcher(origFirst: Char, origLast: Char, normW: String): WordMatcher {
      val t1 = HashSet<String>(32)
      val t2 = HashSet<String>()
      val t3 = HashSet<String>()

      if (normW.length < 3) {
        for (p in SHORT_PHRASE_PROCLITICS) {
          t1.add(p + normW)
        }
        if (normW.length == 2) {
          addDefiniteForms(t1, normW)
        }
        return WordMatcher(t1, t2, t3)
      }

      // Case 1: The Blessed Name "الله" / "اللة"
      if (normW == "الله" || normW == "اللة") {
        for (p in ALLAH_PROCLITICS) t1.add(p + "الله")
        for (p in L_PROCLITICS) t1.add(p + "لله")
        t1.add("اللهم")
        t1.add("واللهم")
        t1.add("فاللهم")
        for (p in SHORT_PHRASE_PROCLITICS) t1.add(p + "عبدالله")
        return WordMatcher(t1, t2, t3)
      }

      val hasAl = (origFirst == CH_ALEF || origFirst == CH_ALEF_WASLA) &&
        normW.length >= 4 &&
        normW[0] == CH_ALEF &&
        normW[1] == CH_LAM
      val stem = if (hasAl) normW.substring(2) else normW
      val isFem = stem.length >= 3 && (origLast == CH_TA_MARBUTA || (hasAl && origLast == CH_HA))

      if (isFem) {
        val base = stem.substring(0, stem.length - 1)
        val femEnds = if (origLast == CH_HA) charArrayOf(CH_TA_MARBUTA, CH_TA, CH_HA)
        else charArrayOf(CH_TA_MARBUTA, CH_TA)

        if (hasAl) {
          for (e in femEnds) {
            val form = base + e
            addDefiniteForms(t1, form)
            addIndefiniteForms(t2, form)
          }
        } else {
          for (e in femEnds) {
            val form = base + e
            addIndefiniteForms(t1, form)
            addDefiniteForms(t1, form)
          }
        }

        // Tier 2: Singular possessive (base + ت + pronoun)
        for (pron in PRONOUNS) {
          addIndefiniteForms(t2, base + CH_TA + pron)
        }

        // Tier 3: Dual (تان / تين + construct تا/تي + pronoun)
        for (dualEnd in arrayOf("تان", "تين")) {
          val form = base + dualEnd
          addDefiniteForms(t3, form)
          addIndefiniteForms(t3, form)
        }
        for (dualC in arrayOf("تا", "تي")) {
          for (pron in PRONOUNS) {
            addIndefiniteForms(t3, base + dualC + pron)
          }
        }

        // Tier 3: Sound feminine plural (ات + optional pronoun; صلا -> صلوات for base ending in ا)
        val plurals = if (base.length >= 3 && base[base.length - 1] == CH_ALEF) {
          arrayOf(base + "ات", base.substring(0, base.length - 1) + "وات")
        } else {
          arrayOf(base + "ات")
        }
        for (pl in plurals) {
          addDefiniteForms(t3, pl)
          addIndefiniteForms(t3, pl)
          for (pron in PRONOUNS) {
            addIndefiniteForms(t3, pl + pron)
          }
        }
      } else if (hasAl) {
        addDefiniteForms(t1, stem)
        if (stem.length >= 3) {
          addIndefiniteForms(t2, stem)
          val last = stem[stem.length - 1]
          if (last != CH_ALEF && last != CH_HAMZA && stem != "رحمن") {
            addIndefiniteForms(t2, stem + CH_ALEF)
          }
          val endsWn = stem.endsWith("ون")
          val endsYn = stem.endsWith("ين")
          if (!endsWn && !endsYn) {
            for (pron in PRONOUNS) {
              addIndefiniteForms(t2, stem + pron)
            }
          }
          if (stem.length >= 5 && endsWn) {
            val alt = stem.substring(0, stem.length - 2) + "ين"
            addDefiniteForms(t3, alt)
            addIndefiniteForms(t3, alt)
          } else if (stem.length >= 5 && endsYn) {
            val alt = stem.substring(0, stem.length - 2) + "ون"
            addDefiniteForms(t3, alt)
            addIndefiniteForms(t3, alt)
          }
        } else {
          // 2-letter definite stems (e.g. الجن/اليد/الدم): allow only bare & و+stem
          // so unrelated 3-letter roots (e.g. فرح from الرح, كلب from اللب) never collide.
          t2.add(stem)
          t2.add("و$stem")
        }
      } else {
        val isVerbLike = normW.length >= 4 &&
          (normW[0] == CH_YA || normW[0] == CH_TA || normW[0] == CH_NUN)
        addIndefiniteForms(t1, normW, allowVerbFuture = isVerbLike)
        addDefiniteForms(t1, normW)
        if (normW[normW.length - 1] == CH_HA) {
          val altTa = normW.substring(0, normW.length - 1) + CH_TA_MARBUTA
          addIndefiniteForms(t1, altTa)
          addDefiniteForms(t1, altTa)
        }

        val last = normW[normW.length - 1]
        val endsWn = normW.endsWith("ون")
        val endsYn = normW.endsWith("ين")
        val endsWa = normW.endsWith("وا")
        val endsTm = normW.endsWith("تم")
        val endsTn = normW.endsWith("تن")
        val endsPron = last == CH_HA ||
          normW.endsWith("ها") ||
          normW.endsWith("هم") ||
          normW.endsWith("كم") ||
          normW.endsWith("نا")

        if (last != CH_ALEF &&
          last != CH_HAMZA &&
          !endsWn &&
          !endsYn &&
          !endsWa &&
          !endsTm &&
          !endsTn &&
          normW != "رحمن"
        ) {
          addIndefiniteForms(t2, normW + CH_ALEF)
        }
        if (!endsWa && !endsWn && !endsYn && !endsTm && !endsTn && !endsPron) {
          for (pron in PRONOUNS) {
            addIndefiniteForms(t2, normW + pron)
          }
        }
        if (normW.length >= 5 && endsWn) {
          val alt = normW.substring(0, normW.length - 2) + "ين"
          addIndefiniteForms(t3, alt)
          addDefiniteForms(t3, alt)
        } else if (normW.length >= 5 && endsYn) {
          val alt = normW.substring(0, normW.length - 2) + "ون"
          addIndefiniteForms(t3, alt)
          addDefiniteForms(t3, alt)
        }
      }

      t2.removeAll(t1)
      t3.removeAll(t1)
      t3.removeAll(t2)
      return WordMatcher(t1, t2, t3)
    }

    private fun normCharMatches(queryCh: Char, textCh: Char): Boolean {
      if (queryCh == textCh) return true
      return when (queryCh) {
        CH_HA -> textCh == CH_TA_MARBUTA
        CH_TA_MARBUTA -> textCh == CH_HA || textCh == CH_TA
        CH_TA -> textCh == CH_TA_MARBUTA
        else -> false
      }
    }

    private fun collectNormSubstringSpans(
      rawText: String,
      needle: String,
      startsOut: ArrayList<Int>,
      endsOut: ArrayList<Int>
    ) {
      val nLen = needle.length
      val tLen = rawText.length
      if (nLen == 0 || tLen == 0) return

      val norm = CharArray(tLen)
      val origStart = IntArray(tLen)
      val origEnd = IntArray(tLen)
      var normLen = 0
      var prevSpace = true

      for (i in 0 until tLen) {
        val ch = rawText[i]
        if (isArabicIgnorable(ch)) {
          if (normLen > 0 && !prevSpace) {
            origEnd[normLen - 1] = i + 1
          }
          continue
        }
        val folded = foldArabicLetter(ch)
        if (isArabicLetter(folded)) {
          norm[normLen] = folded
          origStart[normLen] = i
          origEnd[normLen] = i + 1
          normLen++
          prevSpace = false
        } else if (!prevSpace && normLen > 0) {
          norm[normLen] = ' '
          origStart[normLen] = i
          origEnd[normLen] = i + 1
          normLen++
          prevSpace = true
        }
      }
      if (normLen > 0 && norm[normLen - 1] == ' ') {
        normLen--
      }
      if (normLen < nLen) return

      val maxStart = normLen - nLen
      val first = needle[0]
      var pos = 0
      while (pos <= maxStart) {
        if (normCharMatches(first, norm[pos])) {
          var j = 1
          while (j < nLen && normCharMatches(needle[j], norm[pos + j])) {
            j++
          }
          if (j == nLen) {
            startsOut.add(origStart[pos])
            endsOut.add(origEnd[pos + nLen - 1])
            pos += nLen
            continue
          }
        }
        pos++
      }
    }
  }
}
