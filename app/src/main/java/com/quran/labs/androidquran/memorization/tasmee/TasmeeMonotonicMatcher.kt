package com.quran.labs.androidquran.memorization.tasmee

import com.quran.labs.androidquran.kidsclassroom.FastConformerAsrEngine
import com.quran.labs.androidquran.kidsclassroom.QuranRecitationGrader
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Pure, zero-flicker **Monotonic Follow-Along Reveal Algorithm** for Live Recitation ("Tasmee' / تسميع"):
 *
 * Designed specifically for **following along with the reciter** (not testing/penalizing them):
 * 1. **Global Monotonic Word Cursor (`revealedWordCount`)**:
 *    Never decreases ("latch-on" state).
 * 2. **Dual-Signal Immediate Word Unlocking + Connected-Speech (Wasl) Handling**:
 *    - **Signal A — Follow-Along Phonetic / Lexical Match**:
 *      Matches unconsumed `hypWords` against `targetWord` via forgiving [TasmeeFollowAlongAlignment.matchesFollowAlongWord],
 *      supports 2-hypothesis split tokens (`"يَا أَيُّهَا"` -> `"يَٰٓأَيُّهَا"`), 1-hypothesis merged Wasl
 *      tokens (`"إنالذين"` -> `إِنَّ` + `ٱلَّذِينَ`), Wasl carryover from `cursor - 1`, and mid-word
 *      `>= 3`-letter Rasm prefix on the trailing word.
 *    - **Signal B — BPE Token Posterior Spike (Instant Sub-Word Trigger)**:
 *      Unlocks `targetWord` immediately when the first `2` BPE `token_ids` spike `> 0.55` in tail frames.
 * 3. **Multi-Tier Catch-Up / Skip-Ahead Recovery (`+1` up to `+8` words ahead)**:
 *    If the ASR model misses or garbles the immediate next word(s), catches up and reveals all words
 *    up to what the model heard via:
 *    - **2-Word Phrase Anchor (`+1..+8`)**: 2 consecutive reference words matched in unconsumed `hypWords`
 *      (or fused as one Wasl token).
 *    - **Near Single-Word Anchor (`+1..+2`)**: any word with `>= 3` lexical letters (e.g. `سَوَآءٌ`,
 *      `قَالَ`, `كَانَ`) or `>= 2` Rasm consonants (`لَمْ`, `هُمْ`) matched in unconsumed `hypWords`.
 *    - **Far Distinctive Single-Word Anchor (`+3..+6`)**: any distinctive word (`>= 4` Rasm or `>= 5`
 *      lexical letters) not repeated in the intermediate gap.
 *    - **Sub-Word BPE Spike Lookahead (`+1`)**: strong tail posterior spike (`> 0.66`) on a 4+ letter
 *      next word when the immediate word was missed.
 */
object TasmeeMonotonicMatcher {

  const val SIGNAL_B_POSTERIOR_THRESHOLD = 0.55f
  private const val LOOKAHEAD_SIGNAL_B_THRESHOLD = 0.66f
  private const val MIN_PREFIX_RASM_LETTERS = 3
  private const val SIGNAL_B_TAIL_FRAMES = 14
  private const val MAX_UNLOCKS_PER_HOP = 12
  private const val MAX_PHRASE_LOOKAHEAD_OFFSET = 8
  private const val MAX_FAR_SINGLE_LOOKAHEAD_OFFSET = 6

  enum class UnlockSignal {
    NONE,
    PHONETIC_RASM_FULL,
    PHONETIC_RASM_PREFIX,
    BPE_POSTERIOR_SPIKE,
    SKIP_AHEAD_RECOVERY
  }

  data class HopMatchResult(
    val previousRevealedCount: Int,
    val newRevealedCount: Int,
    val skippedWordIndices: Set<Int>,
    val primarySignal: UnlockSignal,
    val decodedHypWords: List<String>,
    val updatedMinSignalBFrame: Int
  ) {
    val didAdvance: Boolean
      get() = newRevealedCount > previousRevealedCount
  }

