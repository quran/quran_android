package com.quran.labs.androidquran.kidsclassroom

import com.quran.data.model.SuraAyah
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Implements the Two-Tier Quranic Text & Tashkeel Grader specified in `ANDROID_AI_STUDIO_GUIDE.md` (§4.4):
 *
 * 1. **CTC Greedy Decode**:
 *    `pred[t] = argmax_c(logprobs[t, c])`, collapse consecutive duplicates, drop `blank_id == 1024`,
 *    join `vocab[id]`, replace `"▁"` (`\u2581`) with `" "`, trim, and split into `hypWords`.
 * 2. **Phonetic Rasm Skeleton (`phoneticRasm(word)`)**:
 *    Remove diacritics `[\u064B-\u065F\u0670\u06D6-\u06ED]`, normalize `[أإآٱءؤئ] -> "ا"`, `"ى" -> "ي"`,
 *    `"ة" -> "ه"`, remove all `"ا"`.
 *    Rasm matches iff `hr == rr || hr == qr` OR `(rr.length >= 5 && max(ratio(rr, hr), ratio(qr, hr)) >= 0.88)`.
 *    Never lowers `0.88` on short words so letter/Makharij errors (`سراط` vs `صراط`, `الدالين` vs `الضالين`)
 *    are caught as `WORD_ERROR`.
 * 3. **Internal & Ending Harakah Check (`findTashkeelMismatch`)**:
 *    Active vowels: `َ (\u064E)`, `ُ (\u064F)`, `ِ (\u0650)`, `ً (\u064B)`, `ٌ (\u064C)`, `ٍ (\u064D)`.
 *    Compares both final active vowel (ignoring trailing `\u0651` shaddah / `\u0652` sukoon) and medial
 *    `(consonant, activeVowel)` pairs against **both** `normalized_words[i]` and `qari_onnx_words[i]`.
 * 4. **Selective Monotonic BPE Token Posteriors & Weighted Score**:
 *    `Score = round(100 * (0.55 * S_word + 0.25 * S_tashkeel + 0.20 * S_conf), 1)`
 *    `passed = (Score >= 65.0 && S_word >= 0.65)`.
 */
object QuranRecitationGrader {

  private val DIACRITICS_REGEX = Regex("[\u064B-\u065F\u0670\u06D6-\u06ED]")
  private val HAMZA_VARIANTS_REGEX = Regex("[أإآٱءؤئ]")

  private val ACTIVE_VOWELS = setOf(
    '\u064E', // Fatha
    '\u064F', // Damma
    '\u0650', // Kasra
    '\u064B', // Fathatan
    '\u064C', // Dammatan
    '\u064D'  // Kasratan
  )

  private const val SHADDAH = '\u0651'
  private const val SUKOON = '\u0652'

  /**
   * Produces a `TurnEvaluationResult` when VAD detects no speech (`peakRms < 0.004f` or `< 100ms`).
   */
  fun buildNoSpeechResult(suraAyah: SuraAyah, turn: TurnSpec): TurnEvaluationResult {
    val wordCount = turn.uthmaniWords.size
    val grades = List(wordCount) { idx ->
      val norm = turn.normalizedWords.getOrElse(idx) { turn.uthmaniWords[idx] }
      WordGrade(
        wordIndexInTurn = idx,
        mushafWordPosition = turn.wordStartIdx + idx + 1,
        uthmaniWord = turn.uthmaniWords[idx],
        normalizedWord = norm,
        status = WordGradeStatus.MISSING,
        tokenConfidence = 0f,
        matchedHypWord = null
      )
    }
    return TurnEvaluationResult(
      suraAyah = suraAyah,
      turnIndex = turn.turnIndex,
      totalTurns = turn.totalTurns,
      scorePercent = 0.0,
      wordAccuracy = 0.0,
      tashkeelAccuracy = 0.0,
      confidenceScore = 0.0,
      passed = false,
      noSpeechDetected = true,
      decodedTranscript = "",
      wordGrades = grades
    )
  }

