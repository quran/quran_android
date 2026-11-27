package com.quran.labs.androidquran.memorization.tasmee

import com.quran.labs.androidquran.kidsclassroom.QuranRecitationGrader
import kotlin.math.max
import kotlin.math.min

/**
 * Shared phonetic & lexical alignment utilities for **Streaming Follow-Along ("Tasmee' / تسميع")**.
 *
 * Unlike Mode A's strict Tajweed/Tashkeel teacher grader, streaming Tasmee' follows along with
 * the reciter rather than testing them. When the streaming ASR model misses or slightly garbles an
 * immediate word—or merges/splits adjacent words in connected recitation (Wasl / Idgham)—these
 * helpers allow the monotonic cursor to recognize subsequent spoken words and reveal all words
 * up to what the model heard without stalling.
 */
object TasmeeFollowAlongAlignment {

  private val DIACRITICS_REGEX = Regex("[\u064B-\u065F\u0670\u06D6-\u06ED]")
  private val HAMZA_VARIANTS_REGEX = Regex("[أإآٱءؤئ]")

  val BASMALAH_RASMS: Set<String> = setOf("بسم", "لله", "لرحمن", "لرحيم")

  /**
   * Preserves Arabic base letters including `ا` and normalized hamzas (`[أإآٱءؤئ] -> ا`), while
   * stripping tashkeel/tatweel. Ensures words like `سَوَآءٌ` (`"سواا"`, 4 letters), `قَالَ`
   * (`"قال"`, 3 letters), `كَانَ` (`"كان"`, 3 letters), and `آمَنَ` (`"امن"`, 3 letters) retain
   * their full base-letter identity instead of collapsing to 1–2 consonant Rasm letters.
   */
  fun lexicalSkeleton(word: String): String {
    if (word.isEmpty()) return ""
    val cleaned = word.replace(DIACRITICS_REGEX, "")
      .replace("ـ", "")
      .replace(HAMZA_VARIANTS_REGEX, "ا")
      .replace('ى', 'ي')
      .replace('ة', 'ه')
    val sb = StringBuilder(cleaned.length)
    for (ch in cleaned) {
      if (ch in '\u0621'..'\u064A') {
        sb.append(ch)
      }
    }
    return sb.toString()
  }

  /**
   * Soft consonant Rasm skeleton that folds acoustic near-homophones (`ص->س`, `ض->د`, `ط->ت`, `ظ->ذ`)
   * frequently confused by streaming INT8 CTC windows in room acoustics.
   */
  fun softConsonantRasm(word: String): String {
    val rasm = QuranRecitationGrader.phoneticRasm(word)
    if (rasm.isEmpty()) return ""
    val sb = StringBuilder(rasm.length)
    for (ch in rasm) {
      sb.append(
        when (ch) {
          'ص' -> 'س'
          'ض' -> 'د'
          'ط' -> 'ت'
          'ظ' -> 'ذ'
          else -> ch
        }
      )
    }
    return sb.toString()
  }

  /**
   * Returns `true` when [hypWord] matches [refNormWord]/[qariOnnxWord] on exact Rasm, exact
   * lexical skeleton (`>= 2` letters), or exact soft-homophone Rasm (`>= 3` lexical letters),
   * without relying on approximate edit distance.
   */
  fun matchesExactSkeleton(
    hypWord: String,
    refNormWord: String,
    qariOnnxWord: String = refNormWord
  ): Boolean {
    val effectiveQari = qariOnnxWord.ifEmpty { refNormWord }
    if (QuranRecitationGrader.matchesPhoneticRasm(hypWord, refNormWord, effectiveQari)) {
      return true
    }

    val hLex = lexicalSkeleton(hypWord)
    val rLex = lexicalSkeleton(refNormWord)
    val qLex = lexicalSkeleton(effectiveQari)
    if (hLex.isEmpty() || rLex.isEmpty()) return false

    if (rLex.length >= 2 && (hLex == rLex || hLex == qLex)) {
      return true
    }

    val hSoft = softConsonantRasm(hypWord)
    val rSoft = softConsonantRasm(refNormWord)
    val qSoft = softConsonantRasm(effectiveQari)
    if (rLex.length >= 3 && hSoft.length >= 2 && (hSoft == rSoft || hSoft == qSoft)) {
      return true
    }

    return false
  }