  fun evaluateHop(
    slice: PageAyahTasmeeSlice,
    currentRevealedCount: Int,
    logprobs: Array<FloatArray>,
    vocab: Array<String>,
    minSignalBFrame: Int = 0,
    blankId: Int = FastConformerAsrEngine.BLANK_ID
  ): HopMatchResult {
    val totalWords = slice.wordCount
    val clampedCurrent = currentRevealedCount.coerceIn(0, totalWords)
    if (totalWords == 0 || clampedCurrent >= totalWords || logprobs.isEmpty()) {
      return HopMatchResult(
        previousRevealedCount = clampedCurrent,
        newRevealedCount = clampedCurrent,
        skippedWordIndices = emptySet(),
        primarySignal = UnlockSignal.NONE,
        decodedHypWords = emptyList(),
        updatedMinSignalBFrame = minSignalBFrame
      )
    }

    val hypWords = QuranRecitationGrader.greedyCtcDecode(logprobs, vocab, blankId)
    val hypRasms = hypWords.map { QuranRecitationGrader.phoneticRasm(it) }

    // Monotonically align already-revealed prefix [0 until clampedCurrent] onto hypWords so
    // earlier occurrences of repeated words in the rolling window cannot re-trigger later ones.
    var unconsumedHypStart = computeUnconsumedHypStartIndex(
      slice = slice,
      revealedCount = clampedCurrent,
      hypWords = hypWords,
      hypRasms = hypRasms
    )

    var cursor = clampedCurrent
    var signalUsed = UnlockSignal.NONE
    var nextMinSignalBFrame = minSignalBFrame
    val newlySkipped = LinkedHashSet<Int>()
    var unlockedStepsThisHop = 0

    while (cursor < totalWords && unlockedStepsThisHop < MAX_UNLOCKS_PER_HOP) {
      val targetNorm = slice.normalizedWords[cursor]
      val targetQari = slice.qariOnnxWords.getOrElse(cursor) { targetNorm }
      val targetRasm = slice.rasmWords[cursor]
      val targetQariRasm = slice.qariRasmWords.getOrElse(cursor) { targetRasm }

      // --- Signal A: Immediate Follow-Along Match (1-to-1, 2-hyp split, 1-hyp Wasl merge, carryover, or prefix) ---
      val searchStart = unconsumedHypStart.coerceAtLeast(0)
      var matchedHypIndex = -1
      var consumedHypSpan = 1
      var unlockedRefWords = 1
      var matchedAsMidWordPrefix = false

      for (j in searchStart until hypWords.size) {
        val hypW = hypWords[j]
        val hypR = hypRasms[j]

        // 1a. Merged 2-reference-word Wasl/Idgham token (e.g. "إنالذين" -> "إِنَّ" + "ٱلَّذِينَ")
        if (cursor + 1 < totalWords) {
          val nextNorm = slice.normalizedWords[cursor + 1]
          val nextQari = slice.qariOnnxWords.getOrElse(cursor + 1) { nextNorm }
          if (TasmeeFollowAlongAlignment.matchesMergedTwoWords(
              hypW,
              targetNorm,
              targetQari,
              nextNorm,
              nextQari
            )
          ) {
            matchedHypIndex = j
            consumedHypSpan = 1
            unlockedRefWords = 2
            matchedAsMidWordPrefix = false
            break
          }
        }

        // 1b. Direct follow-along word match (yield fuzzy match if hypW is an exact match for an upcoming word)
        if (TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypW, targetNorm, targetQari)) {
          val isExactHere = TasmeeFollowAlongAlignment.matchesExactSkeleton(hypW, targetNorm, targetQari)
          val hasBetterUpcomingExact = !isExactHere &&
            ((cursor + 1)..min(totalWords - 1, cursor + 6)).any { aheadIdx ->
              val aheadNorm = slice.normalizedWords[aheadIdx]
              val aheadQari = slice.qariOnnxWords.getOrElse(aheadIdx) { aheadNorm }
              TasmeeFollowAlongAlignment.matchesExactSkeleton(hypW, aheadNorm, aheadQari)
            }
          if (!hasBetterUpcomingExact) {
            matchedHypIndex = j
            consumedHypSpan = 1
            unlockedRefWords = 1
            matchedAsMidWordPrefix = false
            break
          }
        }

        // 1c. Compound Uthmani token split across 2 ASR hypothesis words (e.g. "يَا أَيُّهَا" -> "يَٰٓأَيُّهَا")
        if (j + 1 < hypWords.size) {
          val joinedHyp = hypW + hypWords[j + 1]
          if (TasmeeFollowAlongAlignment.matchesFollowAlongWord(joinedHyp, targetNorm, targetQari)) {
            matchedHypIndex = j
            consumedHypSpan = 2
            unlockedRefWords = 1
            matchedAsMidWordPrefix = false
            break
          }
        }

        // 1d. Wasl carryover when cursor - 1 was unlocked earlier and ASR fused word[cursor - 1] + word[cursor]
        if (cursor > 0) {
          val prevNorm = slice.normalizedWords[cursor - 1]
          val prevQari = slice.qariOnnxWords.getOrElse(cursor - 1) { prevNorm }
          if (TasmeeFollowAlongAlignment.matchesWaslCarryover(
              hypW,
              prevNorm,
              prevQari,
              targetNorm,
              targetQari
            )
          ) {
            matchedHypIndex = j
            consumedHypSpan = 1
            unlockedRefWords = 1
            matchedAsMidWordPrefix = false
            break
          }
        }

        // 1e. Mid-word >= 3 Rasm-letter prefix on the very last decoded word
        if (j == hypWords.lastIndex &&
          hypR.length >= MIN_PREFIX_RASM_LETTERS &&
          (targetRasm.startsWith(hypR) || targetQariRasm.startsWith(hypR))
        ) {
          matchedHypIndex = j
          consumedHypSpan = 1
          unlockedRefWords = 1
          matchedAsMidWordPrefix = true
          break
        }
      }

      if (matchedHypIndex != -1) {
        cursor = (cursor + unlockedRefWords).coerceAtMost(totalWords)
        unlockedStepsThisHop += unlockedRefWords
        if (signalUsed == UnlockSignal.NONE) {
          signalUsed = if (matchedAsMidWordPrefix) {
            UnlockSignal.PHONETIC_RASM_PREFIX
          } else {
            UnlockSignal.PHONETIC_RASM_FULL
          }
        }
        unconsumedHypStart = matchedHypIndex + consumedHypSpan
        if (matchedAsMidWordPrefix) {
          nextMinSignalBFrame = max(nextMinSignalBFrame, (logprobs.size - 2).coerceAtLeast(0))
          break
        } else {
          nextMinSignalBFrame = max(nextMinSignalBFrame, (logprobs.size - 3).coerceAtLeast(0))
          continue
        }
      }

      // --- Signal B: BPE Token Posterior Spike (Instant Sub-Word Trigger > 0.55) ---
      if (unlockedStepsThisHop == 0) {
        val spikePeakFrame = checkSignalBPosteriorSpike(
          slice = slice,
          wordIndex = cursor,
          logprobs = logprobs,
          minFrame = nextMinSignalBFrame,
          threshold = SIGNAL_B_POSTERIOR_THRESHOLD
        )
        if (spikePeakFrame >= 0) {
          cursor += 1
          unlockedStepsThisHop += 1
          signalUsed = UnlockSignal.BPE_POSTERIOR_SPIKE
          nextMinSignalBFrame = max(nextMinSignalBFrame, spikePeakFrame + 2)
          break
        }
      }

      // --- Multi-Tier Catch-Up / Follow-Along Recovery (up to +8 words ahead) ---
      val catchUpMatch = findFollowAlongCatchUpMatch(
        slice = slice,
        cursor = cursor,
        hypWords = hypWords,
        unconsumedHypStart = searchStart
      )
      if (catchUpMatch != null) {
        for (skippedIdx in cursor until catchUpMatch.skipEndExclusive) {
          newlySkipped.add(skippedIdx)
        }
        val delta = catchUpMatch.newCursor - cursor
        cursor = catchUpMatch.newCursor.coerceAtMost(totalWords)
        unlockedStepsThisHop += delta
        unconsumedHypStart = catchUpMatch.nextHypStart
        nextMinSignalBFrame = max(nextMinSignalBFrame, (logprobs.size - 3).coerceAtLeast(0))
        if (signalUsed == UnlockSignal.NONE) {
          signalUsed = UnlockSignal.SKIP_AHEAD_RECOVERY
        }
        continue
      }

      // --- Sub-Word BPE Posterior Lookahead (+1 word ahead when frontier was missed) ---
      if (unlockedStepsThisHop == 0 && cursor + 1 < totalWords) {
        val nextWordIdx = cursor + 1
        val nextRasm = slice.rasmWords.getOrElse(nextWordIdx) { "" }
        val curFirstTid = slice.wordTokenIds.getOrNull(cursor)?.firstOrNull() ?: -1
        val nextTokens = slice.wordTokenIds.getOrNull(nextWordIdx)
        if (nextRasm.length >= 4 &&
          nextTokens != null &&
          nextTokens.size >= 2 &&
          nextTokens[0] != curFirstTid
        ) {
          val nextSpikeFrame = checkSignalBPosteriorSpike(
            slice = slice,
            wordIndex = nextWordIdx,
            logprobs = logprobs,
            minFrame = nextMinSignalBFrame,
            threshold = LOOKAHEAD_SIGNAL_B_THRESHOLD
          )
          if (nextSpikeFrame >= 0) {
            newlySkipped.add(cursor)
            cursor += 2
            unlockedStepsThisHop += 2
            signalUsed = UnlockSignal.SKIP_AHEAD_RECOVERY
            nextMinSignalBFrame = max(nextMinSignalBFrame, nextSpikeFrame + 2)
            break
          }
        }
      }

      // Frontier word not yet spoken -> wait for next 350 ms hop
      break
    }