  /**
   * Greedy CTC decoding from `logprobs` (`[T_out][1025]`).
   */
  fun greedyCtcDecode(
    logprobs: Array<FloatArray>,
    vocab: Array<String>,
    blankId: Int = FastConformerAsrEngine.BLANK_ID
  ): List<String> {
    if (logprobs.isEmpty()) return emptyList()
    val sb = StringBuilder()
    var prevId = -1
    for (t in logprobs.indices) {
      val frame = logprobs[t]
      if (frame.isEmpty()) continue
      var bestId = 0
      var bestVal = frame[0]
      for (c in 1 until frame.size) {
        if (frame[c] > bestVal) {
          bestVal = frame[c]
          bestId = c
        }
      }
      if (bestId != prevId) {
        if (bestId != blankId && bestId in vocab.indices) {
          sb.append(vocab[bestId])
        }
        prevId = bestId
      }
    }
    val text = sb.toString().replace('\u2581', ' ').trim()
    if (text.isEmpty()) return emptyList()
    return text.split(Regex("\\s+")).filter { it.isNotEmpty() }
  }

  /**
   * Computes the Phonetic Rasm skeleton of a Quranic word.
   */
  fun phoneticRasm(word: String): String {
    if (word.isEmpty()) return ""
    val noMarks = word.replace(DIACRITICS_REGEX, "")
      .replace("ـ", "")
      .replace(HAMZA_VARIANTS_REGEX, "ا")
      .replace('ى', 'ي')
      .replace('ة', 'ه')
      .replace("ا", "")
    val sb = StringBuilder(noMarks.length)
    for (ch in noMarks) {
      if (ch in '\u0621'..'\u064A') {
        sb.append(ch)
      }
    }
    return sb.toString()
  }

  /**
   * Checks whether `hypWord` matches `refNormWord` (or `qariOnnxWord`) in Phonetic Rasm skeleton.
   */
  fun rasmMatches(
    hypWord: String,
    refNormWord: String,
    qariOnnxWord: String = refNormWord
  ): Boolean = matchesPhoneticRasm(hypWord, refNormWord, qariOnnxWord)

  /**
   * Checks whether `hypWord` matches either `refNormWord` or `qariOnnxWord` in Rasm skeleton.
   */
  fun matchesPhoneticRasm(hypWord: String, refNormWord: String, qariOnnxWord: String): Boolean {
    val hr = phoneticRasm(hypWord)
    val rr = phoneticRasm(refNormWord)
    val qr = phoneticRasm(qariOnnxWord.ifEmpty { refNormWord })

    if (hr.isEmpty() && rr.isEmpty()) return true
    if (hr.isEmpty() || rr.isEmpty()) return false
    if (hr == rr || hr == qr) return true

    // Only allow fuzzy >= 0.88 on long words (rr.length >= 5) so short words like صراط / الضالين
    // strictly require exact consonant skeleton match.
    if (rr.length >= 5) {
      val simRef = similarityRatio(rr, hr)
      val simQari = similarityRatio(qr, hr)
      if (max(simRef, simQari) >= 0.88) {
        return true
      }
    }
    return false
  }

  /**
   * Checks whether `hypWord` has an explicit active vowel (internal or ending harakah) that
   * conflicts with **both** `refNormWord` and `qariOnnxWord`.
   */
  fun hasTashkeelMismatch(
    hypWord: String,
    refNormWord: String,
    qariOnnxWord: String = refNormWord
  ): Boolean = findTashkeelMismatch(hypWord, refNormWord, qariOnnxWord)

  /**
   * Checks whether `hypWord` has an explicit active vowel (internal or ending harakah) that
   * conflicts with **both** `refNormWord` and `qariOnnxWord`.
   */
  fun findTashkeelMismatch(hypWord: String, refNormWord: String, qariOnnxWord: String): Boolean {
    val effectiveQari = qariOnnxWord.ifEmpty { refNormWord }
    val conflictsWithRef = hasExplicitVowelConflict(hypWord, refNormWord)
    val conflictsWithQari = hasExplicitVowelConflict(hypWord, effectiveQari)
    return conflictsWithRef && conflictsWithQari
  }

