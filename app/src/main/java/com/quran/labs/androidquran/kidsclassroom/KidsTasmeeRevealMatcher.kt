package com.quran.labs.androidquran.kidsclassroom

import com.quran.labs.androidquran.memorization.tasmee.QuranAsrReferenceRepository
import com.quran.labs.androidquran.memorization.tasmee.TasmeeFollowAlongAlignment
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Monotonic streaming word-reveal matcher for Children Learning Mode **Mode B**:
 * **"Recite & Reveal / Tasmee'" (`التسميع الحي` — Streaming Blank-to-Revealed Mushaf, No Scoring)**.
 *
 * Follows along with the reciter without penalizing ASR misses:
 * 1. Matches the frontier word via forgiving [TasmeeFollowAlongAlignment.matchesFollowAlongWord],
 *    connected-speech Wasl merged tokens (`1-hyp -> 2-ref`), split compound tokens (`2-hyp -> 1-ref`),
 *    Wasl carryover, or a `>= 3`-letter Rasm prefix on the trailing spoken word.
 * 2. **Multi-Word Catch-Up (`+1` up to `+8` words ahead)**: if the ASR model misses the immediate
 *    next word(s) and catches subsequent word(s), reveals all words up to what the model heard via
 *    2-word phrase anchors (`+1..+8`), near single-word anchors (`+1..+2`), or distinctive single-word
 *    anchors (`+3..+6`).
 * 3. **Sub-Word BPE Posterior Trigger**: unlocks the target word when the mean peak probability of
 *    its first 2 BPE `token_ids` in the last `35%` of frames is `>= 0.58f`.
 */
object KidsTasmeeRevealMatcher {

  const val MIN_PREFIX_RASM_LEN = 3
  const val MIN_LOOKAHEAD_RASM_LEN = 3
  const val TAIL_FRAME_START_FRACTION = 0.65f // last 35% of frames
  const val MIN_TAIL_BPE_PEAK_PROB = 0.58f
  private const val MAX_CATCH_UP_OFFSET = 8
  private const val MAX_FAR_SINGLE_OFFSET = 6

  data class AyahRevealSpec(
    val uthmaniWords: List<String>,
    val normalizedWords: List<String>,
    val qariOnnxWords: List<String>,
    val wordTokenIds: List<IntArray>,
    val surah: Int = 0,
    val ayah: Int = 0
  ) {
    val wordCount: Int
      get() = uthmaniWords.size

    companion object {
      fun fromVerse(verse: TeacherVerseSpec, vocab: Array<String>?): AyahRevealSpec {
        val uth = verse.allUthmaniWords
        val norm = verse.allNormalizedWords
        val qari = verse.allQariOnnxWords
        val groupedTokens = QuranAsrReferenceRepository.groupVerseTokenIdsByWord(
          tokenIds = verse.allTokenIds,
          wordCount = uth.size,
          vocab = vocab
        )
        return AyahRevealSpec(
          uthmaniWords = uth,
          normalizedWords = norm,
          qariOnnxWords = qari,
          wordTokenIds = groupedTokens,
          surah = verse.surah,
          ayah = verse.ayah
        )
      }
    }
  }