  /**
   * Forgiving single-word match for streaming follow-along:
   * 1. Exact Rasm, exact [lexicalSkeleton] (`>= 2` letters), or exact soft-homophone Rasm via [matchesExactSkeleton].
   * 2. Approximate match (`<= 1` edit on 4..7-letter Rasm skeletons, `<= 2` edits on `8+`-letter
   *    skeletons) when the leading consonant agrees, tolerating minor streaming slips or final-letter
   *    assimilation (e.g. Idgham Shafawi) without confusing root cognates like `أَأَنذَرْتَهُمْ` vs `تُنذِرْهُمْ`.
   */
  fun matchesFollowAlongWord(
    hypWord: String,
    refNormWord: String,
    qariOnnxWord: String = refNormWord
  ): Boolean {
    val effectiveQari = qariOnnxWord.ifEmpty { refNormWord }
    if (matchesExactSkeleton(hypWord, refNormWord, effectiveQari)) {
      return true
    }

    val hLex = lexicalSkeleton(hypWord)
    val rLex = lexicalSkeleton(refNormWord)
    val qLex = lexicalSkeleton(effectiveQari)
    if (hLex.isEmpty() || rLex.isEmpty()) return false

    val hSoft = softConsonantRasm(hypWord)
    val rSoft = softConsonantRasm(refNormWord)
    val qSoft = softConsonantRasm(effectiveQari)
    val hr = QuranRecitationGrader.phoneticRasm(hypWord)
    val rr = QuranRecitationGrader.phoneticRasm(refNormWord)
    val qr = QuranRecitationGrader.phoneticRasm(effectiveQari)

    val maxRefRasmLen = max(rr.length, qr.length)
    if (maxRefRasmLen >= 4 && hr.length >= 3 && hSoft.isNotEmpty()) {
      val sameLeading = (rSoft.isNotEmpty() && hSoft[0] == rSoft[0]) ||
        (qSoft.isNotEmpty() && hSoft[0] == qSoft[0]) ||
        hLex[0] == rLex[0] ||
        (qLex.isNotEmpty() && hLex[0] == qLex[0])
      if (sameLeading) {
        val maxEdits = if (maxRefRasmLen >= 8) 2 else 1
        val softDist = min(
          levenshteinDistance(rSoft, hSoft),
          levenshteinDistance(qSoft.ifEmpty { rSoft }, hSoft)
        )
        if (softDist <= maxEdits) return true
      }
    }

    return false
  }

  /**
   * Checks whether a single connected-speech ASR token [hypWord] represents two adjacent reference
   * words fused together via Tajweed Wasl/Idgham (e.g., `"إنالذين"` for `إِنَّ` + `ٱلَّذِينَ`,
   * `"مالم"` for `مَا` + `لَمْ`).
   */
  fun matchesMergedTwoWords(
    hypWord: String,
    ref1Norm: String,
    ref1Qari: String,
    ref2Norm: String,
    ref2Qari: String
  ): Boolean {
    val joinedNorm = ref1Norm + ref2Norm
    val joinedQari = ref1Qari.ifEmpty { ref1Norm } + ref2Qari.ifEmpty { ref2Norm }
    val hr = QuranRecitationGrader.phoneticRasm(hypWord)
    val jr = QuranRecitationGrader.phoneticRasm(joinedNorm)
    val jqr = QuranRecitationGrader.phoneticRasm(joinedQari)
    if (hr.length < 3 || jr.length < 3) return false
    if (hr == jr || hr == jqr) return true

    val hLex = lexicalSkeleton(hypWord)
    val jLex = lexicalSkeleton(joinedNorm)
    val jqLex = lexicalSkeleton(joinedQari)
    if (hLex.length >= 4 && (hLex == jLex || hLex == jqLex)) return true

    if (jr.length >= 5 && hr.length >= 4) {
      if (max(lcsSimilarity(jr, hr), lcsSimilarity(jqr, hr)) >= 0.84) {
        return true
      }
    }
    return false
  }