  private fun hasExplicitVowelConflict(hypWord: String, targetWord: String): Boolean {
    // 1. Check final active vowel (ignoring trailing shaddah/sukoon)
    val hypFinal = extractFinalActiveVowel(hypWord)
    val targetFinal = extractFinalActiveVowel(targetWord)
    if (hypFinal != null && targetFinal != null && !vowelsCompatible(hypFinal, targetFinal)) {
      return true
    }

    // 2. Check internal / per-consonant active vowels
    val hypPairs = extractConsonantVowelSequence(hypWord)
    val targetPairs = extractConsonantVowelSequence(targetWord)
    if (hypPairs.isEmpty() || targetPairs.isEmpty()) return false

    if (hypPairs.size == targetPairs.size) {
      for (i in hypPairs.indices) {
        val (hCons, hVowel) = hypPairs[i]
        val (tCons, tVowel) = targetPairs[i]
        if (hCons == tCons && hVowel != null && tVowel != null && !vowelsCompatible(hVowel, tVowel)) {
          return true
        }
      }
    } else {
      // Align consonant-by-consonant when an alef/hamza carrier differs in length
      var tIdx = 0
      for ((hCons, hVowel) in hypPairs) {
        while (tIdx < targetPairs.size && targetPairs[tIdx].first != hCons) {
          tIdx++
        }
        if (tIdx < targetPairs.size) {
          val tVowel = targetPairs[tIdx].second
          if (hVowel != null && tVowel != null && !vowelsCompatible(hVowel, tVowel)) {
            return true
          }
          tIdx++
        }
      }
    }
    return false
  }

  private fun vowelsCompatible(v1: Char, v2: Char): Boolean {
    if (v1 == v2) return true
    // Treat tanween and its corresponding short vowel as distinct unless identical,
    // except when pause/mad normalization converts fathatan to fatha before alef.
    return false
  }

  private fun extractFinalActiveVowel(word: String): Char? {
    for (i in word.length - 1 downTo 0) {
      val ch = word[i]
      if (ch == SHADDAH || ch == SUKOON) continue
      if (ch in ACTIVE_VOWELS) return ch
      if (ch in '\u0621'..'\u064A') {
        // Reached the final base letter without finding an active vowel on it;
        // if the final letter is a long vowel carrier (ا / و / ي), check the letter just before it
        if (ch == 'ا' || ch == 'و' || ch == 'ي' || ch == 'ى') {
          continue
        }
        return null
      }
    }
    return null
  }

  private fun extractConsonantVowelSequence(word: String): List<Pair<Char, Char?>> {
    val result = mutableListOf<Pair<Char, Char?>>()
    var currentConsonant: Char? = null
    var currentVowel: Char? = null

    for (ch in word) {
      val normalizedCh = when (ch) {
        'أ', 'إ', 'آ', 'ٱ', 'ء', 'ؤ', 'ئ' -> 'ا'
        'ى' -> 'ي'
        'ة' -> 'ه'
        else -> ch
      }
      if (normalizedCh in '\u0621'..'\u064A') {
        if (currentConsonant != null && currentConsonant != 'ا') {
          result.add(currentConsonant to currentVowel)
        }
        currentConsonant = normalizedCh
        currentVowel = null
      } else if (ch in ACTIVE_VOWELS) {
        currentVowel = ch
      }
    }
    if (currentConsonant != null && currentConsonant != 'ا') {
      result.add(currentConsonant to currentVowel)
    }
    return result
  }

  /**
   * Evaluates a child's recitation turn given ONNX `logprobs` (`[T_out][1025]`) and `vocab`.
   */
  fun evaluateTurn(
    suraAyah: SuraAyah,
    turn: TurnSpec,
    logprobs: Array<FloatArray>,
    vocab: Array<String>,
    blankId: Int = FastConformerAsrEngine.BLANK_ID,
    passThresholdPercent: Double = 65.0,
    minWordAccuracy: Double = 0.65
  ): TurnEvaluationResult {
    val hypWords = greedyCtcDecode(logprobs, vocab, blankId)
    val wordConfidences = computeSelectiveMonotonicTokenConfidences(
      turn = turn,
      logprobs = logprobs,
      vocab = vocab
    )
    return gradeDecodedWords(
      suraAyah = suraAyah,
      turn = turn,
      hypWords = hypWords,
      wordConfidences = wordConfidences,
      passThresholdPercent = passThresholdPercent,
      minWordAccuracy = minWordAccuracy
    )
  }