  /**
   * Computes the updated monotonic `revealedCount` in `[currentRevealedCount, spec.wordCount]`.
   *
   * @param hadSilenceGapSinceLastReveal true if the rolling stream observed a silence pause since
   *   `currentRevealedCount` last changed, allowing identical consecutive words (e.g. `دَكًّا دَكًّا`)
   *   spoken after a pause to match immediately without suffix-consumption suppression.
   */
  fun computeNextRevealedCount(
    spec: AyahRevealSpec,
    currentRevealedCount: Int,
    hypWords: List<String>,
    logprobs: Array<FloatArray>,
    hadSilenceGapSinceLastReveal: Boolean = false
  ): Int {
    val totalWords = spec.wordCount
    if (totalWords <= 0) return 0
    var revealed = currentRevealedCount.coerceIn(0, totalWords)
    if (revealed >= totalWords) return totalWords

    var hypIdx = if (hypWords.isNotEmpty()) {
      if (revealed > 0 && !hadSilenceGapSinceLastReveal) {
        consumeAlreadyRevealedSubsequence(spec, revealed, hypWords)
      } else {
        skipLeadingBasmalahIfNeeded(spec, hypWords)
      }
    } else {
      0
    }

    val initialRevealed = revealed

    while (revealed < totalWords && hypIdx < hypWords.size) {
      val hypWord = hypWords[hypIdx]
      val isLastHypWord = hypIdx == hypWords.lastIndex

      val targetNorm = spec.normalizedWords.getOrElse(revealed) { spec.uthmaniWords[revealed] }
      val targetQari = spec.qariOnnxWords.getOrElse(revealed) { targetNorm }

      // Rule 1a: Merged 2-word Wasl/Idgham token (e.g. "إنالذين" -> "إِنَّ" + "ٱلَّذِينَ")
      if (revealed + 1 < totalWords) {
        val nextNorm = spec.normalizedWords.getOrElse(revealed + 1) { spec.uthmaniWords[revealed + 1] }
        val nextQari = spec.qariOnnxWords.getOrElse(revealed + 1) { nextNorm }
        if (TasmeeFollowAlongAlignment.matchesMergedTwoWords(
            hypWord,
            targetNorm,
            targetQari,
            nextNorm,
            nextQari
          )
        ) {
          revealed = (revealed + 2).coerceAtMost(totalWords)
          hypIdx++
          continue
        }
      }

      // Rule 1b: Forgiving follow-along word match on target word at `revealed`
      if (TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWord, targetNorm, targetQari)) {
        val isExactHere = TasmeeFollowAlongAlignment.matchesExactSkeleton(hypWord, targetNorm, targetQari)
        val hasBetterUpcomingExact = !isExactHere &&
          ((revealed + 1)..min(totalWords - 1, revealed + 6)).any { aheadIdx ->
            val aheadNorm = spec.normalizedWords.getOrElse(aheadIdx) { spec.uthmaniWords[aheadIdx] }
            val aheadQari = spec.qariOnnxWords.getOrElse(aheadIdx) { aheadNorm }
            TasmeeFollowAlongAlignment.matchesExactSkeleton(hypWord, aheadNorm, aheadQari)
          }
        if (!hasBetterUpcomingExact) {
          revealed++
          hypIdx++
          continue
        }
      }

      // Rule 1c: Split 2-hypothesis compound word (e.g. "يَا" + "أَيُّهَا" -> "يَٰٓأَيُّهَا")
      if (hypIdx + 1 < hypWords.size &&
        TasmeeFollowAlongAlignment.matchesFollowAlongWord(
          hypWord + hypWords[hypIdx + 1],
          targetNorm,
          targetQari
        )
      ) {
        revealed++
        hypIdx += 2
        continue
      }

      // Rule 1d: Wasl carryover when previous word fused onto current hypWord
      if (revealed > 0) {
        val prevNorm = spec.normalizedWords.getOrElse(revealed - 1) { spec.uthmaniWords[revealed - 1] }
        val prevQari = spec.qariOnnxWords.getOrElse(revealed - 1) { prevNorm }
        if (TasmeeFollowAlongAlignment.matchesWaslCarryover(
            hypWord,
            prevNorm,
            prevQari,
            targetNorm,
            targetQari
          )
        ) {
          revealed++
          hypIdx++
          continue
        }
      }

      // Rule 1e: >= 3-letter Rasm prefix on the last spoken word
      if (isLastHypWord && matchesRasmPrefixAtLeast3(hypWord, targetNorm, targetQari)) {
        revealed++
        hypIdx++
        break
      }

      // Rule 2: Multi-Word Follow-Along Catch-Up (+1 up to +8 words ahead)
      val catchUp = findCatchUpInUnconsumed(spec, revealed, hypWords, hypIdx)
      if (catchUp != null) {
        revealed = catchUp.first.coerceAtMost(totalWords)
        hypIdx = catchUp.second
        continue
      }

      // Unmatched noise/hesitation token in hypothesis; advance hypothesis cursor
      hypIdx++
    }