  /**
   * Handles Wasl carryover when `word[cursor - 1]` was already unlocked (e.g. via BPE spike)
   * while CTC decoded `word[cursor - 1] + word[cursor]` as a single fused token (e.g. `"إنالذين"`
   * when `cursor` is now on `"الذين"`).
   */
  fun matchesWaslCarryover(
    hypWord: String,
    prevNorm: String,
    prevQari: String,
    targetNorm: String,
    targetQari: String
  ): Boolean {
    val targetRasm = QuranRecitationGrader.phoneticRasm(targetNorm)
    val targetLex = lexicalSkeleton(targetNorm)
    if (targetRasm.length < 2 && targetLex.length < 3) return false
    return matchesMergedTwoWords(hypWord, prevNorm, prevQari, targetNorm, targetQari)
  }

  /**
   * Near lookahead (`+1` or `+2` words ahead): allows words with `>= 3` lexical letters (such as
   * `سَوَآءٌ`, `قَالَ`, `كَانَ`, `آمَنَ`, `كَفَرُوا۟`, `عَلَيْهِمْ`) or `>= 2` Rasm consonants
   * (`لَمْ`, `هُمْ`, `عَنْ`) as long as the lookahead word doesn't have the exact same skeleton
   * as the currently awaited frontier word.
   */
  fun canServeAsNearCatchUpAnchor(
    frontierNorm: String,
    frontierQari: String,
    lookNorm: String,
    lookQari: String
  ): Boolean {
    val fRasm = QuranRecitationGrader.phoneticRasm(frontierNorm)
    val fqRasm = QuranRecitationGrader.phoneticRasm(frontierQari.ifEmpty { frontierNorm })
    val lRasm = QuranRecitationGrader.phoneticRasm(lookNorm)
    val lqRasm = QuranRecitationGrader.phoneticRasm(lookQari.ifEmpty { lookNorm })
    val fLex = lexicalSkeleton(frontierNorm)
    val lLex = lexicalSkeleton(lookNorm)

    if (lRasm == fRasm && lLex == fLex) return false
    if (lqRasm == fqRasm && lLex == fLex) return false

    val maxRasmLen = max(lRasm.length, lqRasm.length)
    val maxLexLen = max(lLex.length, lexicalSkeleton(lookQari.ifEmpty { lookNorm }).length)
    return maxRasmLen >= 3 || maxLexLen >= 3 || maxRasmLen >= 2
  }

  /**
   * Far single-word catch-up (`+3 .. +6` words ahead): requires a distinctive word (`>= 4` Rasm
   * consonants or `>= 5` lexical letters) whose Rasm skeleton does not appear among the skipped
   * intermediate words.
   */
  fun canServeAsFarSingleWordAnchor(
    lookNorm: String,
    lookQari: String,
    intermediateRasms: Set<String>
  ): Boolean {
    val lRasm = QuranRecitationGrader.phoneticRasm(lookNorm)
    val lqRasm = QuranRecitationGrader.phoneticRasm(lookQari.ifEmpty { lookNorm })
    if (lRasm in intermediateRasms || lqRasm in intermediateRasms) return false
    val maxRasmLen = max(lRasm.length, lqRasm.length)
    val maxLexLen = max(
      lexicalSkeleton(lookNorm).length,
      lexicalSkeleton(lookQari.ifEmpty { lookNorm }).length
    )
    return maxRasmLen >= 4 || maxLexLen >= 5
  }

  fun lcsSimilarity(a: String, b: String): Double {
    if (a == b) return 1.0
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val n = a.length
    val m = b.length
    val prev = IntArray(m + 1)
    val curr = IntArray(m + 1)
    for (i in 1..n) {
      val ca = a[i - 1]
      for (j in 1..m) {
        curr[j] = if (ca == b[j - 1]) {
          prev[j - 1] + 1
        } else {
          max(prev[j], curr[j - 1])
        }
      }
      System.arraycopy(curr, 0, prev, 0, m + 1)
      curr.fill(0)
    }
    return (2.0 * prev[m].toDouble()) / (n + m).toDouble()
  }

  fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val n = a.length
    val m = b.length
    var prev = IntArray(m + 1) { it }
    var curr = IntArray(m + 1)
    for (i in 1..n) {
      curr[0] = i
      val ca = a[i - 1]
      for (j in 1..m) {
        val cost = if (ca == b[j - 1]) 0 else 1
        curr[j] = min(
          min(curr[j - 1] + 1, prev[j] + 1),
          prev[j - 1] + cost
        )
      }
      val tmp = prev
      prev = curr
      curr = tmp
    }
    return prev[m]
  }
}