  /**
   * Grades decoded `hypWords` against `turn` reference words and per-word BPE confidences.
   */
  fun gradeDecodedWords(
    suraAyah: SuraAyah,
    turn: TurnSpec,
    hypWords: List<String>,
    wordConfidences: FloatArray,
    passThresholdPercent: Double = 65.0,
    minWordAccuracy: Double = 0.65
  ): TurnEvaluationResult {
    val nRef = turn.uthmaniWords.size
    if (nRef == 0) {
      return TurnEvaluationResult(
        suraAyah = suraAyah,
        turnIndex = turn.turnIndex,
        totalTurns = turn.totalTurns,
        scorePercent = 100.0,
        wordAccuracy = 1.0,
        tashkeelAccuracy = 1.0,
        confidenceScore = 1.0,
        passed = true,
        noSpeechDetected = false,
        decodedTranscript = hypWords.joinToString(" "),
        wordGrades = emptyList()
      )
    }

    val alignedHypIndices = alignReferenceToHypothesis(
      refNormWords = turn.normalizedWords,
      qariOnnxWords = turn.qariOnnxWords,
      hypWords = hypWords
    )

    val grades = mutableListOf<WordGrade>()
    var rasmCorrectCount = 0
    var tashkeelCleanCount = 0

    for (i in 0 until nRef) {
      val uthmani = turn.uthmaniWords[i]
      val refNorm = turn.normalizedWords.getOrElse(i) { uthmani }
      val qariWord = turn.qariOnnxWords.getOrElse(i) { refNorm }
      val conf = wordConfidences.getOrElse(i) { 0f }.coerceIn(0f, 1f)
      val hypIdx = alignedHypIndices[i]

      if (hypIdx == -1) {
        grades.add(
          WordGrade(
            wordIndexInTurn = i,
            mushafWordPosition = turn.wordStartIdx + i + 1,
            uthmaniWord = uthmani,
            normalizedWord = refNorm,
            status = WordGradeStatus.MISSING,
            tokenConfidence = conf,
            matchedHypWord = null
          )
        )
        continue
      }

      val hypWord = hypWords[hypIdx]
      val rasmOk = matchesPhoneticRasm(hypWord, refNorm, qariWord)
      if (!rasmOk) {
        grades.add(
          WordGrade(
            wordIndexInTurn = i,
            mushafWordPosition = turn.wordStartIdx + i + 1,
            uthmaniWord = uthmani,
            normalizedWord = refNorm,
            status = WordGradeStatus.WORD_ERROR,
            tokenConfidence = conf,
            matchedHypWord = hypWord
          )
        )
      } else {
        rasmCorrectCount++
        val hasTashkeelErr = findTashkeelMismatch(hypWord, refNorm, qariWord)
        if (hasTashkeelErr) {
          grades.add(
            WordGrade(
              wordIndexInTurn = i,
              mushafWordPosition = turn.wordStartIdx + i + 1,
              uthmaniWord = uthmani,
              normalizedWord = refNorm,
              status = WordGradeStatus.TASHKEEL_ERROR,
              tokenConfidence = conf,
              matchedHypWord = hypWord
            )
          )
        } else {
          tashkeelCleanCount++
          grades.add(
            WordGrade(
              wordIndexInTurn = i,
              mushafWordPosition = turn.wordStartIdx + i + 1,
              uthmaniWord = uthmani,
              normalizedWord = refNorm,
              status = WordGradeStatus.CORRECT,
              tokenConfidence = conf,
              matchedHypWord = hypWord
            )
          )
        }
      }
    }

    val sWord = rasmCorrectCount.toDouble() / nRef.toDouble()
    val sTashkeel = if (rasmCorrectCount > 0) {
      tashkeelCleanCount.toDouble() / nRef.toDouble()
    } else {
      0.0
    }
    val sConf = if (wordConfidences.isNotEmpty()) {
      var sum = 0.0
      for (i in 0 until nRef) {
        sum += wordConfidences.getOrElse(i) { 0f }.coerceIn(0f, 1f)
      }
      (sum / nRef.toDouble()).coerceIn(0.0, 1.0)
    } else {
      sWord
    }

    val rawScore = 100.0 * (0.55 * sWord + 0.25 * sTashkeel + 0.20 * sConf)
    val roundedScore = (rawScore * 10.0).roundToInt() / 10.0
    val passed = roundedScore >= passThresholdPercent && sWord >= minWordAccuracy

    return TurnEvaluationResult(
      suraAyah = suraAyah,
      turnIndex = turn.turnIndex,
      totalTurns = turn.totalTurns,
      scorePercent = roundedScore,
      wordAccuracy = sWord,
      tashkeelAccuracy = sTashkeel,
      confidenceScore = sConf,
      passed = passed,
      noSpeechDetected = false,
      decodedTranscript = hypWords.joinToString(" "),
      wordGrades = grades
    )
  }