    // Rule 3: BPE tail peak posterior >= 0.58f on the first 2 tokens of target word
    if (revealed == initialRevealed && revealed < totalWords && logprobs.isNotEmpty()) {
      val tids = spec.wordTokenIds.getOrNull(revealed)
      if (tids != null && tids.isNotEmpty()) {
        val tailPeakMean = computeTailFirstTwoBpePeakMean(logprobs, tids)
        if (tailPeakMean >= MIN_TAIL_BPE_PEAK_PROB) {
          revealed++
        }
      }
    }

    return revealed.coerceIn(currentRevealedCount, totalWords)
  }

  private fun findCatchUpInUnconsumed(
    spec: AyahRevealSpec,
    cursor: Int,
    hypWords: List<String>,
    hypStart: Int
  ): Pair<Int, Int>? {
    val totalWords = spec.wordCount
    val maxOffset = min(MAX_CATCH_UP_OFFSET, totalWords - 1 - cursor)
    if (maxOffset < 1 || hypStart >= hypWords.size) return null

    val frontierNorm = spec.normalizedWords.getOrElse(cursor) { spec.uthmaniWords[cursor] }
    val frontierQari = spec.qariOnnxWords.getOrElse(cursor) { frontierNorm }

    // Near catch-up (+1..+2)
    val nearMax = min(2, maxOffset)
    for (offset in 1..nearMax) {
      val lookIdx = cursor + offset
      val lookNorm = spec.normalizedWords.getOrElse(lookIdx) { spec.uthmaniWords[lookIdx] }
      val lookQari = spec.qariOnnxWords.getOrElse(lookIdx) { lookNorm }

      if (lookIdx + 1 < totalWords) {
        val look2Norm = spec.normalizedWords.getOrElse(lookIdx + 1) { spec.uthmaniWords[lookIdx + 1] }
        val look2Qari = spec.qariOnnxWords.getOrElse(lookIdx + 1) { look2Norm }
        for (j in hypStart until hypWords.size) {
          if (j + 1 < hypWords.size &&
            TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[j], lookNorm, lookQari) &&
            TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[j + 1], look2Norm, look2Qari)
          ) {
            return (lookIdx + 2) to (j + 2)
          }
          if (TasmeeFollowAlongAlignment.matchesMergedTwoWords(
              hypWords[j],
              lookNorm,
              lookQari,
              look2Norm,
              look2Qari
            )
          ) {
            return (lookIdx + 2) to (j + 1)
          }
        }
      }

      if (TasmeeFollowAlongAlignment.canServeAsNearCatchUpAnchor(
          frontierNorm = frontierNorm,
          frontierQari = frontierQari,
          lookNorm = lookNorm,
          lookQari = lookQari
        )
      ) {
        for (j in hypStart until hypWords.size) {
          if (TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[j], lookNorm, lookQari)) {
            return (lookIdx + 1) to (j + 1)
          }
          if (j + 1 < hypWords.size &&
            TasmeeFollowAlongAlignment.matchesFollowAlongWord(
              hypWords[j] + hypWords[j + 1],
              lookNorm,
              lookQari
            )
          ) {
            return (lookIdx + 1) to (j + 2)
          }
        }
      }
    }

    // Far catch-up (+3..+8 via 2-word phrase, or +3..+6 via distinctive single word)
    if (maxOffset >= 3) {
      val intermediateRasms = HashSet<String>(maxOffset * 2)
      for (idx in cursor..min(cursor + 2, totalWords - 1)) {
        val n = spec.normalizedWords.getOrElse(idx) { spec.uthmaniWords[idx] }
        val q = spec.qariOnnxWords.getOrElse(idx) { n }
        intermediateRasms.add(QuranRecitationGrader.phoneticRasm(n))
        intermediateRasms.add(QuranRecitationGrader.phoneticRasm(q))
      }

      for (offset in 3..maxOffset) {
        val lookIdx = cursor + offset
        val lookNorm = spec.normalizedWords.getOrElse(lookIdx) { spec.uthmaniWords[lookIdx] }
        val lookQari = spec.qariOnnxWords.getOrElse(lookIdx) { lookNorm }

        if (lookIdx + 1 < totalWords) {
          val look2Norm = spec.normalizedWords.getOrElse(lookIdx + 1) { spec.uthmaniWords[lookIdx + 1] }
          val look2Qari = spec.qariOnnxWords.getOrElse(lookIdx + 1) { look2Norm }
          val substantial = max(
            TasmeeFollowAlongAlignment.lexicalSkeleton(lookNorm).length,
            TasmeeFollowAlongAlignment.lexicalSkeleton(look2Norm).length
          ) >= 3
          if (substantial) {
            for (j in hypStart until hypWords.size) {
              if (j + 1 < hypWords.size &&
                TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[j], lookNorm, lookQari) &&
                TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[j + 1], look2Norm, look2Qari)
              ) {
                return (lookIdx + 2) to (j + 2)
              }
              if (TasmeeFollowAlongAlignment.matchesMergedTwoWords(
                  hypWords[j],
                  lookNorm,
                  lookQari,
                  look2Norm,
                  look2Qari
                )
              ) {
                return (lookIdx + 2) to (j + 1)
              }
            }
          }
        }

        if (offset <= MAX_FAR_SINGLE_OFFSET &&
          TasmeeFollowAlongAlignment.canServeAsFarSingleWordAnchor(
            lookNorm = lookNorm,
            lookQari = lookQari,
            intermediateRasms = intermediateRasms
          )
        ) {
          for (j in hypStart until hypWords.size) {
            if (TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[j], lookNorm, lookQari)) {
              return (lookIdx + 1) to (j + 1)
            }
          }
        }

        intermediateRasms.add(QuranRecitationGrader.phoneticRasm(lookNorm))
        intermediateRasms.add(QuranRecitationGrader.phoneticRasm(lookQari))
      }
    }

    return null
  }

  /**
   * Computes the mean peak probability `exp(logprobs[t][tid])` of the word's first up-to-2 BPE
   * `token_ids` across the trailing `35%` of acoustic frames (`t in (T * 0.65).toInt() until T`).
   */
  fun computeTailFirstTwoBpePeakMean(
    logprobs: Array<FloatArray>,
    wordTokenIds: IntArray
  ): Float {
    val totalFrames = logprobs.size
    if (totalFrames <= 0 || wordTokenIds.isEmpty()) return 0f
    val startFrame = (totalFrames * TAIL_FRAME_START_FRACTION).toInt().coerceIn(0, totalFrames - 1)
    val count = minOf(2, wordTokenIds.size)
    var sumPeak = 0f

    for (i in 0 until count) {
      val tid = wordTokenIds[i]
      var peakProb = 0f
      for (t in startFrame until totalFrames) {
        val frame = logprobs[t]
        if (tid in frame.indices) {
          val prob = exp(frame[tid].coerceIn(-30f, 0f))
          if (prob > peakProb) {
            peakProb = prob
          }
        }
      }
      sumPeak += peakProb
    }
    return sumPeak / count.toFloat()
  }

  internal fun matchesRasmPrefixAtLeast3(
    hypWord: String,
    targetNorm: String,
    targetQari: String
  ): Boolean {
    val hr = QuranRecitationGrader.phoneticRasm(hypWord)
    if (hr.length < MIN_PREFIX_RASM_LEN) return false
    val rr = QuranRecitationGrader.phoneticRasm(targetNorm)
    val qr = QuranRecitationGrader.phoneticRasm(targetQari.ifEmpty { targetNorm })
    return (rr.length >= hr.length && rr.startsWith(hr)) ||
      (qr.length >= hr.length && qr.startsWith(hr))
  }

  private fun skipLeadingBasmalahIfNeeded(
    spec: AyahRevealSpec,
    hypWords: List<String>
  ): Int {
    if (hypWords.isEmpty()) return 0
    if (spec.surah == 1 || (spec.ayah != 0 && spec.ayah != 1)) return 0
    val firstTargetNorm = spec.normalizedWords.firstOrNull() ?: spec.uthmaniWords.firstOrNull().orEmpty()
    val firstTargetRasm = QuranRecitationGrader.phoneticRasm(firstTargetNorm)
    var hCursor = 0
    while (hCursor < min(4, hypWords.size)) {
      val hr = QuranRecitationGrader.phoneticRasm(hypWords[hCursor])
      if (hr in TasmeeFollowAlongAlignment.BASMALAH_RASMS && hr != firstTargetRasm) {
        hCursor++
      } else {
        break
      }
    }
    return hCursor
  }

  /**
   * Aligns already-revealed words `spec[0 until revealedCount]` monotonically onto [hypWords] so
   * even if an intermediate word was previously revealed via catch-up, subsequent revealed words
   * in the 4.5s rolling window are still properly consumed and never double-trigger repeated words.
   */
  private fun consumeAlreadyRevealedSubsequence(
    spec: AyahRevealSpec,
    revealedCount: Int,
    hypWords: List<String>
  ): Int {
    var hCursor = skipLeadingBasmalahIfNeeded(spec, hypWords)
    if (revealedCount <= 0 || hCursor >= hypWords.size) {
      return hCursor.coerceIn(0, hypWords.size)
    }

    val refStart = max(0, revealedCount - (hypWords.size + 4))
    var rIdx = refStart
    while (rIdx < revealedCount && hCursor < hypWords.size) {
      val refNorm = spec.normalizedWords.getOrElse(rIdx) { spec.uthmaniWords[rIdx] }
      val refQari = spec.qariOnnxWords.getOrElse(rIdx) { refNorm }
      val searchLimit = min(hypWords.size, hCursor + 3)
      var foundAt = -1
      var consumedRef = 1
      var consumedHyp = 1

      for (hIdx in hCursor until searchLimit) {
        if (rIdx + 1 < revealedCount) {
          val nextNorm = spec.normalizedWords.getOrElse(rIdx + 1) { spec.uthmaniWords[rIdx + 1] }
          val nextQari = spec.qariOnnxWords.getOrElse(rIdx + 1) { nextNorm }
          if (TasmeeFollowAlongAlignment.matchesMergedTwoWords(
              hypWords[hIdx],
              refNorm,
              refQari,
              nextNorm,
              nextQari
            )
          ) {
            foundAt = hIdx
            consumedRef = 2
            consumedHyp = 1
            break
          }
        }
        if (TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[hIdx], refNorm, refQari) ||
          matchesRasmPrefixAtLeast3(hypWords[hIdx], refNorm, refQari)
        ) {
          foundAt = hIdx
          consumedRef = 1
          consumedHyp = 1
          break
        }
        if (hIdx + 1 < hypWords.size &&
          TasmeeFollowAlongAlignment.matchesFollowAlongWord(
            hypWords[hIdx] + hypWords[hIdx + 1],
            refNorm,
            refQari
          )
        ) {
          foundAt = hIdx
          consumedRef = 1
          consumedHyp = 2
          break
        }
      }

      if (foundAt != -1) {
        hCursor = foundAt + consumedHyp
        rIdx += consumedRef
      } else {
        rIdx += 1
      }
    }

    return hCursor.coerceIn(0, hypWords.size)
  }
}