    return HopMatchResult(
      previousRevealedCount = clampedCurrent,
      newRevealedCount = cursor,
      skippedWordIndices = newlySkipped,
      primarySignal = signalUsed,
      decodedHypWords = hypWords,
      updatedMinSignalBFrame = nextMinSignalBFrame
    )
  }

  /**
   * Checks if the reciter has already finished the final `1..3` words of [currentSlice] (which the
   * ASR model may have missed at a breath/waqf boundary) and started reciting [nextSlice] on the
   * same Mushaf page. Returns the number of unlocked words (`>= 1`) in [nextSlice] if confirmed,
   * or `0` otherwise.
   */
  fun evaluateCrossAyahContinuation(
    currentSlice: PageAyahTasmeeSlice,
    currentRevealedCount: Int,
    nextSlice: PageAyahTasmeeSlice,
    decodedHypWords: List<String>
  ): Int {
    val remainingInCurrent = currentSlice.wordCount - currentRevealedCount
    if (remainingInCurrent !in 1..3 || nextSlice.wordCount <= 0 || decodedHypWords.isEmpty()) {
      return 0
    }

    val hypRasms = decodedHypWords.map { QuranRecitationGrader.phoneticRasm(it) }
    val unconsumedStart = computeUnconsumedHypStartIndex(
      slice = currentSlice,
      revealedCount = currentRevealedCount,
      hypWords = decodedHypWords,
      hypRasms = hypRasms
    )
    if (unconsumedStart >= decodedHypWords.size) return 0

    val remainingRasms = HashSet<String>(remainingInCurrent * 2)
    for (idx in currentRevealedCount until currentSlice.wordCount) {
      remainingRasms.add(currentSlice.rasmWords[idx])
      remainingRasms.add(currentSlice.qariRasmWords.getOrElse(idx) { currentSlice.rasmWords[idx] })
    }

    val next0Norm = nextSlice.normalizedWords[0]
    val next0Qari = nextSlice.qariOnnxWords.getOrElse(0) { next0Norm }
    val next0Rasm = nextSlice.rasmWords[0]

    for (j in unconsumedStart until decodedHypWords.size) {
      val hypW = decodedHypWords[j]
      // 1. Two-word opening phrase of nextSlice (strongest confirmation)
      if (nextSlice.wordCount >= 2) {
        val next1Norm = nextSlice.normalizedWords[1]
        val next1Qari = nextSlice.qariOnnxWords.getOrElse(1) { next1Norm }
        if (j + 1 < decodedHypWords.size &&
          TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypW, next0Norm, next0Qari) &&
          TasmeeFollowAlongAlignment.matchesFollowAlongWord(decodedHypWords[j + 1], next1Norm, next1Qari)
        ) {
          return countGreedyContinuationInNextSlice(nextSlice, decodedHypWords, j, initialCount = 2)
        }
        if (TasmeeFollowAlongAlignment.matchesMergedTwoWords(hypW, next0Norm, next0Qari, next1Norm, next1Qari)) {
          return 2
        }
      }

      // 2. Distinctive single opening word of nextSlice (>= 4 Rasm letters, not in remaining tail of currentSlice)
      if (remainingInCurrent <= 2 &&
        next0Rasm.length >= 4 &&
        next0Rasm !in remainingRasms &&
        TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypW, next0Norm, next0Qari)
      ) {
        return countGreedyContinuationInNextSlice(nextSlice, decodedHypWords, j, initialCount = 1)
      }
    }

    return 0
  }

  private fun countGreedyContinuationInNextSlice(
    nextSlice: PageAyahTasmeeSlice,
    hypWords: List<String>,
    matchedStartJ: Int,
    initialCount: Int
  ): Int {
    var unlocked = initialCount.coerceAtMost(nextSlice.wordCount)
    var hIdx = matchedStartJ + initialCount
    while (unlocked < nextSlice.wordCount && hIdx < hypWords.size) {
      val norm = nextSlice.normalizedWords[unlocked]
      val qari = nextSlice.qariOnnxWords.getOrElse(unlocked) { norm }
      if (TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[hIdx], norm, qari)) {
        unlocked++
        hIdx++
      } else {
        break
      }
    }
    return unlocked
  }

  private data class CatchUpMatch(
    val skipEndExclusive: Int,
    val newCursor: Int,
    val nextHypStart: Int
  )

  private fun findFollowAlongCatchUpMatch(
    slice: PageAyahTasmeeSlice,
    cursor: Int,
    hypWords: List<String>,
    unconsumedHypStart: Int
  ): CatchUpMatch? {
    if (unconsumedHypStart >= hypWords.size) return null
    val totalWords = slice.wordCount
    val maxOffset = min(MAX_PHRASE_LOOKAHEAD_OFFSET, totalWords - 1 - cursor)
    if (maxOffset < 1) return null

    val frontierNorm = slice.normalizedWords[cursor]
    val frontierQari = slice.qariOnnxWords.getOrElse(cursor) { frontierNorm }

    // Pass 1: Near lookahead (+1 and +2) — check smallest offset first so we never skip further than needed
    val nearMax = min(2, maxOffset)
    for (offset in 1..nearMax) {
      val lookIdx = cursor + offset
      val lookNorm = slice.normalizedWords[lookIdx]
      val lookQari = slice.qariOnnxWords.getOrElse(lookIdx) { lookNorm }

      // 1a. 2-word phrase at lookIdx..lookIdx+1
      if (lookIdx + 1 < totalWords) {
        val look2Norm = slice.normalizedWords[lookIdx + 1]
        val look2Qari = slice.qariOnnxWords.getOrElse(lookIdx + 1) { look2Norm }
        for (j in unconsumedHypStart until hypWords.size) {
          if (j + 1 < hypWords.size &&
            TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[j], lookNorm, lookQari) &&
            TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[j + 1], look2Norm, look2Qari)
          ) {
            return CatchUpMatch(
              skipEndExclusive = lookIdx,
              newCursor = lookIdx + 2,
              nextHypStart = j + 2
            )
          }
          if (TasmeeFollowAlongAlignment.matchesMergedTwoWords(
              hypWords[j],
              lookNorm,
              lookQari,
              look2Norm,
              look2Qari
            )
          ) {
            return CatchUpMatch(
              skipEndExclusive = lookIdx,
              newCursor = lookIdx + 2,
              nextHypStart = j + 1
            )
          }
        }
      }

      // 1b. Single-word near anchor at lookIdx
      if (TasmeeFollowAlongAlignment.canServeAsNearCatchUpAnchor(
          frontierNorm = frontierNorm,
          frontierQari = frontierQari,
          lookNorm = lookNorm,
          lookQari = lookQari
        )
      ) {
        for (j in unconsumedHypStart until hypWords.size) {
          if (TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[j], lookNorm, lookQari)) {
            return CatchUpMatch(
              skipEndExclusive = lookIdx,
              newCursor = lookIdx + 1,
              nextHypStart = j + 1
            )
          }
          if (j + 1 < hypWords.size &&
            TasmeeFollowAlongAlignment.matchesFollowAlongWord(
              hypWords[j] + hypWords[j + 1],
              lookNorm,
              lookQari
            )
          ) {
            return CatchUpMatch(
              skipEndExclusive = lookIdx,
              newCursor = lookIdx + 1,
              nextHypStart = j + 2
            )
          }
        }
      }
    }

    // Pass 2: Far catch-up (+3..+8) via 2-word phrase anchor OR (+3..+6) via distinctive single word
    if (maxOffset >= 3) {
      val intermediateRasms = HashSet<String>(maxOffset * 2)
      intermediateRasms.add(slice.rasmWords[cursor])
      intermediateRasms.add(slice.qariRasmWords.getOrElse(cursor) { slice.rasmWords[cursor] })
      for (idx in (cursor + 1)..min(cursor + 2, totalWords - 1)) {
        intermediateRasms.add(slice.rasmWords[idx])
        intermediateRasms.add(slice.qariRasmWords.getOrElse(idx) { slice.rasmWords[idx] })
      }

      for (offset in 3..maxOffset) {
        val lookIdx = cursor + offset
        val lookNorm = slice.normalizedWords[lookIdx]
        val lookQari = slice.qariOnnxWords.getOrElse(lookIdx) { lookNorm }

        // 2a. 2-word phrase anchor (+3..+8)
        if (lookIdx + 1 < totalWords) {
          val look2Norm = slice.normalizedWords[lookIdx + 1]
          val look2Qari = slice.qariOnnxWords.getOrElse(lookIdx + 1) { look2Norm }
          // Ensure at least one of the two words has >= 2 Rasm or >= 3 lexical letters
          val phraseSubstantial = max(
            TasmeeFollowAlongAlignment.lexicalSkeleton(lookNorm).length,
            TasmeeFollowAlongAlignment.lexicalSkeleton(look2Norm).length
          ) >= 3 || max(slice.rasmWords[lookIdx].length, slice.rasmWords[lookIdx + 1].length) >= 2
          if (phraseSubstantial) {
            for (j in unconsumedHypStart until hypWords.size) {
              if (j + 1 < hypWords.size &&
                TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[j], lookNorm, lookQari) &&
                TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[j + 1], look2Norm, look2Qari)
              ) {
                return CatchUpMatch(
                  skipEndExclusive = lookIdx,
                  newCursor = lookIdx + 2,
                  nextHypStart = j + 2
                )
              }
              if (TasmeeFollowAlongAlignment.matchesMergedTwoWords(
                  hypWords[j],
                  lookNorm,
                  lookQari,
                  look2Norm,
                  look2Qari
                )
              ) {
                return CatchUpMatch(
                  skipEndExclusive = lookIdx,
                  newCursor = lookIdx + 2,
                  nextHypStart = j + 1
                )
              }
            }
          }
        }

        // 2b. Distinctive single-word anchor (+3..+6)
        if (offset <= MAX_FAR_SINGLE_LOOKAHEAD_OFFSET &&
          TasmeeFollowAlongAlignment.canServeAsFarSingleWordAnchor(
            lookNorm = lookNorm,
            lookQari = lookQari,
            intermediateRasms = intermediateRasms
          )
        ) {
          for (j in unconsumedHypStart until hypWords.size) {
            if (TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[j], lookNorm, lookQari)) {
              return CatchUpMatch(
                skipEndExclusive = lookIdx,
                newCursor = lookIdx + 1,
                nextHypStart = j + 1
              )
            }
          }
        }

        intermediateRasms.add(slice.rasmWords[lookIdx])
        intermediateRasms.add(slice.qariRasmWords.getOrElse(lookIdx) { slice.rasmWords[lookIdx] })
      }
    }

    return null
  }

  /**
   * Checks **Signal B**: if the mean peak probability `exp(logprobs[t, tid])` of the first `2`
   * BPE tokens of `targetWord` in the tail frames exceeds [threshold], returns the peak frame index
   * (`>= 0`); otherwise returns `-1`.
   */
  private fun checkSignalBPosteriorSpike(
    slice: PageAyahTasmeeSlice,
    wordIndex: Int,
    logprobs: Array<FloatArray>,
    minFrame: Int,
    threshold: Float
  ): Int {
    val wordTokens = slice.wordTokenIds.getOrNull(wordIndex) ?: return -1
    if (wordTokens.isEmpty()) return -1

    val targetRasm = slice.rasmWords.getOrElse(wordIndex) { "" }
    if (wordTokens.size == 1 && targetRasm.length < 3) {
      return -1
    }

    val numFrames = logprobs.size
    val tailStart = max(minFrame, numFrames - SIGNAL_B_TAIL_FRAMES).coerceAtLeast(0)
    if (tailStart >= numFrames) return -1

    val checkCount = min(2, wordTokens.size)
    val tid0 = wordTokens[0]

    var bestP0 = 0f
    var bestF0 = tailStart
    for (t in tailStart until numFrames) {
      val frame = logprobs[t]
      if (tid0 !in frame.indices) continue
      val p = exp(frame[tid0].coerceIn(-30f, 0f))
      if (p > bestP0) {
        bestP0 = p
        bestF0 = t
      }
    }

    if (checkCount == 1) {
      return if (bestP0 > threshold) bestF0 else -1
    }

    val tid1 = wordTokens[1]
    val secondStart = if (tid1 == tid0) min(numFrames - 1, bestF0 + 1) else bestF0
    if (secondStart >= numFrames) return -1

    var bestP1 = 0f
    var bestF1 = secondStart
    for (t in secondStart until numFrames) {
      val frame = logprobs[t]
      if (tid1 !in frame.indices) continue
      val p = exp(frame[tid1].coerceIn(-30f, 0f))
      if (p > bestP1) {
        bestP1 = p
        bestF1 = t
      }
    }

    val meanPeakProb = 0.5f * (bestP0 + bestP1)
    return if (bestP0 >= 0.25f && bestP1 >= 0.25f && meanPeakProb > threshold) {
      bestF1
    } else {
      -1
    }
  }

  /**
   * Finds how many leading hypothesis words in [hypWords] have already been consumed by
   * `slice[0 until revealedCount]`. Uses monotonic subsequence alignment so earlier words that
   * were unlocked via catch-up or Wasl merging never prevent later revealed words in the rolling
   * window from being properly marked as consumed.
   */
  internal fun computeUnconsumedHypStartIndex(
    slice: PageAyahTasmeeSlice,
    revealedCount: Int,
    hypWords: List<String>,
    hypRasms: List<String>
  ): Int {
    if (hypWords.isEmpty()) return 0

    var hCursor = 0
    // Skip leading Basmalah tokens if the reciter said Basmalah before Ayah 1 of a Surah (sura != 1)
    if (slice.suraAyah.sura != 1 && slice.suraAyah.ayah == 1) {
      val firstTargetRasm = slice.rasmWords.firstOrNull().orEmpty()
      while (hCursor < min(4, hypRasms.size) &&
        hypRasms[hCursor] in TasmeeFollowAlongAlignment.BASMALAH_RASMS &&
        hypRasms[hCursor] != firstTargetRasm
      ) {
        hCursor++
      }
    }

    if (revealedCount <= 0) return hCursor.coerceIn(0, hypWords.size)

    val refWindowStart = max(0, revealedCount - (hypWords.size + 4))
    var rIdx = refWindowStart

    while (rIdx < revealedCount && hCursor < hypWords.size) {
      val refNorm = slice.normalizedWords[rIdx]
      val refQari = slice.qariOnnxWords.getOrElse(rIdx) { refNorm }
      val refRasm = slice.rasmWords[rIdx]

      val searchLimit = min(hypWords.size, hCursor + 3)
      var foundAt = -1
      var consumedRefWords = 1
      var consumedHypWords = 1

      for (hIdx in hCursor until searchLimit) {
        if (rIdx + 1 < revealedCount) {
          val nextNorm = slice.normalizedWords[rIdx + 1]
          val nextQari = slice.qariOnnxWords.getOrElse(rIdx + 1) { nextNorm }
          if (TasmeeFollowAlongAlignment.matchesMergedTwoWords(
              hypWords[hIdx],
              refNorm,
              refQari,
              nextNorm,
              nextQari
            )
          ) {
            foundAt = hIdx
            consumedRefWords = 2
            consumedHypWords = 1
            break
          }
        }
        if (TasmeeFollowAlongAlignment.matchesFollowAlongWord(hypWords[hIdx], refNorm, refQari) ||
          (hypRasms[hIdx].length >= MIN_PREFIX_RASM_LETTERS && refRasm.startsWith(hypRasms[hIdx]))
        ) {
          foundAt = hIdx
          consumedRefWords = 1
          consumedHypWords = 1
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
          consumedRefWords = 1
          consumedHypWords = 2
          break
        }
      }

      if (foundAt != -1) {
        hCursor = foundAt + consumedHypWords
        rIdx += consumedRefWords
      } else {
        rIdx += 1
      }
    }

    return hCursor.coerceIn(0, hypWords.size)
  }
}