  /**
   * Computes selective monotonic BPE token posteriors (`exp(logprobs[t, tid])`)
   * grouped into `N` word groups at tokens starting with `"▁"` (`\u2581`).
   */
  fun computeSelectiveMonotonicTokenConfidences(
    turn: TurnSpec,
    logprobs: Array<FloatArray>,
    vocab: Array<String>
  ): FloatArray {
    val nWords = turn.uthmaniWords.size
    if (nWords == 0) return FloatArray(0)
    if (logprobs.isEmpty() || turn.tokenIds.isEmpty()) {
      return FloatArray(nWords) { 0f }
    }

    // Group turn.tokenIds into word groups at tokens starting with "▁"
    val wordTokenGroups = mutableListOf<MutableList<Int>>()
    for (tid in turn.tokenIds) {
      val tokStr = if (tid in vocab.indices) vocab[tid] else ""
      if (tokStr.startsWith('\u2581') || wordTokenGroups.isEmpty()) {
        wordTokenGroups.add(mutableListOf(tid))
      } else {
        wordTokenGroups.last().add(tid)
      }
    }

    // Precompute exp(logprobs[t, tid]) ONLY for unique tids in turn.tokenIds
    val uniqueTids = turn.tokenIds.distinct()
    val numFrames = logprobs.size
    val posteriorsByTid = HashMap<Int, FloatArray>(uniqueTids.size)
    for (tid in uniqueTids) {
      val arr = FloatArray(numFrames)
      for (t in 0 until numFrames) {
        val frame = logprobs[t]
        val lp = if (tid in frame.indices) frame[tid] else -30f
        arr[t] = exp(lp.coerceIn(-30f, 0f))
      }
      posteriorsByTid[tid] = arr
    }

    val confidences = FloatArray(nWords)
    var curF = 0
    val numGroups = wordTokenGroups.size

    for (wIdx in 0 until nWords) {
      val group = if (wIdx < numGroups) wordTokenGroups[wIdx] else wordTokenGroups.lastOrNull()
      if (group.isNullOrEmpty()) {
        confidences[wIdx] = 0f
        continue
      }

      val remainingWords = (nWords - wIdx).coerceAtLeast(1)
      val windowLimit = min(
        numFrames - 1,
        curF + max(12, ((numFrames - curF) * 2) / remainingWords)
      ).coerceAtLeast(curF)

      var tokenProbSum = 0f
      for (tid in group) {
        val post = posteriorsByTid[tid] ?: continue
        var bestP = 0f
        var bestFrame = curF
        val searchEnd = min(numFrames - 1, max(curF, windowLimit))
        for (f in curF..searchEnd) {
          val p = post[f]
          if (p > bestP) {
            bestP = p
            bestFrame = f
          }
        }
        tokenProbSum += bestP
        if (bestP >= 0.08f && bestFrame > curF) {
          curF = min(numFrames - 1, bestFrame)
        }
      }
      confidences[wIdx] = (tokenProbSum / group.size.toFloat()).coerceIn(0f, 1f)
    }

    return confidences
  }

