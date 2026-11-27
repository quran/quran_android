package com.quran.labs.androidquran.memorization.tasmee

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.kidsclassroom.FastConformerAsrEngine
import com.quran.labs.androidquran.kidsclassroom.QuranRecitationGrader
import com.quran.labs.androidquran.memorization.QuranMemorizationManager
import com.quran.labs.androidquran.memorization.RevealMode
import org.junit.Test
import kotlin.math.ln

class TasmeeMonotonicMatcherTest {

  private fun buildSlice(
    sura: Int,
    ayah: Int,
    words: List<String>,
    wordTokenIds: List<IntArray> = List(words.size) { IntArray(0) }
  ): PageAyahTasmeeSlice {
    val rasms = words.map { QuranRecitationGrader.phoneticRasm(it) }
    return PageAyahTasmeeSlice(
      page = 1,
      suraAyah = SuraAyah(sura, ayah),
      uthmaniWords = words,
      normalizedWords = words,
      qariOnnxWords = words,
      rasmWords = rasms,
      qariRasmWords = rasms,
      wordTokenIds = wordTokenIds,
      glyphPositionsByWordIdx = List(words.size) { idx -> listOf(idx + 1) }
    )
  }

  private fun makeLogprobsForGreedyTokens(
    vocabSize: Int = FastConformerAsrEngine.VOCAB_SIZE,
    frameTokenIds: IntArray
  ): Array<FloatArray> {
    return Array(frameTokenIds.size) { t ->
      val row = FloatArray(vocabSize) { -20f }
      row[frameTokenIds[t]] = -0.02f
      row
    }
  }

  @Test
  fun `Signal A unlocks on full Rasm match and mid-word 3-letter Rasm prefix`() {
    val vocab = Array(FastConformerAsrEngine.VOCAB_SIZE) { "" }
    vocab[10] = "\u2581اهْدِنَا"
    vocab[11] = "\u2581الصِّرَاطَ"
    vocab[12] = "\u2581المست" // mid-word prefix ("لمست", 4 Rasm letters) of المستقيم ("لمستقيم")

    val slice = buildSlice(
      sura = 1,
      ayah = 6,
      words = listOf("اهْدِنَا", "الصِّرَاطَ", "الْمُسْتَقِيمَ")
    )

    val blank = FastConformerAsrEngine.BLANK_ID
    val logprobs = makeLogprobsForGreedyTokens(
      frameTokenIds = intArrayOf(10, blank, 11, blank, 12, blank)
    )

    val res = TasmeeMonotonicMatcher.evaluateHop(
      slice = slice,
      currentRevealedCount = 0,
      logprobs = logprobs,
      vocab = vocab
    )

    assertThat(res.didAdvance).isTrue()
    assertThat(res.newRevealedCount).isEqualTo(3)
    assertThat(res.skippedWordIndices).isEmpty()
  }

  @Test
  fun `Signal B unlocks long word immediately when first 2 BPE token posteriors exceed 0_55`() {
    val vocab = Array(FastConformerAsrEngine.VOCAB_SIZE) { "" }
    vocab[20] = "\u2581يَ"
    vocab[21] = "تَ"
    vocab[22] = "سَاءَلُونَ"

    val slice = buildSlice(
      sura = 78,
      ayah = 1,
      words = listOf("عَمَّ", "يَتَسَاءَلُونَ"),
      wordTokenIds = listOf(intArrayOf(5), intArrayOf(20, 21, 22))
    )

    // Suppose word 0 ("عم") is already revealed (currentRevealedCount = 1), and user is mid-pronouncing
    // the start of "يتساءلون" where BPE tokens 20 & 21 spike to p = 0.72 without full word completion:
    val numFrames = 12
    val logprobs = Array(numFrames) {
      FloatArray(FastConformerAsrEngine.VOCAB_SIZE) { -20f }.also { row ->
        row[FastConformerAsrEngine.BLANK_ID] = -0.05f
      }
    }
    // Spike token 20 at frame 7 (p = 0.75) and token 21 at frame 9 (p = 0.70) -> mean = 0.725 > 0.55
    logprobs[7][FastConformerAsrEngine.BLANK_ID] = -2.0f
    logprobs[7][20] = ln(0.75f)
    logprobs[9][FastConformerAsrEngine.BLANK_ID] = -2.0f
    logprobs[9][21] = ln(0.70f)

    val res = TasmeeMonotonicMatcher.evaluateHop(
      slice = slice,
      currentRevealedCount = 1,
      logprobs = logprobs,
      vocab = vocab
    )

    assertThat(res.didAdvance).isTrue()
    assertThat(res.newRevealedCount).isEqualTo(2)
    assertThat(res.primarySignal).isEqualTo(TasmeeMonotonicMatcher.UnlockSignal.BPE_POSTERIOR_SPIKE)
  }

