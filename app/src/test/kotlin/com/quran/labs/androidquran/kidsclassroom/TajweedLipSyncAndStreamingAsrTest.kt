package com.quran.labs.androidquran.kidsclassroom

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class TajweedLipSyncAndStreamingAsrTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    RewardsDataStore.clearForTests(context)
  }

  @Test
  fun tajweedSpanAdapter_separatesGhunnahMeemNoonIkhfaIqlabMaddAndSilentRules() {
    // 1. Madd Lazim (6x Dark Red) + Lam Shamsiyyah SILENT (0.0x) in الضَّالِّينَ
    val fatihaEnd = TajweedSpanAdapter.analyzeText("وَلَا الضَّالِّينَ")
    assertThat(fatihaEnd.allTokens.any { it.ruleType == TajweedRuleType.MADD_6 && it.isMaddLetter }).isTrue()
    val shamsiyyahLam = fatihaEnd.allTokens.firstOrNull { it.wordIndex == 1 && it.baseChar == 'ل' }
    assertThat(shamsiyyahLam?.ruleType).isEqualTo(TajweedRuleType.SILENT)
    assertThat(shamsiyyahLam?.ruleType?.durationMultiplier).isEqualTo(0.0f)

    // 2. Separated Ghunnah rules:
    // - إِنَّ -> GHUNNAH_NOON (NASAL_SPARKLE_TONGUE)
    // - ثُمَّ -> GHUNNAH_MEEM (NASAL_SPARKLE_LIPS)
    // - مِن شَرِّ -> IKHFA (NASAL_SPARKLE_HOVER)
    // - مِنۢ بَعْدِ -> IQLAB (NASAL_SPARKLE_LIPS)
    val ghunnahAnalysis = TajweedSpanAdapter.analyzeText("إِنَّ ثُمَّ مِن شَرِّ مِنۢ بَعْدِ")
    assertThat(ghunnahAnalysis.allTokens.any {
      it.ruleType == TajweedRuleType.GHUNNAH_NOON && it.ruleType.effect == TajweedVisualEffect.NASAL_SPARKLE_TONGUE
    }).isTrue()
    assertThat(ghunnahAnalysis.allTokens.any {
      it.ruleType == TajweedRuleType.GHUNNAH_MEEM && it.ruleType.effect == TajweedVisualEffect.NASAL_SPARKLE_LIPS
    }).isTrue()
    assertThat(ghunnahAnalysis.allTokens.any {
      it.ruleType == TajweedRuleType.IKHFA && it.ruleType.effect == TajweedVisualEffect.NASAL_SPARKLE_HOVER
    }).isTrue()
    assertThat(ghunnahAnalysis.allTokens.any {
      it.ruleType == TajweedRuleType.IQLAB && it.ruleType.effect == TajweedVisualEffect.NASAL_SPARKLE_LIPS
    }).isTrue()

    // 3. Qalqalah (1.35x Blue) in قُلْ هُوَ اللَّهُ أَحَدٌ
    val ikhlas = TajweedSpanAdapter.analyzeText("قُلْ هُوَ اللَّهُ أَحَدٌ")
    val finalDal = ikhlas.allTokens.last()
    assertThat(finalDal.baseChar).isEqualTo('د')
    assertThat(finalDal.ruleType).isEqualTo(TajweedRuleType.QALQALAH)
    assertThat(finalDal.ruleType.durationMultiplier).isWithin(1e-4f).of(1.35f)
  }

  @Test
  fun tajweedSpanAdapter_preservesExplicitAnnotatedStringColorsAndRefinesGreenSubRules() {
    val darkRed = Color(0xFF8B0000)
    val green = Color(0xFF2E7D32)
    val grey = Color(0xFF9E9E9E)
    val annotated = buildAnnotatedString {
      withStyle(SpanStyle(color = grey)) { append("ٱ") }
      withStyle(SpanStyle(color = darkRed)) { append("آ") }
      withStyle(SpanStyle(color = green)) { append("نَّ ") }
      withStyle(SpanStyle(color = green)) { append("مَّ") }
    }
    val analysis = TajweedSpanAdapter.adaptAnnotatedString(annotated)
    assertThat(analysis.allTokens).hasSize(4)
    assertThat(analysis.allTokens[0].ruleType).isEqualTo(TajweedRuleType.SILENT)
    assertThat(analysis.allTokens[1].ruleType).isEqualTo(TajweedRuleType.MADD_6)
    assertThat(analysis.allTokens[1].tajweedColor).isEqualTo(darkRed)
    assertThat(analysis.allTokens[2].ruleType).isEqualTo(TajweedRuleType.GHUNNAH_NOON)
    assertThat(analysis.allTokens[2].tajweedColor).isEqualTo(green)
    assertThat(analysis.allTokens[3].ruleType).isEqualTo(TajweedRuleType.GHUNNAH_MEEM)
    assertThat(analysis.allTokens[3].tajweedColor).isEqualTo(green)
  }

  @Test
  fun tajweedOnlyArticulation_keepsMouthClosedForNormalLettersAndAnimatesExpandedRules() {
    // Ordinary NORMAL syllable (e.g., بَ or كِ) MUST stay strictly closed (REST_CLOSED, jawOpen == 0f)
    val normalTokens = listOf(
      TajweedPhonemeToken(baseChar = 'ب', harakah = '\u064E', isMaddLetter = false, ruleType = TajweedRuleType.NORMAL, wordIndex = 0),
      TajweedPhonemeToken(baseChar = 'ك', harakah = '\u0650', isMaddLetter = false, ruleType = TajweedRuleType.NORMAL, wordIndex = 0)
    )
    val normalFrames = TajweedVisemePlanner.planKeyframes(normalTokens, startMs = 0L, endMs = 1000L)
    assertThat(normalFrames).hasSize(2)
    assertThat(normalFrames.all { it.viseme == ArabicViseme.REST_CLOSED }).isTrue()

    val loudNormalSample = TajweedVisemePlanner.sampleAt(
      keyframes = normalFrames,
      positionMs = 250L,
      rmsEnvelope = 0.90f,
      silenceDurationMs = 0L
    )
    assertThat(loudNormalSample.viseme).isEqualTo(ArabicViseme.REST_CLOSED)
    assertThat(loudNormalSample.geometry.jawOpen).isEqualTo(0.0f)
    assertThat(loudNormalSample.geometry.tonguePose).isEqualTo(TonguePose.HIDDEN)
    assertThat(loudNormalSample.mouthOpenness).isEqualTo(0.0f)
    assertThat(loudNormalSample.harakahHint).isEmpty()

    // Separated green nasal rules + Tafkheem articulate their exact distinct mouth/tongue/cheek geometries
    val tajweedTokens = listOf(
      TajweedPhonemeToken(baseChar = 'ن', hasShaddah = true, ruleType = TajweedRuleType.GHUNNAH_NOON, wordIndex = 0),
      TajweedPhonemeToken(baseChar = 'ن', harakah = '\u0652', ruleType = TajweedRuleType.IKHFA, wordIndex = 1),
      TajweedPhonemeToken(baseChar = 'ن', harakah = '\u0652', ruleType = TajweedRuleType.IQLAB, wordIndex = 2),
      TajweedPhonemeToken(baseChar = 'ط', harakah = '\u064E', ruleType = TajweedRuleType.TAFKHEEM, wordIndex = 3)
    )
    val tajweedFrames = TajweedVisemePlanner.planKeyframes(tajweedTokens, startMs = 0L, endMs = 4000L)

    val ghunnahNoonFrame = tajweedFrames.first { it.ruleType == TajweedRuleType.GHUNNAH_NOON }
    val ghunnahNoonSample = TajweedVisemePlanner.sampleAt(tajweedFrames, ghunnahNoonFrame.startMs + 50L, 0.85f, 0L)
    assertThat(ghunnahNoonSample.viseme).isEqualTo(ArabicViseme.TONGUE_ALVEOLAR_UP)
    assertThat(ghunnahNoonSample.geometry.tonguePose).isEqualTo(TonguePose.TIP_UP_ALVEOLAR)
    assertThat(ghunnahNoonSample.geometry.tongueLift).isGreaterThan(0.90f)

    val ikhfaFrame = tajweedFrames.first { it.ruleType == TajweedRuleType.IKHFA }
    val ikhfaSample = TajweedVisemePlanner.sampleAt(tajweedFrames, ikhfaFrame.startMs + 50L, 0.85f, 0L)
    assertThat(ikhfaSample.viseme).isEqualTo(ArabicViseme.TONGUE_IKHFA_HOVER)
    assertThat(ikhfaSample.geometry.tonguePose).isEqualTo(TonguePose.HOVER_MID)

    val iqlabFrame = tajweedFrames.first { it.ruleType == TajweedRuleType.IQLAB }
    val iqlabSample = TajweedVisemePlanner.sampleAt(tajweedFrames, iqlabFrame.startMs + 50L, 0.85f, 0L)
    assertThat(iqlabSample.viseme).isEqualTo(ArabicViseme.LIPS_MEEM_HOLD)
    assertThat(iqlabSample.geometry.jawOpen).isEqualTo(0.0f)
    assertThat(iqlabSample.geometry.lipPress).isWithin(0.02f).of(0.58f)

    val tafkheemFrame = tajweedFrames.first { it.ruleType == TajweedRuleType.TAFKHEEM }
    val tafkheemSample = TajweedVisemePlanner.sampleAt(tajweedFrames, tafkheemFrame.startMs + 20L, 0.85f, 0L)
    assertThat(tafkheemSample.geometry.cheekFullness).isAtLeast(0.58f)
  }

  @Test
  fun tajweedVisemePlanner_coArticulatesMaddPreservesClosedLipsAndGatesSilenceAfter80Ms() {
    val tokens = listOf(
      TajweedPhonemeToken(baseChar = 'ٱ', ruleType = TajweedRuleType.SILENT, wordIndex = 0),
      TajweedPhonemeToken(baseChar = 'ب', harakah = '\u064E', ruleType = TajweedRuleType.NORMAL, wordIndex = 0),
      TajweedPhonemeToken(baseChar = 'ا', isMaddLetter = true, ruleType = TajweedRuleType.MADD_6, wordIndex = 0),
      TajweedPhonemeToken(baseChar = 'م', harakah = '\u064E', hasShaddah = true, ruleType = TajweedRuleType.GHUNNAH_MEEM, wordIndex = 0),
      TajweedPhonemeToken(baseChar = 'د', harakah = '\u0652', ruleType = TajweedRuleType.QALQALAH, wordIndex = 0)
    )
    val keyframes = TajweedVisemePlanner.planKeyframes(tokens, startMs = 1000L, endMs = 5000L)

    // Silent token must NEVER emit a keyframe
    assertThat(keyframes.none { it.ruleType == TajweedRuleType.SILENT }).isTrue()

    // بَا: NORMAL consonant onset stays REST_CLOSED (~18%), then MADD_ALIF holds (~82%)
    val baOnset = keyframes.first { it.consonantChar == 'ب' }
    val maddFrame = keyframes.first { it.ruleType == TajweedRuleType.MADD_6 && it.viseme == ArabicViseme.MADD_ALIF }
    assertThat(baOnset.viseme).isEqualTo(ArabicViseme.REST_CLOSED)
    assertThat(maddFrame.durationMs).isGreaterThan(baOnset.durationMs * 4)

    // Closed-lip Ghunnah Meem MUST stay at jawOpen == 0.0f even under loud RMS = 0.95f!
    val meemFrame = keyframes.first { it.ruleType == TajweedRuleType.GHUNNAH_MEEM }
    val loudMeemSample = TajweedVisemePlanner.sampleAt(
      keyframes = keyframes,
      positionMs = meemFrame.startMs + 40L,
      rmsEnvelope = 0.95f,
      silenceDurationMs = 0L
    )
    assertThat(loudMeemSample.viseme).isEqualTo(ArabicViseme.LIPS_MEEM_HOLD)
    assertThat(loudMeemSample.geometry.jawOpen).isEqualTo(0.0f)
    assertThat(loudMeemSample.geometry.lipPress).isWithin(0.02f).of(0.70f)
    assertThat(loudMeemSample.isVoiceActive).isTrue()

    // Qalqalah must emit a controlled middle-open bounce (never a wide yawn)
    val bounceFrame = keyframes.first { it.isQalqalahBounce }
    val bounceSample = TajweedVisemePlanner.sampleAt(
      keyframes = keyframes,
      positionMs = bounceFrame.startMs + bounceFrame.durationMs / 2,
      rmsEnvelope = 0.80f,
      silenceDurationMs = 0L
    )
    assertThat(bounceSample.geometry.jawOpen).isAtLeast(0.15f)
    assertThat(bounceSample.geometry.jawOpen).isAtMost(0.35f)

    // RMS < 0.06f for >= 80 ms must snap cleanly to REST_CLOSED
    val silentSample = TajweedVisemePlanner.sampleAt(
      keyframes = keyframes,
      positionMs = maddFrame.startMs + 50L,
      rmsEnvelope = 0.02f,
      silenceDurationMs = 95L
    )
    assertThat(silentSample.viseme).isEqualTo(ArabicViseme.REST_CLOSED)
    assertThat(silentSample.mouthOpenness).isEqualTo(0f)

    // Active speech RMS > 0.06f opens MADD_ALIF cleanly around ~0.73f..0.82f with positive maddProgress
    val activeSample = TajweedVisemePlanner.sampleAt(
      keyframes = keyframes,
      positionMs = maddFrame.startMs + 50L,
      rmsEnvelope = 0.80f,
      silenceDurationMs = 0L
    )
    assertThat(activeSample.viseme).isEqualTo(ArabicViseme.MADD_ALIF)
    assertThat(activeSample.mouthOpenness).isGreaterThan(0.68f)
    assertThat(activeSample.maddProgress).isGreaterThan(0f)
  }

  @Test
  fun melFeatureExtractor_loadsBinaryFilterbankAndRunsCausalWelfordWithN0_50() {
    val tempFile = File(context.cacheDir, "test_mel_basis_80x257.bin")
    val bb = ByteBuffer.allocate(80 * 257 * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (m in 0 until 80) {
      for (k in 0 until 257) {
        bb.putFloat(if (k == m + 1) 1.0f else 0.0f)
      }
    }
    tempFile.writeBytes(bb.array())

    val loaded = MelFeatureExtractor.loadMelBasisBinary(tempFile)
    assertThat(loaded).isNotNull()
    assertThat(loaded!![5][6]).isWithin(1e-6f).of(1.0f)
    assertThat(loaded[5][7]).isWithin(1e-6f).of(0.0f)

    val normalizer = CausalWelfordOnlineNormalizer(nMels = 80, priorCount = 50)
    assertThat(normalizer.currentCount(0)).isWithin(1e-6).of(50.0)
    val frame = FloatArray(80) { -5.0f }
    normalizer.normalizeFrameInPlace(frame)
    assertThat(normalizer.currentCount(0)).isWithin(1e-6).of(51.0)
    assertThat(frame[0].isFinite()).isTrue()

    val cfg = StreamingAsrConfig.parseJson(
      """{"chunk_ms":560,"blank_id":1024,"welford_n0":50,"cache_last_channel_shape":[1,17,70,512],"cache_last_time_shape":[1,17,512,8]}"""
    )
    assertThat(cfg.chunkMs).isEqualTo(560)
    assertThat(cfg.blankId).isEqualTo(1024)
    assertThat(cfg.welfordPriorCount).isEqualTo(50)
    assertThat(cfg.cacheChannelShape.toList()).containsExactly(1L, 17L, 70L, 512L).inOrder()

    // Verify nested HuggingFace streaming_config.json schema (frontend + cache_shapes + welford_prior_var)
    val means80 = (0 until 80).joinToString(",") { (-10.0 + it * 0.05).toString() }
    val vars80 = (0 until 80).joinToString(",") { "9.0" }
    val hfCfg = StreamingAsrConfig.parseJson(
      """
      {
        "frontend": {
          "sample_rate": 16000,
          "n_mels": 80,
          "welford_prior_count": 50,
          "welford_prior_mean": [$means80],
          "welford_prior_var": [$vars80]
        },
        "cache_shapes": {
          "cache_last_channel": [1, 17, 70, 512],
          "cache_last_time": [1, 17, 512, 8],
          "pre_encode_mel_cache_frames": 9,
          "drop_extra_pre_encoded": 2
        },
        "blank_id": 1024
      }
      """.trimIndent()
    )
    assertThat(hfCfg.welfordPriorCount).isEqualTo(50)
    assertThat(hfCfg.welfordPriorMean).isNotNull()
    assertThat(hfCfg.welfordPriorMean!![0]).isWithin(1e-4f).of(-10.0f)
    assertThat(hfCfg.welfordPriorStd).isNotNull()
    assertThat(hfCfg.welfordPriorStd!![0]).isWithin(1e-4f).of(3.0f) // sqrt(9.0)
    assertThat(hfCfg.preEncodeMelCacheFrames).isEqualTo(9)
    assertThat(hfCfg.dropExtraPreEncoded).isEqualTo(2)

    // Verify StreamingQuranAsrEngine parses piece->id vocab.json format
    val vocabFile = File(context.cacheDir, "test_streaming_vocab.json")
    vocabFile.writeText("""{"<unk>":0,"▁بِسْمِ":292,"<blk>":1024}""")
    val parsedVocab = StreamingQuranAsrEngine().parseVocab(vocabFile)
    assertThat(parsedVocab[292]).isEqualTo("▁بِسْمِ")
    assertThat(parsedVocab[1024]).isEqualTo("<blk>")
    vocabFile.delete()

    // Verify 9-frame pre-encode mel cache yields 65-frame window on 560ms (8960-sample) chunks
    val extractor = MelFeatureExtractor()
    val welford = CausalWelfordOnlineNormalizer(
      nMels = 80,
      priorCount = hfCfg.welfordPriorCount,
      priorMean = hfCfg.welfordPriorMean,
      priorStd = hfCfg.welfordPriorStd
    )
    val chunk560ms = ShortArray(8960) { i -> (kotlin.math.sin(2.0 * kotlin.math.PI * 300.0 * i / 16000.0) * 6000.0).toInt().toShort() }
    val (feat1, total1, new1) = extractor.extractCausalNormalizedWindowWithPreEncodeCache(chunk560ms, welford, 9)
    assertThat(total1).isEqualTo(new1 + 9)
    assertThat(feat1.size).isEqualTo(80 * total1)
    val (feat2, total2, new2) = extractor.extractCausalNormalizedWindowWithPreEncodeCache(chunk560ms, welford, 9)
    assertThat(new2).isEqualTo(56)
    assertThat(total2).isEqualTo(65)
    assertThat(feat2.size).isEqualTo(80 * 65)

    tempFile.delete()
  }

  @Test
  fun asrModelManager_parsesSha256SumsAndInvalidatesLegacyModelMismatch() {
    val fakeModel = File(context.cacheDir, "legacy_fastconformer.onnx")
    fakeModel.writeText("legacy-onnx-bytes-0001")
    val actualHash = AsrModelManager.computeSha256(fakeModel)

    val matchingManifest = AsrModelManager.parseSha256Sums("$actualHash  fastconformer_quran_int8.onnx\n")
    assertThat(matchingManifest["fastconformer_quran_int8.onnx"]).isEqualTo(actualHash)
    val okMatch = AsrModelManager.verifyOrInvalidateLegacyFile(
      targetFile = fakeModel,
      minExpectedBytes = 5L,
      sha256Map = matchingManifest,
      entryName = "fastconformer_quran_int8.onnx"
    )
    assertThat(okMatch).isTrue()
    assertThat(fakeModel.exists()).isTrue()

    val stage4Hash = "a".repeat(64)
    val newManifest = AsrModelManager.parseSha256Sums("$stage4Hash  fastconformer_quran_int8.onnx\n")
    val okLegacy = AsrModelManager.verifyOrInvalidateLegacyFile(
      targetFile = fakeModel,
      minExpectedBytes = 5L,
      sha256Map = newManifest,
      entryName = "fastconformer_quran_int8.onnx"
    )
    assertThat(okLegacy).isFalse()
    assertThat(fakeModel.exists()).isFalse()
  }

  @Test
  fun rewardsDataStore_tracksBestStarsStreakAndBadges() {
    val delta1 = RewardsDataStore.recordAyahStars(context, surah = 112, ayah = 1, starsEarned = 2, todayStamp = "2026-11-17")
    assertThat(delta1).isEqualTo(2)
    assertThat(RewardsDataStore.state.value.totalStars).isEqualTo(2)
    assertThat(RewardsDataStore.state.value.streakDays).isEqualTo(1)

    val delta2 = RewardsDataStore.recordAyahStars(context, surah = 112, ayah = 1, starsEarned = 3, todayStamp = "2026-11-18")
    assertThat(delta2).isEqualTo(1)
    assertThat(RewardsDataStore.state.value.totalStars).isEqualTo(3)
    assertThat(RewardsDataStore.state.value.streakDays).isEqualTo(2)
    assertThat(RewardsDataStore.state.value.unlockedBadges.any { it.id == "first_star" }).isTrue()
  }

  @Test
  fun audioCluesModeToggle_preservesExistingMinshawiTeacherAudioSourceAndTurns() {
    MinshawiJuz30Repository.ensureLoaded(context)
    val verse = MinshawiJuz30Repository.getVerse(context, 78, 1)
    assertThat(verse).isNotNull()
    assertThat(verse!!.audioUrl).isNotEmpty()
    assertThat(verse.turns).isNotEmpty()

    KidsClassroomManager.ensureKidsTabInitialized(context)
    KidsClassroomManager.setUseAudioCluesMode(context, true)
    assertThat(KidsClassroomManager.uiState.value.useAudioCluesMode).isTrue()
    assertThat(KidsClassroomManager.uiState.value.currentTurn).isNotNull()

    // Ensure persisted preference reloads cleanly
    KidsClassroomManager.ensureKidsTabInitialized(context)
    assertThat(KidsClassroomManager.uiState.value.useAudioCluesMode).isTrue()

    KidsClassroomManager.setUseAudioCluesMode(context, false)
    assertThat(KidsClassroomManager.uiState.value.useAudioCluesMode).isFalse()
  }

  @Test
  fun audioWaveformTajweedAnalyzer_detectsMaddGhunnahQalqalahAndSilencePrimarilyFromWav() {
    val sampleRate = 16000
    // 1. Verify RIFF PCM-16 WAV parser round-trip
    val toneSamples = FloatArray(sampleRate / 2) { i ->
      (kotlin.math.sin(2.0 * kotlin.math.PI * 800.0 * i / sampleRate) * 0.55).toFloat()
    }
    val wavBytes = buildSyntheticWavPcm16(sampleRate, toneSamples)
    val parsed = AudioWaveformTajweedAnalyzer.parseWavPcm16(wavBytes)
    assertThat(parsed).isNotNull()
    assertThat(parsed!!.first).isEqualTo(sampleRate)
    assertThat(parsed.second.size).isEqualTo(toneSamples.size)

    val analysis = TajweedSpanAdapter.analyzeText("إِنَّ الضَّالِّينَ أَحَدٌ")
    val tracker = StreamingPcmAcousticTracker()
    tracker.configure(sampleRate, 1)
    val analyzer = AudioWaveformTajweedAnalyzer()

    // 2. Silence must NEVER emit a Tajweed cue even if text has rules nearby
    val silentSample = analyzer.analyzeStep(
      frame = AudioAcousticFrame(rms = 0.01f),
      deltaMs = 100L,
      verseProgress = 0.5f,
      analysis = analysis
    )
    assertThat(silentSample.activeRuleType).isEqualTo(TajweedRuleType.NORMAL)
    assertThat(silentSample.isVoiceActive).isFalse()

    // 3. Sustained mid-band vowel tone (850 Hz) -> WAV drives MADD_2 -> MADD_4_5 -> MADD_6 even with misaligned text!
    var lastMaddSample = AudioTajweedClueSample()
    for (step in 0 until 72) { // 72 * 20 ms = 1440 ms sustained vowel plateau
      for (i in 0 until 320) {
        val t = (step * 320 + i).toDouble() / sampleRate.toDouble()
        val s = (kotlin.math.sin(2.0 * kotlin.math.PI * 850.0 * t) * 0.45).toFloat()
        tracker.pushMonoSample(s)
      }
      lastMaddSample = analyzer.analyzeStep(
        frame = tracker.latestFrame(),
        deltaMs = 20L,
        verseProgress = 0.25f, // intentionally misaligned vs الضَّالِّينَ
        analysis = analysis
      )
    }
    assertThat(lastMaddSample.activeRuleType).isEqualTo(TajweedRuleType.MADD_6)
    assertThat(lastMaddSample.maddProgress).isGreaterThan(0.65f)
    assertThat(lastMaddSample.isVoiceActive).isTrue()

    // 4. Sustained low-frequency nasal hum (190 Hz) -> WAV detects GHUNNAH_NOON from audio + soft text context
    tracker.reset()
    analyzer.reset()
    var lastNasalSample = AudioTajweedClueSample()
    for (step in 0 until 18) { // 360 ms nasal hum
      for (i in 0 until 320) {
        val t = (step * 320 + i).toDouble() / sampleRate.toDouble()
        val s = (kotlin.math.sin(2.0 * kotlin.math.PI * 190.0 * t) * 0.35).toFloat()
        tracker.pushMonoSample(s)
      }
      lastNasalSample = analyzer.analyzeStep(
        frame = tracker.latestFrame(),
        deltaMs = 20L,
        verseProgress = 0.15f,
        analysis = analysis
      )
    }
    assertThat(lastNasalSample.activeRuleType.isGhunnahFamily).isTrue()
    assertThat(lastNasalSample.activeRuleType).isEqualTo(TajweedRuleType.GHUNNAH_NOON)

    // 5. Qalqalah stop-closure dip -> crisp voiced burst triggers QALQALAH directly from audio
    analyzer.reset()
    val qalqalahSample = analyzer.analyzeStep(
      frame = AudioAcousticFrame(
        rms = 0.48f,
        lowBandRatio = 0.35f,
        midBandRatio = 0.50f,
        highBandRatio = 0.15f,
        qalqalahBurstScore = 0.82f
      ),
      deltaMs = 20L,
      verseProgress = 0.72f, // generous window covering final أَحَدٌ
      analysis = analysis
    )
    assertThat(qalqalahSample.activeRuleType).isEqualTo(TajweedRuleType.QALQALAH)
    assertThat(qalqalahSample.qalqalahPulse).isGreaterThan(0.8f)
    assertThat(qalqalahSample.matchedWordText).contains("أَحَد")

    // 6. Toggle option persists cleanly in KidsClassroomManager
    KidsClassroomManager.setUseAudioCluesMode(context, true)
    assertThat(KidsClassroomManager.uiState.value.useAudioCluesMode).isTrue()
    KidsClassroomManager.setUseAudioCluesMode(context, false)
    assertThat(KidsClassroomManager.uiState.value.useAudioCluesMode).isFalse()
  }

  @Test
  fun testKidsTabRemovedFromQuranActivity() {
    val titlesField = com.quran.labs.androidquran.ui.QuranActivity::class.java
      .getDeclaredField("TITLES").apply { isAccessible = true }
    val arabicTitlesField = com.quran.labs.androidquran.ui.QuranActivity::class.java
      .getDeclaredField("ARABIC_TITLES").apply { isAccessible = true }
    val titles = titlesField.get(null) as IntArray
    val arabicTitles = arabicTitlesField.get(null) as IntArray

    assertThat(titles.toList()).hasSize(3)
    assertThat(arabicTitles.toList()).hasSize(3)
    assertThat(titles.toList()).doesNotContain(com.quran.labs.androidquran.R.string.kids_classroom_tab_title)
    assertThat(arabicTitles.toList()).doesNotContain(com.quran.labs.androidquran.R.string.kids_classroom_tab_title)
  }

  @Test
  fun testTenRandomCelebrationThemesAndFullSurahCelebrationOnly() {
    assertThat(KidsCelebrationTheme.entries).hasSize(10)

    var prevTheme: KidsCelebrationTheme? = null
    val seenThemes = mutableSetOf<KidsCelebrationTheme>()
    repeat(60) {
      val next = KidsClassroomManager.pickRandomCelebrationTheme()
      if (prevTheme != null) {
        assertThat(next).isNotEqualTo(prevTheme)
      }
      prevTheme = next
      seenThemes.add(next)
    }
    assertThat(seenThemes.size).isAtLeast(6)

    val quranInfo = com.quran.data.core.QuranInfo(
      com.quran.labs.androidquran.pages.data.madani.MadaniDataSource()
    )
    RewardsDataStore.clearForTests(context)
    KidsSurahAnalysisStore.clearForTests(context)
    try {
      KidsClassroomManager.enableClassroomMode(
        context = context,
        currentPage = 583,
        selectedAyah = SuraAyah(78, 38), // Non-final 2-turn Ayah of Surah 78 (Husary Muallim stop reference)
        quranInfo = quranInfo
      )
      KidsClassroomManager.setAutoNextEnabled(context, false)
      KidsClassroomManager.setAyahRepeatTarget(context, 1)
      KidsClassroomManager.setSurahRepeatTarget(context, 1)
      KidsClassroomManager.setDifficultyLevel(context, KidsDifficultyLevel.INTERMEDIATE_80)

      val verse38 = MinshawiJuz30Repository.getVerse(context, 78, 38)!!
      assertThat(verse38.turns.size).isEqualTo(2)

      // Pass Turn 0 and Turn 1 of non-final Ayah 78:38 -> MUST NOT trigger full-screen celebration
      val turn0Eval = TurnEvaluationResult(
        suraAyah = SuraAyah(78, 38),
        turnIndex = 0,
        totalTurns = 2,
        scorePercent = 84.0,
        wordAccuracy = 0.88,
        tashkeelAccuracy = 0.84,
        confidenceScore = 0.84,
        passed = true,
        noSpeechDetected = false,
        decodedTranscript = "",
        wordGrades = emptyList()
      )
      KidsClassroomManager.applyTurnEvaluationResult(context, turn0Eval)
      assertThat(KidsClassroomManager.currentState().pendingCelebration).isNull()

      KidsClassroomManager.applyTurnEvaluationResult(context, turn0Eval.copy(turnIndex = 1, scorePercent = 96.0))
      assertThat(KidsClassroomManager.currentState().pendingCelebration).isNull()
      assertThat(KidsClassroomManager.currentState().stage).isEqualTo(ClassroomStage.TURN_PASSED)

      // Now complete Surah 112 (4 Ayat): Ayat 1..3 must NOT show celebration, Ayah 4 MUST show Surah celebration
      KidsClassroomManager.selectAyahAndTurn(context, SuraAyah(112, 1))
      for (ayah in 1..3) {
        val eval = turn0Eval.copy(
          suraAyah = SuraAyah(112, ayah),
          turnIndex = 0,
          totalTurns = 1,
          scorePercent = 90.0,
          wordAccuracy = 0.92
        )
        KidsClassroomManager.selectAyahAndTurn(context, SuraAyah(112, ayah), preserveRepetitionProgress = true)
        KidsClassroomManager.applyTurnEvaluationResult(context, eval)
        assertThat(KidsClassroomManager.currentState().pendingCelebration).isNull()
      }

      KidsClassroomManager.selectAyahAndTurn(context, SuraAyah(112, 4), preserveRepetitionProgress = true)
      KidsClassroomManager.applyTurnEvaluationResult(
        context,
        turn0Eval.copy(
          suraAyah = SuraAyah(112, 4),
          turnIndex = 0,
          totalTurns = 1,
          scorePercent = 98.0,
          wordAccuracy = 0.98
        )
      )

      val celebration = KidsClassroomManager.currentState().pendingCelebration
      assertThat(celebration).isNotNull()
      assertThat(celebration!!.isSurahCompleted).isTrue()
      assertThat(celebration.suraAyah).isEqualTo(SuraAyah(112, 4))
      assertThat(celebration.scorePercent).isEqualTo(92) // avg(90, 90, 90, 98)
      assertThat(celebration.starsEarned).isEqualTo(3)
      assertThat(celebration.totalStars).isAtLeast(3)
      assertThat(KidsCelebrationTheme.entries).contains(celebration.theme)
    } finally {
      KidsClassroomManager.disableClassroomMode(context)
    }
  }

  @Test
  fun testAyahAndSurahLevelRepetitionFlow() {
    val quranInfo = com.quran.data.core.QuranInfo(
      com.quran.labs.androidquran.pages.data.madani.MadaniDataSource()
    )
    try {
      KidsClassroomManager.enableClassroomMode(
        context = context,
        currentPage = 604,
        selectedAyah = SuraAyah(112, 1),
        quranInfo = quranInfo
      )
      KidsClassroomManager.setAutoNextEnabled(context, false)
      KidsClassroomManager.setAyahRepeatTarget(context, 3)
      KidsClassroomManager.setSurahRepeatTarget(context, 2)
      KidsClassroomManager.setDifficultyLevel(context, KidsDifficultyLevel.INTERMEDIATE_80)

      val pass1121 = TurnEvaluationResult(
        suraAyah = SuraAyah(112, 1),
        turnIndex = 0,
        totalTurns = 1,
        scorePercent = 95.0,
        wordAccuracy = 0.95,
        tashkeelAccuracy = 0.95,
        confidenceScore = 0.95,
        passed = true,
        noSpeechDetected = false,
        decodedTranscript = "",
        wordGrades = emptyList()
      )

      // Non-final Ayah 112:1: Reps 1/3 and 2/3 track ayahRepeatCompleted WITHOUT showing full-screen celebration
      KidsClassroomManager.applyTurnEvaluationResult(context, pass1121)
      assertThat(KidsClassroomManager.currentState().pendingCelebration).isNull()
      assertThat(KidsClassroomManager.currentState().ayahRepeatCompleted).isEqualTo(1)

      KidsClassroomManager.applyTurnEvaluationResult(context, pass1121)
      assertThat(KidsClassroomManager.currentState().pendingCelebration).isNull()
      assertThat(KidsClassroomManager.currentState().ayahRepeatCompleted).isEqualTo(2)

      // Rep 3/3 on non-final Ayah -> resets ayahRepeatCompleted to 0, still no full-screen Surah celebration
      KidsClassroomManager.applyTurnEvaluationResult(context, pass1121)
      assertThat(KidsClassroomManager.currentState().pendingCelebration).isNull()
      assertThat(KidsClassroomManager.currentState().ayahRepeatCompleted).isEqualTo(0)

      // Final Ayah 112:4 with ayahRepeatTarget = 2, surahRepeatTarget = 2
      KidsClassroomManager.setAyahRepeatTarget(context, 2)
      KidsClassroomManager.selectAyahAndTurn(context, SuraAyah(112, 4))
      val pass1124 = pass1121.copy(suraAyah = SuraAyah(112, 4))

      // Final Ayah rep 1/2 -> NOT full Surah complete yet -> no celebration
      KidsClassroomManager.applyTurnEvaluationResult(context, pass1124)
      assertThat(KidsClassroomManager.currentState().pendingCelebration).isNull()
      assertThat(KidsClassroomManager.currentState().ayahRepeatCompleted).isEqualTo(1)

      // Final Ayah rep 2/2 -> FULL Surah pass 1/2 complete -> emits full-screen Surah celebration & loops to 112:1
      KidsClassroomManager.applyTurnEvaluationResult(context, pass1124)
      val surahDone1 = KidsClassroomManager.currentState().pendingCelebration!!
      assertThat(surahDone1.isSurahCompleted).isTrue()
      assertThat(surahDone1.surahRepetitionCompleted).isEqualTo(1)
      assertThat(surahDone1.surahRepetitionTarget).isEqualTo(2)
      KidsClassroomManager.dismissCelebrationAndContinue(context, autoStart = false)
      assertThat(KidsClassroomManager.currentState().currentAyah).isEqualTo(SuraAyah(112, 1))
      assertThat(KidsClassroomManager.currentState().surahRepeatCompleted).isEqualTo(1)

      // Jump to 112:4 with ayahRepeatTarget = 1 preserving surah pass 1, complete Surah pass 2/2 -> advances to 113:1
      KidsClassroomManager.setAyahRepeatTarget(context, 1)
      KidsClassroomManager.selectAyahAndTurn(context, SuraAyah(112, 4), preserveRepetitionProgress = true)
      KidsClassroomManager.applyTurnEvaluationResult(context, pass1124)
      val surahDone2 = KidsClassroomManager.currentState().pendingCelebration!!
      assertThat(surahDone2.isSurahCompleted).isTrue()
      assertThat(surahDone2.surahRepetitionCompleted).isEqualTo(2)
      KidsClassroomManager.dismissCelebrationAndContinue(context, autoStart = false)
      assertThat(KidsClassroomManager.currentState().currentAyah).isEqualTo(SuraAyah(113, 1))
      assertThat(KidsClassroomManager.currentState().surahRepeatCompleted).isEqualTo(0)
    } finally {
      KidsClassroomManager.setAyahRepeatTarget(context, 1)
      KidsClassroomManager.setSurahRepeatTarget(context, 1)
      KidsClassroomManager.disableClassroomMode(context)
    }
  }

  @Test
  fun testDirectPrevAndNextAyahNavigationNearMicRtlDirection() {
    val quranInfo = com.quran.data.core.QuranInfo(
      com.quran.labs.androidquran.pages.data.madani.MadaniDataSource()
    )
    try {
      KidsClassroomManager.enableClassroomMode(
        context = context,
        currentPage = 582,
        selectedAyah = SuraAyah(78, 1),
        quranInfo = quranInfo
      )
      val overlay = KidsClassroomOverlayView(context)
      val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY)
      val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(160, android.view.View.MeasureSpec.AT_MOST)
      overlay.measure(widthSpec, heightSpec)
      overlay.layout(0, 0, overlay.measuredWidth, overlay.measuredHeight)

      val nextBtn = overlay.findViewWithTag<android.view.View>("kids_next_ayah_btn")
      val playOrb = overlay.findViewWithTag<android.view.View>("kids_play_orb_btn")
      val prevBtn = overlay.findViewWithTag<android.view.View>("kids_prev_ayah_btn")
      assertThat(nextBtn).isNotNull()
      assertThat(playOrb).isNotNull()
      assertThat(prevBtn).isNotNull()

      // RTL Quran rule: Left button proceeds (Next Ayah), Right button goes back (Previous Ayah)
      assertThat(nextBtn.left).isLessThan(playOrb.left)
      assertThat(prevBtn.left).isGreaterThan(playOrb.left)

      nextBtn.performClick()
      assertThat(KidsClassroomManager.currentState().currentAyah).isEqualTo(SuraAyah(78, 2))
      assertThat(KidsClassroomManager.currentState().currentTurnIndex).isEqualTo(0)

      prevBtn.performClick()
      assertThat(KidsClassroomManager.currentState().currentAyah).isEqualTo(SuraAyah(78, 1))
      assertThat(KidsClassroomManager.currentState().currentTurnIndex).isEqualTo(0)
    } finally {
      KidsClassroomManager.disableClassroomMode(context)
    }
  }

  @Test
  fun testDifficultySelectorPassThresholds() {
    val quranInfo = com.quran.data.core.QuranInfo(
      com.quran.labs.androidquran.pages.data.madani.MadaniDataSource()
    )
    try {
      KidsClassroomManager.enableClassroomMode(
        context = context,
        currentPage = 604,
        selectedAyah = SuraAyah(112, 1),
        quranInfo = quranInfo
      )
      KidsClassroomManager.setAutoNextEnabled(context, false)

      val score85Eval = TurnEvaluationResult(
        suraAyah = SuraAyah(112, 1),
        turnIndex = 0,
        totalTurns = 1,
        scorePercent = 85.0,
        wordAccuracy = 0.85,
        tashkeelAccuracy = 0.85,
        confidenceScore = 0.85,
        passed = true,
        noSpeechDetected = false,
        decodedTranscript = "",
        wordGrades = emptyList()
      )

      // At 90% Proficient -> 85% MUST fail (TURN_RETRY)
      KidsClassroomManager.setDifficultyLevel(context, KidsDifficultyLevel.PROFICIENT_90)
      assertThat(KidsClassroomManager.currentState().difficultyLevel).isEqualTo(KidsDifficultyLevel.PROFICIENT_90)
      KidsClassroomManager.applyTurnEvaluationResult(context, score85Eval)
      assertThat(KidsClassroomManager.currentState().stage).isEqualTo(ClassroomStage.TURN_RETRY)
      assertThat(KidsClassroomManager.currentState().lastEvaluation?.passed).isFalse()

      // At 80% Standard -> 85% MUST pass (TURN_PASSED)
      KidsClassroomManager.setDifficultyLevel(context, KidsDifficultyLevel.INTERMEDIATE_80)
      KidsClassroomManager.applyTurnEvaluationResult(context, score85Eval)
      assertThat(KidsClassroomManager.currentState().stage).isEqualTo(ClassroomStage.TURN_PASSED)
      assertThat(KidsClassroomManager.currentState().lastEvaluation?.passed).isTrue()

      // At 80% Standard -> 74% MUST fail; at 70% Starter -> 74% MUST pass
      val score74Eval = score85Eval.copy(scorePercent = 74.0, wordAccuracy = 0.75)
      KidsClassroomManager.applyTurnEvaluationResult(context, score74Eval)
      assertThat(KidsClassroomManager.currentState().stage).isEqualTo(ClassroomStage.TURN_RETRY)

      KidsClassroomManager.setDifficultyLevel(context, KidsDifficultyLevel.STARTER_70)
      KidsClassroomManager.applyTurnEvaluationResult(context, score74Eval)
      assertThat(KidsClassroomManager.currentState().stage).isEqualTo(ClassroomStage.TURN_PASSED)
    } finally {
      KidsClassroomManager.setDifficultyLevel(context, KidsDifficultyLevel.INTERMEDIATE_80)
      KidsClassroomManager.disableClassroomMode(context)
    }
  }

  @Test
  fun testRepeatFromFirstMistakeTailSlicingAndSheikhOnlyRetry() {
    val vocab = arrayOf("<unk>", "\u2581قُلْ", "\u2581هُوَ", "\u2581اللَّهُ", "هُ", "\u2581أَحَدٌ", "\u2581الصَّمَدُ")
    val fiveWordTurn = TurnSpec(
      turnIndex = 0,
      totalTurns = 1,
      wordStartIdx = 0,
      wordEndIdx = 5,
      qariStartSec = 1.0,
      qariEndSec = 6.0,
      kidsStartSec = 6.2,
      kidsEndSec = 11.2,
      contiguousPlayback = true,
      cutReason = "full",
      uthmaniWords = listOf("قُلْ", "هُوَ", "ٱللَّهُ", "أَحَدٌ", "ٱلصَّمَدُ"),
      normalizedWords = listOf("قُلْ", "هُوَ", "اللَّهُ", "أَحَدٌ", "الصَّمَدُ"),
      qariOnnxWords = listOf("قُلْ", "هُوَ", "اللَّهُ", "أَحَدٌ", "الصَّمَدُ"),
      tokenIds = listOf(1, 2, 3, 4, 5, 6)
    )

    // Mistake on the 3rd word (index 2) -> must start from the word BEFORE the mistake (word 2, index 1)
    // and replay words 2, 3, 4, 5!
    val tail = KidsClassroomManager.buildMistakeTailTurn(
      baseTurn = fiveWordTurn,
      firstMistakeIndexInTurn = 2,
      vocab = vocab
    )
    assertThat(tail.uthmaniWords).containsExactly("هُوَ", "ٱللَّهُ", "أَحَدٌ", "ٱلصَّمَدُ").inOrder()
    assertThat(tail.wordStartIdx).isEqualTo(1)
    assertThat(tail.wordEndIdx).isEqualTo(5)
    assertThat(tail.qariStartSec).isGreaterThan(fiveWordTurn.qariStartSec)
    assertThat(tail.qariStartSec).isLessThan(fiveWordTurn.qariEndSec)
    assertThat(tail.qariEndSec).isEqualTo(fiveWordTurn.qariEndSec)
    assertThat(tail.tokenIds).containsExactly(2, 3, 4, 5, 6).inOrder()
    assertThat(tail.contiguousPlayback).isFalse()

    // Mistake on the very first word (index 0) -> clamps cleanly to index 0 (all 5 words), Sheikh-only
    val tailFromStart = KidsClassroomManager.buildMistakeTailTurn(
      baseTurn = fiveWordTurn,
      firstMistakeIndexInTurn = 0,
      vocab = vocab
    )
    assertThat(tailFromStart.wordStartIdx).isEqualTo(0)
    assertThat(tailFromStart.uthmaniWords).isEqualTo(fiveWordTurn.uthmaniWords)
    assertThat(tailFromStart.contiguousPlayback).isFalse()

    // Verify manager schedules the sliced tail (starting 1 word before the first mistake)
    // when repeatFromFirstMistakeOnly is enabled
    val quranInfo = com.quran.data.core.QuranInfo(
      com.quran.labs.androidquran.pages.data.madani.MadaniDataSource()
    )
    try {
      KidsClassroomManager.enableClassroomMode(
        context = context,
        currentPage = 604,
        selectedAyah = SuraAyah(112, 4), // 5-word Ayah: وَلَمْ يَكُن لَّهُۥ كُفُوًا أَحَدٌۢ
        quranInfo = quranInfo
      )
      KidsClassroomManager.setAutoNextEnabled(context, false)
      KidsClassroomManager.setRepeatFromFirstMistakeOnly(context, true)
      val currentTurn = KidsClassroomManager.currentState().currentTurn!!
      assertThat(currentTurn.uthmaniWords.size).isAtLeast(4)

      val wordGrades = currentTurn.uthmaniWords.mapIndexed { idx, w ->
        WordGrade(
          wordIndexInTurn = idx,
          mushafWordPosition = currentTurn.wordStartIdx + idx + 1,
          uthmaniWord = w,
          normalizedWord = currentTurn.normalizedWords.getOrElse(idx) { w },
          status = if (idx < 2) WordGradeStatus.CORRECT else WordGradeStatus.WORD_ERROR,
          tokenConfidence = if (idx < 2) 0.95f else 0.25f,
          matchedHypWord = if (idx == 2) "خَطَأ" else w
        )
      }
      val failedEval = TurnEvaluationResult(
        suraAyah = SuraAyah(112, 4),
        turnIndex = 0,
        totalTurns = 1,
        scorePercent = 45.0,
        wordAccuracy = 0.40,
        tashkeelAccuracy = 0.40,
        confidenceScore = 0.50,
        passed = false,
        noSpeechDetected = false,
        decodedTranscript = "",
        wordGrades = wordGrades
      )

      KidsClassroomManager.applyTurnEvaluationResult(context, failedEval)

      val scheduledTail = KidsClassroomManager.activeRetryTailTurnForTest()
      assertThat(scheduledTail).isNotNull()
      assertThat(scheduledTail!!.wordStartIdx).isEqualTo(currentTurn.wordStartIdx + 1)
      assertThat(scheduledTail.uthmaniWords).isEqualTo(currentTurn.uthmaniWords.drop(1))
      assertThat(scheduledTail.qariStartSec).isGreaterThan(currentTurn.qariStartSec)
      assertThat(KidsClassroomManager.currentState().activeMistakeTailStartWordIndex)
        .isEqualTo(currentTurn.wordStartIdx + 1)
    } finally {
      KidsClassroomManager.setRepeatFromFirstMistakeOnly(context, false)
      KidsClassroomManager.disableClassroomMode(context)
    }
  }

  @Test
  fun testMistakeTailSlowMoSpeedConstant() {
    assertThat(KidsClassroomAudioController.MISTAKE_TAIL_SLOW_MO_SPEED).isWithin(0.001f).of(0.75f)
  }

  @Test
  fun testPerSurahRevisionAnalysisStoreTrackingAndPersistence() {
    KidsSurahAnalysisStore.clearForTests(context)
    try {
      val eval1 = TurnEvaluationResult(
        suraAyah = SuraAyah(112, 4),
        turnIndex = 0,
        totalTurns = 1,
        scorePercent = 62.0,
        wordAccuracy = 0.60,
        tashkeelAccuracy = 0.40,
        confidenceScore = 0.65,
        passed = false,
        noSpeechDetected = false,
        decodedTranscript = "وَلَمْ يَكُن كُفُوًا",
        wordGrades = listOf(
          WordGrade(0, 1, "وَلَمْ", "وَلَمْ", WordGradeStatus.CORRECT, 0.96f, "وَلَمْ"),
          WordGrade(1, 2, "يَكُن", "يَكُن", WordGradeStatus.TASHKEEL_ERROR, 0.80f, "يَكِن"),
          WordGrade(2, 3, "لَّهُۥ", "لَهُ", WordGradeStatus.MISSING, 0.15f, null),
          WordGrade(3, 4, "كُفُوًا", "كُفُوًا", WordGradeStatus.WORD_ERROR, 0.30f, "كُفْوًا"),
          WordGrade(4, 5, "أَحَدٌۢ", "أَحَدٌ", WordGradeStatus.CORRECT, 0.92f, "أَحَدٌ")
        )
      )
      // Record twice so word 4 ("كُفُوًا") becomes the top difficult word
      KidsSurahAnalysisStore.recordEvaluation(context, eval1, effectivePassed = false, surahNameAr = "الإخلاص")
      val eval2 = eval1.copy(
        scorePercent = 78.0,
        wordGrades = listOf(
          WordGrade(0, 1, "وَلَمْ", "وَلَمْ", WordGradeStatus.CORRECT, 0.98f, "وَلَمْ"),
          WordGrade(1, 2, "يَكُن", "يَكُن", WordGradeStatus.CORRECT, 0.95f, "يَكُن"),
          WordGrade(2, 3, "لَّهُۥ", "لَهُ", WordGradeStatus.CORRECT, 0.90f, "لَهُ"),
          WordGrade(3, 4, "كُفُوًا", "كُفُوًا", WordGradeStatus.WORD_ERROR, 0.35f, "كُفْوًا"),
          WordGrade(4, 5, "أَحَدٌۢ", "أَحَدٌ", WordGradeStatus.CORRECT, 0.94f, "أَحَدٌ")
        )
      )
      KidsSurahAnalysisStore.recordEvaluation(context, eval2, effectivePassed = false, surahNameAr = "الإخلاص")

      // Reload from disk to verify JSON persistence
      KidsSurahAnalysisStore.reloadFromDiskForTests(context)

      val summary = KidsSurahAnalysisStore.getSurahSummary(context, 112)
      assertThat(summary.surah).isEqualTo(112)
      assertThat(summary.practicedAyahCount).isEqualTo(1)
      assertThat(summary.totalAttempts).isEqualTo(2)
      assertThat(summary.avgAccuracyPercent).isEqualTo(70) // avg(62, 78)
      assertThat(summary.wordErrorTotal).isEqualTo(2)
      assertThat(summary.missingTotal).isEqualTo(1)
      assertThat(summary.tashkeelErrorTotal).isEqualTo(1)
      assertThat(summary.totalMistakes).isEqualTo(4)
      assertThat(summary.difficultWords).hasSize(3)

      // Most difficult word must be ranked first ("كُفُوًا" with 2 word errors)
      val topWord = summary.difficultWords.first()
      assertThat(topWord.uthmaniWord).isEqualTo("كُفُوًا")
      assertThat(topWord.wordErrorCount).isEqualTo(2)
      assertThat(topWord.lastMatchedHypWord).isEqualTo("كُفْوًا")
      assertThat(topWord.primaryErrorType).isEqualTo(WordGradeStatus.WORD_ERROR)

      // Clear Surah resets its analysis cleanly
      KidsSurahAnalysisStore.clearSurah(context, 112)
      val cleared = KidsSurahAnalysisStore.getSurahSummary(context, 112)
      assertThat(cleared.totalMistakes).isEqualTo(0)
      assertThat(cleared.difficultWords).isEmpty()
    } finally {
      KidsSurahAnalysisStore.clearForTests(context)
    }
  }

  @Test
  fun testSurahHighAccuracyBadgesPersistenceUpgradeAndRank() {
    RewardsDataStore.clearForTests(context)
    try {
      // Below 80% accuracy does NOT award a high-accuracy Surah badge
      val lowRes = RewardsDataStore.recordSurahCompletion(
        context = context,
        surah = 112,
        surahNameAr = "الإخلاص",
        accuracyPercent = 74,
        todayStamp = "2026-11-20"
      )
      assertThat(lowRes.earnedHighAccuracyBadge).isFalse()
      assertThat(RewardsDataStore.badgeForSurah(112)).isNull()
      assertThat(RewardsDataStore.state.value.learnerRank).isEqualTo(KidsLearnerRank.LITTLE_SPROUT)

      // 84% accuracy -> unlocks SILVER badge
      val silverRes = RewardsDataStore.recordSurahCompletion(
        context = context,
        surah = 112,
        surahNameAr = "الإخلاص",
        accuracyPercent = 84,
        todayStamp = "2026-11-20"
      )
      assertThat(silverRes.earnedHighAccuracyBadge).isTrue()
      assertThat(silverRes.isNewlyUnlocked).isTrue()
      assertThat(silverRes.isTierUpgraded).isFalse()
      assertThat(silverRes.badge?.tier).isEqualTo(SurahBadgeTier.SILVER)
      assertThat(RewardsDataStore.state.value.learnerRank).isEqualTo(KidsLearnerRank.SHINING_RECITER)

      // Re-completing Surah 112 at 94% -> upgrades to GOLD
      val goldRes = RewardsDataStore.recordSurahCompletion(
        context = context,
        surah = 112,
        surahNameAr = "الإخلاص",
        accuracyPercent = 94,
        todayStamp = "2026-11-21"
      )
      assertThat(goldRes.isNewlyUnlocked).isFalse()
      assertThat(goldRes.isTierUpgraded).isTrue()
      assertThat(goldRes.previousTier).isEqualTo(SurahBadgeTier.SILVER)
      assertThat(goldRes.badge?.tier).isEqualTo(SurahBadgeTier.GOLD)
      assertThat(goldRes.badge?.bestAccuracyPercent).isEqualTo(94)
      assertThat(goldRes.badge?.completionsCount).isEqualTo(2)

      // Lower subsequent attempt (82%) NEVER downgrades GOLD badge or bestAccuracyPercent
      val keepBestRes = RewardsDataStore.recordSurahCompletion(
        context = context,
        surah = 112,
        surahNameAr = "الإخلاص",
        accuracyPercent = 82,
        todayStamp = "2026-11-21"
      )
      assertThat(keepBestRes.isTierUpgraded).isFalse()
      assertThat(keepBestRes.badge?.tier).isEqualTo(SurahBadgeTier.GOLD)
      assertThat(keepBestRes.badge?.bestAccuracyPercent).isEqualTo(94)
      assertThat(keepBestRes.badge?.completionsCount).isEqualTo(3)

      // Surah 1 at 98% -> DIAMOND badge + unlocks Diamond Hafiz milestone
      val diamondRes = RewardsDataStore.recordSurahCompletion(
        context = context,
        surah = 1,
        surahNameAr = "الفاتحة",
        accuracyPercent = 98,
        todayStamp = "2026-11-21"
      )
      assertThat(diamondRes.badge?.tier).isEqualTo(SurahBadgeTier.DIAMOND)

      // 3rd Surah (114 at 91%) -> unlocks 3-Surah Champion & GOLDEN_LANTERN rank
      RewardsDataStore.recordSurahCompletion(
        context = context,
        surah = 114,
        surahNameAr = "الناس",
        accuracyPercent = 91,
        todayStamp = "2026-11-21"
      )
      RewardsDataStore.setLearnerName(context, "يوسف")

      // Reload from disk to verify persistence
      RewardsDataStore.reloadFromDiskForTests(context)
      val state = RewardsDataStore.state.value
      assertThat(state.learnerName).isEqualTo("يوسف")
      assertThat(state.earnedSurahBadgesList).hasSize(3)
      assertThat(state.diamondBadgeCount).isEqualTo(1)
      assertThat(state.goldBadgeCount).isEqualTo(2)
      assertThat(state.silverBadgeCount).isEqualTo(0)
      assertThat(state.learnerRank).isEqualTo(KidsLearnerRank.GOLDEN_LANTERN)
      assertThat(state.unlockedBadges.map { it.id }).containsAtLeast(
        "first_surah_badge",
        "golden_surah_badge",
        "diamond_surah_badge",
        "three_surahs_master"
      )
      // Ordered by tier desc (Diamond Surah 1 first)
      assertThat(state.earnedSurahBadgesList.first().surah).isEqualTo(1)
    } finally {
      RewardsDataStore.clearForTests(context)
    }
  }

  @Test
  fun testFullSurahCompletionAwardsBadgeOnCelebrationEvent() {
    RewardsDataStore.clearForTests(context)
    KidsSurahAnalysisStore.clearForTests(context)
    val quranInfo = com.quran.data.core.QuranInfo(
      com.quran.labs.androidquran.pages.data.madani.MadaniDataSource()
    )
    try {
      KidsClassroomManager.enableClassroomMode(
        context = context,
        currentPage = 602,
        selectedAyah = SuraAyah(108, 1), // Surah Al-Kawthar (3 Ayat)
        quranInfo = quranInfo
      )
      KidsClassroomManager.setAutoNextEnabled(context, false)
      KidsClassroomManager.setAyahRepeatTarget(context, 1)
      KidsClassroomManager.setSurahRepeatTarget(context, 1)

      for (ayahNum in 1..3) {
        KidsClassroomManager.selectAyahAndTurn(
          context = context,
          suraAyah = SuraAyah(108, ayahNum),
          turnIndex = 0,
          autoStartMode = null
        )
        val passEval = TurnEvaluationResult(
          suraAyah = SuraAyah(108, ayahNum),
          turnIndex = 0,
          totalTurns = 1,
          scorePercent = 95.0,
          wordAccuracy = 1.0,
          tashkeelAccuracy = 0.92,
          confidenceScore = 0.96,
          passed = true,
          noSpeechDetected = false,
          decodedTranscript = "",
          wordGrades = emptyList()
        )
        KidsClassroomManager.applyTurnEvaluationResult(context, passEval)
      }

      val celebration = KidsClassroomManager.currentState().pendingCelebration
      assertThat(celebration).isNotNull()
      assertThat(celebration!!.isSurahCompleted).isTrue()
      val awardedBadge = celebration.awardedSurahBadge
      assertThat(awardedBadge).isNotNull()
      assertThat(awardedBadge!!.surah).isEqualTo(108)
      assertThat(awardedBadge.tier).isEqualTo(SurahBadgeTier.GOLD)
      assertThat(celebration.isNewSurahBadge).isTrue()
      assertThat(RewardsDataStore.badgeForSurah(108)?.tier).isEqualTo(SurahBadgeTier.GOLD)
    } finally {
      KidsClassroomManager.disableClassroomMode(context)
      RewardsDataStore.clearForTests(context)
      KidsSurahAnalysisStore.clearForTests(context)
    }
  }

  @Test
  fun testSurahJourneyPlannerJuzAndFullQuranMilestones() {
    // 1. Verify all 30 Juz specs exist and cover every Surah 1..114
    assertThat(SurahJourneyPlanner.QURAN_JUZ_SPECS).hasSize(30)
    val coveredSurahs = SurahJourneyPlanner.QURAN_JUZ_SPECS.flatMap { it.surahNumbers }.toSet()
    assertThat(coveredSurahs).containsExactlyElementsIn((1..114).toList())

    // 2. Build a realistic learner state: Al-Fatiha + 3 Quls completed (1 with Diamond badge, 3 with full Ayat mastery)
    //    plus partial progress on Surah Al-Masad (111: 2 of 5 Ayat)
    val fatihaBadge = SurahCompletionBadge(
      surah = 1,
      surahNameAr = "الفاتحة",
      tier = SurahBadgeTier.DIAMOND,
      bestAccuracyPercent = 98,
      completionsCount = 2,
      earnedAtMillis = 1_700_000_000_000L
    )
    val rewards = KidsRewardsState(
      surahBadges = mapOf(1 to fatihaBadge),
      surahMasteredCount = mapOf(
        1 to 7,
        114 to 6,
        113 to 5,
        112 to 4,
        111 to 2 // partial progress (2/5 Ayat)
      ),
      surahStars = mapOf(1 to 21, 114 to 18, 113 to 15, 112 to 12, 111 to 5)
    )

    // 3. Juz 'Amma Adventure: 38 stepping stones (1, 114..78) + 5 milestone gates
    val ammaPlan = SurahJourneyPlanner.buildSurahJourneyPlan(
      scope = SurahJourneyScope.JUZ_AMMA,
      rewards = rewards
    )
    assertThat(ammaPlan.nodes).hasSize(38)
    assertThat(ammaPlan.nodes.first().surahNumber).isEqualTo(1)
    assertThat(ammaPlan.nodes[1].surahNumber).isEqualTo(114)
    assertThat(ammaPlan.nodes.last().surahNumber).isEqualTo(78)
    assertThat(ammaPlan.gates).hasSize(5)
    assertThat(ammaPlan.completedSurahCount).isEqualTo(4)
    assertThat(ammaPlan.badgedSurahCount).isEqualTo(1)

    // Verify per-node statuses
    assertThat(ammaPlan.nodes[0].status).isEqualTo(JourneyNodeStatus.COMPLETED_BADGED)
    assertThat(ammaPlan.nodes[1].status).isEqualTo(JourneyNodeStatus.COMPLETED_STARS)
    assertThat(ammaPlan.nodes[4].surahNumber).isEqualTo(111)
    assertThat(ammaPlan.nodes[4].status).isEqualTo(JourneyNodeStatus.IN_PROGRESS)
    assertThat(ammaPlan.nodes[4].progressFraction).isWithin(0.01f).of(0.4f)
    assertThat(ammaPlan.nextRecommendedSurah).isEqualTo(111)

    // First milestone gate ("The Four Shields") unlocked; second gate ("Oasis of Short Surahs") locked at 4/10
    val fourShieldsGate = ammaPlan.gates.first { it.id == "amma_four_shields" }
    val oasisGate = ammaPlan.gates.first { it.id == "amma_oasis_10" }
    assertThat(fourShieldsGate.isUnlocked).isTrue()
    assertThat(oasisGate.isUnlocked).isFalse()
    assertThat(oasisGate.currentCount).isEqualTo(4)
    assertThat(oasisGate.requiredCount).isEqualTo(10)
    assertThat(ammaPlan.unlockedGateCount).isEqualTo(1)

    // 4. Full Quran 30-Juz overview: 30 Juz stations (30 down to 1) + 6 milestone gates
    val fullPlan = SurahJourneyPlanner.buildFullQuranJourneyPlan(rewards)
    assertThat(fullPlan.juzNodes).hasSize(30)
    assertThat(fullPlan.juzNodes.first().juzSpec.juzNumber).isEqualTo(30)
    assertThat(fullPlan.juzNodes.last().juzSpec.juzNumber).isEqualTo(1)
    assertThat(fullPlan.gates).hasSize(6)
    assertThat(fullPlan.completedSurahsTotal).isEqualTo(4)
    assertThat(fullPlan.juzNodes.first().status).isEqualTo(JourneyNodeStatus.IN_PROGRESS)
    assertThat(fullPlan.nextRecommendedJuz).isEqualTo(30)

    // 5. Drilling down into Juz 29 builds the 11-Surah trail (77 down to 67) with 2 milestone gates
    val juz29Plan = SurahJourneyPlanner.buildSurahJourneyPlan(
      scope = SurahJourneyScope.FULL_QURAN,
      rewards = rewards,
      selectedJuzNumber = 29
    )
    assertThat(juz29Plan.nodes).hasSize(11)
    assertThat(juz29Plan.nodes.first().surahNumber).isEqualTo(77)
    assertThat(juz29Plan.nodes.last().surahNumber).isEqualTo(67)
    assertThat(juz29Plan.gates).hasSize(2)
    assertThat(juz29Plan.nodes.first().status).isEqualTo(JourneyNodeStatus.NEXT_UP)
  }

  @Test
  fun testKidsAchievementsActivityLaunchesWithoutCrash() {
    val controller = org.robolectric.Robolectric.buildActivity(KidsAchievementsActivity::class.java)
    val activity = controller.create().start().resume().get()
    assertThat(activity).isNotNull()
    assertThat(activity.isFinishing).isFalse()
    controller.pause().stop().destroy()
  }

  @Test
  fun testKidsModeActivityLaunchesWithoutCrash() {
    val controller = org.robolectric.Robolectric.buildActivity(KidsModeActivity::class.java)
    val activity = controller.create().start().resume().get()
    assertThat(activity).isNotNull()
    assertThat(activity.isFinishing).isFalse()
    controller.pause().stop().destroy()
  }

  @Test
  fun testKidsModeNavigationStateReturnEventAndArchipelagoMapping() {
    KidsModeNavigationState.clearForTests()
    assertThat(KidsModeNavigationState.pendingReturnEvent.value).isNull()

    // Juz 'Amma & Al-Fatiha map to JUZ_AMMA without drill-down
    assertThat(KidsModeNavigationState.resolveArchipelagoForSurah(1))
      .isEqualTo(SurahJourneyScope.JUZ_AMMA to null)
    assertThat(KidsModeNavigationState.resolveArchipelagoForSurah(112))
      .isEqualTo(SurahJourneyScope.JUZ_AMMA to null)
    assertThat(KidsModeNavigationState.resolveArchipelagoForSurah(78))
      .isEqualTo(SurahJourneyScope.JUZ_AMMA to null)

    // Juz 29 maps to JUZ_TABARAK
    assertThat(KidsModeNavigationState.resolveArchipelagoForSurah(67))
      .isEqualTo(SurahJourneyScope.JUZ_TABARAK to null)
    assertThat(KidsModeNavigationState.resolveArchipelagoForSurah(77))
      .isEqualTo(SurahJourneyScope.JUZ_TABARAK to null)

    // Other Surahs map to FULL_QURAN drilled into the matching Juz
    assertThat(KidsModeNavigationState.resolveArchipelagoForSurah(36))
      .isEqualTo(SurahJourneyScope.FULL_QURAN to 22)
    assertThat(KidsModeNavigationState.resolveArchipelagoForSurah(18))
      .isEqualTo(SurahJourneyScope.FULL_QURAN to 15)

    // Emit and consume return event once
    val event = KidsModeNavigationState.emitReturnToIslands(
      surahNumber = 112,
      starsGained = 6,
      ayatMasteredGained = 2,
      surahJustCompleted = true
    )
    assertThat(KidsModeNavigationState.pendingReturnEvent.value).isEqualTo(event)
    KidsModeNavigationState.consumeReturnEvent(event.eventId)
    assertThat(KidsModeNavigationState.pendingReturnEvent.value).isNull()
  }

  private fun buildSyntheticWavPcm16(sampleRate: Int, samples: FloatArray): ByteArray {
    val dataBytes = samples.size * 2
    val bb = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    bb.put("RIFF".toByteArray(Charsets.US_ASCII))
    bb.putInt(36 + dataBytes)
    bb.put("WAVE".toByteArray(Charsets.US_ASCII))
    bb.put("fmt ".toByteArray(Charsets.US_ASCII))
    bb.putInt(16) // PCM fmt chunk size
    bb.putShort(1) // PCM format
    bb.putShort(1) // mono
    bb.putInt(sampleRate)
    bb.putInt(sampleRate * 2) // byte rate
    bb.putShort(2) // block align
    bb.putShort(16) // bits per sample
    bb.put("data".toByteArray(Charsets.US_ASCII))
    bb.putInt(dataBytes)
    for (s in samples) {
      bb.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
    }
    return bb.array()
  }
}