  /**
   * Sequence alignment between reference words and decoded hypothesis words so a skipped
   * word is graded as `MISSING` while subsequent spoken words still align to their true reference index.
   */
  private fun alignReferenceToHypothesis(
    refNormWords: List<String>,
    qariOnnxWords: List<String>,
    hypWords: List<String>
  ): IntArray {
    val n = refNormWords.size
    val m = hypWords.size
    val result = IntArray(n) { -1 }
    if (n == 0 || m == 0) return result

    // DP table: score of aligning ref[0..i-1] with hyp[0..j-1]
    val dp = Array(n + 1) { DoubleArray(m + 1) }
    val choice = Array(n + 1) { IntArray(m + 1) } // 1 = pair(i-1, j-1), 2 = skip ref, 3 = skip hyp

    val gapPenalty = -0.35
    for (i in 1..n) {
      dp[i][0] = i * gapPenalty
      choice[i][0] = 2
    }
    for (j in 1..m) {
      dp[0][j] = j * gapPenalty
      choice[0][j] = 3
    }

    for (i in 1..n) {
      val refW = refNormWords[i - 1]
      val qariW = qariOnnxWords.getOrElse(i - 1) { refW }
      val rr = phoneticRasm(refW)
      val qr = phoneticRasm(qariW)

      for (j in 1..m) {
        val hypW = hypWords[j - 1]
        val hr = phoneticRasm(hypW)
        val sim = max(similarityRatio(rr, hr), similarityRatio(qr, hr))
        val pairGain = if (matchesPhoneticRasm(hypW, refW, qariW)) {
          2.0 + sim
        } else if (sim >= 0.45) {
          0.4 + sim
        } else {
          -0.25
        }

        val scorePair = dp[i - 1][j - 1] + pairGain
        val scoreSkipRef = dp[i - 1][j] + gapPenalty
        val scoreSkipHyp = dp[i][j - 1] + gapPenalty

        if (scorePair >= scoreSkipRef && scorePair >= scoreSkipHyp) {
          dp[i][j] = scorePair
          choice[i][j] = 1
        } else if (scoreSkipRef >= scoreSkipHyp) {
          dp[i][j] = scoreSkipRef
          choice[i][j] = 2
        } else {
          dp[i][j] = scoreSkipHyp
          choice[i][j] = 3
        }
      }
    }

    var i = n
    var j = m
    while (i > 0 && j > 0) {
      when (choice[i][j]) {
        1 -> {
          val refW = refNormWords[i - 1]
          val qariW = qariOnnxWords.getOrElse(i - 1) { refW }
          val hypW = hypWords[j - 1]
          val sim = max(
            similarityRatio(phoneticRasm(refW), phoneticRasm(hypW)),
            similarityRatio(phoneticRasm(qariW), phoneticRasm(hypW))
          )
          // Pair if Rasm matches or if similarity is at least a plausible attempt at the word
          if (matchesPhoneticRasm(hypW, refW, qariW) || sim >= 0.30 || n == m) {
            result[i - 1] = j - 1
          }
          i--
          j--
        }
        2 -> i--
        else -> j--
      }
    }
    return result
  }

  /**
   * Computes `1.0 - (levenshteinDistance(a, b) / max(a.length, b.length))` in `[0.0, 1.0]`.
   */
  fun similarityRatio(a: String, b: String): Double {
    if (a == b) return 1.0
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val dist = levenshteinDistance(a, b)
    val maxLen = max(a.length, b.length).toDouble()
    return (1.0 - dist.toDouble() / maxLen).coerceIn(0.0, 1.0)
  }

  private fun levenshteinDistance(a: String, b: String): Int {
    val n = a.length
    val m = b.length
    var prev = IntArray(m + 1) { it }
    var curr = IntArray(m + 1)
    for (i in 1..n) {
      curr[0] = i
      val ca = a[i - 1]
      for (j in 1..m) {
        val cost = if (ca == b[j - 1]) 0 else 1
        curr[j] = minOf(
          curr[j - 1] + 1,
          prev[j] + 1,
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