  @Test
  fun `Skip-ahead recovery unlocks word i and i+1 with hint dot when word i is skipped`() {
    val vocab = Array(FastConformerAsrEngine.VOCAB_SIZE) { "" }
    vocab[30] = "\u2581مَالِكِ"
    vocab[31] = "\u2581الدِّينِ" // user skipped "يَوْمِ" and recited "الدِّينِ" (Rasm "لدين", length 4 >= 3)

    val slice = buildSlice(
      sura = 1,
      ayah = 4,
      words = listOf("مَالِكِ", "يَوْمِ", "الدِّينِ")
    )

    val blank = FastConformerAsrEngine.BLANK_ID
    val logprobs = makeLogprobsForGreedyTokens(
      frameTokenIds = intArrayOf(30, blank, 31, blank)
    )

    val res = TasmeeMonotonicMatcher.evaluateHop(
      slice = slice,
      currentRevealedCount = 1, // "مَالِكِ" already revealed
      logprobs = logprobs,
      vocab = vocab
    )

    assertThat(res.didAdvance).isTrue()
    assertThat(res.newRevealedCount).isEqualTo(3)
    assertThat(res.skippedWordIndices).containsExactly(1) // "يَوْمِ" marked skipped for hint dot
    assertThat(res.primarySignal).isEqualTo(TasmeeMonotonicMatcher.UnlockSignal.SKIP_AHEAD_RECOVERY)
  }

  @Test
  fun `Repeated word in same Ayah does not prematurely unlock second occurrence from rolling window`() {
    val vocab = Array(FastConformerAsrEngine.VOCAB_SIZE) { "" }
    vocab[1] = "\u2581صِرَاطَ"
    vocab[2] = "\u2581الَّذِينَ"
    vocab[3] = "\u2581أَنْعَمْتَ"
    vocab[4] = "\u2581عَلَيْهِمْ"
    vocab[5] = "\u2581غَيْرِ"
    vocab[6] = "\u2581الْمَغْضُوبِ"

    val slice = buildSlice(
      sura = 1,
      ayah = 7,
      words = listOf(
        "صِرَاطَ", "الَّذِينَ", "أَنْعَمْتَ", "عَلَيْهِمْ",
        "غَيْرِ", "الْمَغْضُوبِ", "عَلَيْهِمْ", "وَلَا", "الضَّالِّينَ"
      )
    )

    val b = FastConformerAsrEngine.BLANK_ID
    // Rolling window has words 0..5 ("صراط الذين أنعمت عليهم غير المغضوب") — user hasn't spoken the 2nd "عليهم" yet!
    val logprobs = makeLogprobsForGreedyTokens(
      frameTokenIds = intArrayOf(1, b, 2, b, 3, b, 4, b, 5, b, 6, b)
    )

    val resBeforeSecond = TasmeeMonotonicMatcher.evaluateHop(
      slice = slice,
      currentRevealedCount = 6,
      logprobs = logprobs,
      vocab = vocab
    )
    assertThat(resBeforeSecond.didAdvance).isFalse()
    assertThat(resBeforeSecond.newRevealedCount).isEqualTo(6)

    // Now user pronounces the 2nd "عَلَيْهِمْ":
    val logprobsAfterSecond = makeLogprobsForGreedyTokens(
      frameTokenIds = intArrayOf(1, b, 2, b, 3, b, 4, b, 5, b, 6, b, 4, b)
    )
    val resAfterSecond = TasmeeMonotonicMatcher.evaluateHop(
      slice = slice,
      currentRevealedCount = 6,
      logprobs = logprobsAfterSecond,
      vocab = vocab
    )
    assertThat(resAfterSecond.didAdvance).isTrue()
    assertThat(resAfterSecond.newRevealedCount).isEqualTo(7)
  }

  @Test
  fun `QuranMemorizationManager One-Word Tasmee cursor latches monotonically and tracks skipped hints`() {
    val page = 604
    val ayah = SuraAyah(112, 1)
    QuranMemorizationManager.registerAyahWordCountFromText(page, ayah, 4)
    QuranMemorizationManager.startTasmeeOneWordSession(page = page)

    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isTrue()
    assertThat(QuranMemorizationManager.getRevealMode()).isEqualTo(RevealMode.SINGLE_WORD)

    // Unlock word 1, then skip word 2 and unlock word 3
    QuranMemorizationManager.advanceTasmeeMonotonicCursor(
      page = page,
      suraAyah = ayah,
      unlockedGlyphPositions = listOf(1)
    )
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 1)).isTrue()

    QuranMemorizationManager.advanceTasmeeMonotonicCursor(
      page = page,
      suraAyah = ayah,
      unlockedGlyphPositions = listOf(2, 3),
      skippedGlyphPositions = setOf(2)
    )
    // In SINGLE_WORD ("one word") mode, only word 3 is visible while word 2 has a skipped hint dot
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 1)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 2)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 3)).isTrue()
    assertThat(QuranMemorizationManager.isWordSkippedHint(page, ayah, 2)).isTrue()
    assertThat(QuranMemorizationManager.getRevealedWordPositionsForAyah(page, ayah))
      .containsExactly(1, 2, 3)

    QuranMemorizationManager.setHiddenModeEnabled(false, page)
  }

  @Test
  fun `QuranMemorizationManager startTasmeeSession defaults to WORD_BY_WORD cumulative reveal and supports switching modes`() {
    val page = 604
    val ayah = SuraAyah(112, 1)
    QuranMemorizationManager.setHiddenModeEnabled(false, page)
    QuranMemorizationManager.registerAyahWordCountFromText(page, ayah, 4)

    // Starting Tasmee' from unhidden Mushaf defaults to WORD_BY_WORD (not single-word only!)
    QuranMemorizationManager.startTasmeeSession(page = page)
    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isTrue()
    assertThat(QuranMemorizationManager.getRevealMode()).isEqualTo(RevealMode.WORD_BY_WORD)

    // Unlocking words 1, 2, 3 in WORD_BY_WORD keeps ALL of words 1, 2, 3 revealed cumulatively
    QuranMemorizationManager.advanceTasmeeMonotonicCursor(
      page = page,
      suraAyah = ayah,
      unlockedGlyphPositions = listOf(1, 2, 3)
    )
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 1)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 2)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 3)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 4)).isFalse()

    // Switching to SINGLE_WORD mid-session spotlights the latest revealed word (word 3)
    QuranMemorizationManager.setRevealMode(RevealMode.SINGLE_WORD)
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 1)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 2)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 3)).isTrue()

    // Switching back to WORD_BY_WORD restores cumulative visibility for words 1..3
    QuranMemorizationManager.setRevealMode(RevealMode.WORD_BY_WORD)
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 1)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 2)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, 3)).isTrue()

    // When hidden mode is ALREADY active in SINGLE_WORD, startTasmeeSession preserves SINGLE_WORD
    QuranMemorizationManager.setRevealMode(RevealMode.SINGLE_WORD)
    QuranMemorizationManager.startTasmeeSession(page = page)
    assertThat(QuranMemorizationManager.getRevealMode()).isEqualTo(RevealMode.SINGLE_WORD)

    QuranMemorizationManager.setHiddenModeEnabled(false, page)
  }

  @Test
  fun `evaluateHop skips leading Basmalah on Ayah 1 and unlocks fluent multi-word recitation`() {
    val vocab = Array(FastConformerAsrEngine.VOCAB_SIZE) { "" }
    vocab[1] = "\u2581بِسْمِ"
    vocab[2] = "\u2581اللَّهِ"
    vocab[3] = "\u2581الرَّحْمَنِ"
    vocab[4] = "\u2581الرَّحِيمِ"
    vocab[5] = "\u2581قُلْ"
    vocab[6] = "\u2581هُوَ"
    vocab[7] = "\u2581اللَّهُ"
    vocab[8] = "\u2581أَحَدٌ"

    val slice = buildSlice(
      sura = 112,
      ayah = 1,
      words = listOf("قُلْ", "هُوَ", "ٱللَّهُ", "أَحَدٌ")
    )

    val b = FastConformerAsrEngine.BLANK_ID
    val logprobs = makeLogprobsForGreedyTokens(
      frameTokenIds = intArrayOf(1, b, 2, b, 3, b, 4, b, 5, b, 6, b, 7, b, 8, b)
    )

    val res = TasmeeMonotonicMatcher.evaluateHop(
      slice = slice,
      currentRevealedCount = 0,
      logprobs = logprobs,
      vocab = vocab,
      blankId = b
    )

    assertThat(res.didAdvance).isTrue()
    assertThat(res.newRevealedCount).isEqualTo(4)
    assertThat(res.skippedWordIndices).isEmpty()
  }

  @Test
  fun `Follow-along catch-up unlocks words up to what ASR heard when immediate words are missed or Wasl-merged`() {
    val vocab = Array(FastConformerAsrEngine.VOCAB_SIZE) { "" }
    vocab[1] = "\u2581إنالذين" // Wasl-merged "إِنَّ" + "الَّذِينَ"
    vocab[2] = "\u2581سواء"    // 2-letter Rasm ("سو"), 4-letter lexical ("سواء") after missed "كفروا"
    vocab[3] = "\u2581عليهم"
    vocab[4] = "\u2581لم"
    vocab[5] = "\u2581تنذرهم"  // 2-word phrase catch-up after missing "أأنذرتهم أم"

    val ayah26 = buildSlice(
      sura = 2,
      ayah = 6,
      words = listOf(
        "إِنَّ", "الَّذِينَ", "كَفَرُوا", "سَوَاءٌ", "عَلَيْهِمْ",
        "أَأَنذَرْتَهُمْ", "أَمْ", "لَمْ", "تُنذِرْهُمْ", "لَا", "يُؤْمِنُونَ"
      )
    )

    val b = FastConformerAsrEngine.BLANK_ID

    // 1. Wasl-merged "إنالذين" + missed "كفروا" + heard "سواء عليهم" -> reveals all words up to "عليهم" (5/11)
    val hop1 = TasmeeMonotonicMatcher.evaluateHop(
      slice = ayah26,
      currentRevealedCount = 0,
      logprobs = makeLogprobsForGreedyTokens(frameTokenIds = intArrayOf(1, b, 2, b, 3, b)),
      vocab = vocab,
      blankId = b
    )
    assertThat(hop1.didAdvance).isTrue()
    assertThat(hop1.newRevealedCount).isEqualTo(5)

    // 2. Model misses "أأنذرتهم أم" (+2 missed words) and catches "لم تنذرهم" -> reveals up to "تنذرهم" (9/11)
    val hop2 = TasmeeMonotonicMatcher.evaluateHop(
      slice = ayah26,
      currentRevealedCount = 5,
      logprobs = makeLogprobsForGreedyTokens(frameTokenIds = intArrayOf(1, b, 2, b, 3, b, 4, b, 5, b)),
      vocab = vocab,
      blankId = b
    )
    assertThat(hop2.didAdvance).isTrue()
    assertThat(hop2.newRevealedCount).isEqualTo(9)
  }

  @Test
  fun `evaluateCrossAyahContinuation completes trailing missed word and advances into next Ayah`() {
    val ayah26 = buildSlice(
      sura = 2,
      ayah = 6,
      words = listOf(
        "إِنَّ", "الَّذِينَ", "كَفَرُوا", "سَوَاءٌ", "عَلَيْهِمْ",
        "أَأَنذَرْتَهُمْ", "أَمْ", "لَمْ", "تُنذِرْهُمْ", "لَا", "يُؤْمِنُونَ"
      )
    )
    val ayah27 = buildSlice(
      sura = 2,
      ayah = 7,
      words = listOf("خَتَمَ", "اللَّهُ", "عَلَى", "قُلُوبِهِمْ")
    )

    // Cursor at 9/11 (missing "لا يؤمنون"), reciter already speaking "ختم الله على" of Ayah 2:7
    val nextUnlocked = TasmeeMonotonicMatcher.evaluateCrossAyahContinuation(
      currentSlice = ayah26,
      currentRevealedCount = 9,
      nextSlice = ayah27,
      decodedHypWords = listOf("تنذرهم", "ختم", "الله", "على")
    )
    assertThat(nextUnlocked).isEqualTo(3)
  }
}

