package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.view.HighlightingImageView
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.data.coordinates.GlyphCoords
import com.quran.page.common.data.coordinates.PageGlyphsCoords
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [33])
class KidsClassroomTest {

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  @After
  fun tearDown() {
    KidsClassroomManager.disableClassroomMode(context)
  }

  @Test
  fun testBundledMinshawiJuz30JsonLoadsAll564AyatAndSanitizesTurns() {
    // Default teacher reciter is now Husary Muallim (canonical stop reference, no children chorus)
    MinshawiJuz30Repository.setActiveTeacherQari(context, KidsTeacherQari.HUSARY_MUALLIM)
    assertThat(MinshawiJuz30Repository.getActiveTeacherQari()).isEqualTo(KidsTeacherQari.HUSARY_MUALLIM)
    assertThat(MinshawiJuz30Repository.hasChildrenRepeat()).isFalse()

    val count = MinshawiJuz30Repository.getAllVersesCount(context)
    assertThat(count).isEqualTo(564)

    val allVerses = MinshawiJuz30Repository.getAllVerses(context).toList()
    assertThat(allVerses.first().surah).isEqualTo(78)
    assertThat(allVerses.last().surah).isEqualTo(114)
    assertThat(allVerses.first().audioUrl).contains("Husary_Muallim_128kbps")

    val naba37 = MinshawiJuz30Repository.getVerse(context, 78, 37)
    assertThat(naba37).isNotNull()
    assertThat(naba37!!.turns.size).isEqualTo(1)

    val naba38 = MinshawiJuz30Repository.getVerse(context, 78, 38)
    assertThat(naba38).isNotNull()
    assertThat(naba38!!.turns.size).isEqualTo(2)
    assertThat(naba38.turns[1].effectiveContiguousPlayback).isTrue()

    for (lesson in allVerses) {
      assertThat(lesson.turns).isNotEmpty()
      for (turn in lesson.turns) {
        assertThat(turn.effectiveQariEndMs).isGreaterThan(turn.effectiveQariStartMs)
        assertThat(turn.effectiveKidsEndMs).isGreaterThan(turn.effectiveKidsStartMs)
      }
    }

    // Switching to Minshawi Teacher loads the repaired v3 dataset aligned to Husary Muallim's stops
    MinshawiJuz30Repository.setActiveTeacherQari(context, KidsTeacherQari.MINSHAWI_TEACHER)
    assertThat(MinshawiJuz30Repository.hasChildrenRepeat()).isTrue()
    val minshawiNaba37 = MinshawiJuz30Repository.getVerse(context, 78, 37)
    assertThat(minshawiNaba37).isNotNull()
    assertThat(minshawiNaba37!!.audioUrl).contains("Minshawy_Teacher_128kbps")
    assertThat(minshawiNaba37.turns.size).isEqualTo(1)

    // Restore default Husary Muallim for subsequent tests
    MinshawiJuz30Repository.setActiveTeacherQari(context, KidsTeacherQari.HUSARY_MUALLIM)
  }

  @Test
  fun testNeMoMelPreprocessorNoSpeechAndBesselNormalization() {
    val silent = FloatArray(16000) { 0.0002f }
    assertThat(NeMoMelPreprocessor.process(silent))
      .isEqualTo(NeMoMelPreprocessor.PreprocessResult.NoSpeech)

    val tinyBurst = FloatArray(1200) { i -> (0.25 * sin(2.0 * Math.PI * 220.0 * i / 16000.0)).toFloat() }
    assertThat(NeMoMelPreprocessor.process(tinyBurst))
      .isEqualTo(NeMoMelPreprocessor.PreprocessResult.NoSpeech)

    val speechSamples = FloatArray(16000) { i ->
      if (i in 1600 until 14400) {
        val env = 0.5 + 0.4 * sin(2.0 * Math.PI * 3.0 * i / 16000.0)
        (env * (0.35 * sin(2.0 * Math.PI * 210.0 * i / 16000.0) +
          0.20 * sin(2.0 * Math.PI * 420.0 * i / 16000.0) +
          0.12 * sin(2.0 * Math.PI * 1150.0 * i / 16000.0))).toFloat()
      } else {
        0f
      }
    }

    val res = NeMoMelPreprocessor.process(speechSamples)
    assertThat(res).isInstanceOf(NeMoMelPreprocessor.PreprocessResult.MelFeatures::class.java)
    val mel = res as NeMoMelPreprocessor.PreprocessResult.MelFeatures

    assertThat(mel.numFrames).isGreaterThan(120)
    assertThat(mel.features.size).isEqualTo(80 * mel.numFrames)

    assertThat(NeMoMelPreprocessor.MEL_BASIS_80X257.size).isEqualTo(80)
    assertThat(NeMoMelPreprocessor.MEL_BASIS_80X257[0].size).isEqualTo(257)

    val tMel = mel.numFrames
    for (m in listOf(0, 15, 40, 79)) {
      val offset = m * tMel
      var mean = 0.0
      for (t in 0 until tMel) {
        mean += mel.features[offset + t].toDouble()
      }
      mean /= tMel.toDouble()
      assertThat(abs(mean)).isLessThan(1e-4)

      var varSum = 0.0
      for (t in 0 until tMel) {
        val d = mel.features[offset + t].toDouble() - mean
        varSum += d * d
      }
      val sampleStd = sqrt(varSum / (tMel - 1).toDouble())
      assertThat(sampleStd).isWithin(0.02).of(1.0)
    }

    // Also verify rolling Tasmee' window preprocessor skips silent trailing hops and processes live hops
    val rollingSilentTail = speechSamples + FloatArray(12000) { 0f }
    assertThat(NeMoMelPreprocessor.processRollingTasmeeWindow(rollingSilentTail))
      .isEqualTo(NeMoMelPreprocessor.PreprocessResult.NoSpeech)

    val rollingActiveTail = FloatArray(16000) { 0f } + speechSamples
    assertThat(NeMoMelPreprocessor.processRollingTasmeeWindow(rollingActiveTail))
      .isInstanceOf(NeMoMelPreprocessor.PreprocessResult.MelFeatures::class.java)
  }

  @Test
  fun testTwoTierGraderRasmMakharijTashkeelAndMissingWords() {
    assertThat(QuranRecitationGrader.phoneticRasm("ٱلسَّمَٰوَٰتِ"))
      .isEqualTo(QuranRecitationGrader.phoneticRasm("السَّماواتِ"))
    assertThat(QuranRecitationGrader.phoneticRasm("ٱلرَّحْمَٰنِ"))
      .isEqualTo(QuranRecitationGrader.phoneticRasm("الرَّحْمانِ"))

    assertThat(QuranRecitationGrader.matchesPhoneticRasm("سِرَاطَ", "صِرَاطَ", "صِرَاطَ")).isFalse()
    assertThat(QuranRecitationGrader.matchesPhoneticRasm("الدَّالِّينَ", "الضَّالِّينَ", "الضَّالِّينَ")).isFalse()

    assertThat(QuranRecitationGrader.findTashkeelMismatch("صِرَاطُ", "صِرَاطَ", "صِرَاطَ")).isTrue()
    assertThat(QuranRecitationGrader.findTashkeelMismatch("عَلَيْهُمْ", "عَلَيْهِمْ", "عَلَيْهِمْ")).isTrue()
    assertThat(QuranRecitationGrader.findTashkeelMismatch("السَّمَوَتِ", "السَّماواتِ", "السَّمَاوَاتِ")).isFalse()

    val turn = TurnSpec(
      turnIndex = 0,
      totalTurns = 1,
      wordStartIdx = 0,
      wordEndIdx = 4,
      qariStartSec = 0.0,
      qariEndSec = 4.0,
      kidsStartSec = 4.0,
      kidsEndSec = 8.0,
      contiguousPlayback = true,
      cutReason = "TEST",
      uthmaniWords = listOf("صِرَٰطَ", "ٱلَّذِينَ", "أَنْعَمْتَ", "عَلَيْهِمْ"),
      normalizedWords = listOf("صِرَاطَ", "الَّذِينَ", "أَنْعَمْتَ", "عَلَيْهِمْ"),
      qariOnnxWords = listOf("صِرَاطَ", "الَّذِينَ", "أَنْعَمْتَ", "عَلَيْهِمْ"),
      tokenIds = listOf(10, 11, 12, 13)
    )

    val highConf = floatArrayOf(0.95f, 0.95f, 0.95f, 0.95f)

    val perfect = QuranRecitationGrader.gradeDecodedWords(
      suraAyah = SuraAyah(1, 7),
      turn = turn,
      hypWords = listOf("صِرَاطَ", "الَّذِينَ", "أَنْعَمْتَ", "عَلَيْهِمْ"),
      wordConfidences = highConf
    )
    assertThat(perfect.passed).isTrue()
    assertThat(perfect.scorePercent).isGreaterThan(95.0)
    assertThat(perfect.wordGrades.map { it.status }).containsExactly(
      WordGradeStatus.CORRECT,
      WordGradeStatus.CORRECT,
      WordGradeStatus.CORRECT,
      WordGradeStatus.CORRECT
    ).inOrder()

    val mixed = QuranRecitationGrader.gradeDecodedWords(
      suraAyah = SuraAyah(1, 7),
      turn = turn,
      hypWords = listOf("سِرَاطَ", "الَّذِينَ", "عَلَيْهُمْ"),
      wordConfidences = highConf
    )
    assertThat(mixed.wordGrades[0].status).isEqualTo(WordGradeStatus.WORD_ERROR)
    assertThat(mixed.wordGrades[1].status).isEqualTo(WordGradeStatus.CORRECT)
    assertThat(mixed.wordGrades[2].status).isEqualTo(WordGradeStatus.MISSING)
    assertThat(mixed.wordGrades[3].status).isEqualTo(WordGradeStatus.TASHKEEL_ERROR)
    assertThat(mixed.passed).isFalse()
  }

  @Test
  fun testGreedyCtcDecodeAndVocabParser() {
    val vocabJson = """
      {"<unk>": 0, "▁عَمَّ": 1, "▁يَتَسَاءَلُونَ": 2}
    """.trimIndent()
    val vocab = FastConformerAsrEngine.parseVocabJson(vocabJson)
    assertThat(vocab[1]).isEqualTo("▁عَمَّ")
    assertThat(vocab[2]).isEqualTo("▁يَتَسَاءَلُونَ")

    val b = FastConformerAsrEngine.BLANK_ID
    val frameIds = intArrayOf(b, 1, 1, b, 2, 2, b)
    val syntheticLogProbs = Array(frameIds.size) { f ->
      FloatArray(FastConformerAsrEngine.VOCAB_SIZE) { id -> if (id == frameIds[f]) -0.05f else -20.0f }
    }

    val decoded = QuranRecitationGrader.greedyCtcDecode(syntheticLogProbs, vocab)
    assertThat(decoded).containsExactly("عَمَّ", "يَتَسَاءَلُونَ").inOrder()
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun testKidsClassroomHighlightsRenderDirectlyOnTajweedMushafPage() {
    val quranInfo = QuranInfo(MadaniDataSource())
    val targetAyah = KidsClassroomManager.enableClassroomMode(
      context = context,
      currentPage = 582,
      selectedAyah = SuraAyah(78, 1),
      quranInfo = quranInfo
    )
    assertThat(targetAyah).isEqualTo(SuraAyah(78, 1))
    assertThat(KidsClassroomManager.isEnabled()).isTrue()

    val pageBmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
    pageBmp.eraseColor(Color.TRANSPARENT)
    for (y in 55..75) {
      for (x in 240..340) pageBmp.setPixel(x, y, Color.rgb(210, 35, 35))
      for (x in 90..190) pageBmp.setPixel(x, y, Color.rgb(20, 140, 60))
    }

    val ay1 = SuraAyah(78, 1)
    val glyphs = listOf(
      GlyphCoords(WordGlyph(ay1, 1, 1), 1, RectF(220f, 40f, 360f, 95f)),
      GlyphCoords(WordGlyph(ay1, 2, 2), 1, RectF(70f, 40f, 210f, 95f)),
      GlyphCoords(AyahEndGlyph(ay1, 3), 1, RectF(20f, 40f, 65f, 95f))
    )
    val pageGlyphs = PageGlyphsCoords(582, glyphs)
    val ayahCoords = AyahCoordinates(
      582,
      mapOf("78:1" to listOf(AyahBounds(1, 1, RectF(20f, 40f, 360f, 95f)))),
      pageGlyphs,
      emptyMap(),
      0f
    )

    val imageView = HighlightingImageView(context)
    imageView.setImageDrawable(BitmapDrawable(context.resources, pageBmp))
    imageView.setPageData(
      PageCoordinates(582, RectF(0f, 0f, 400f, 200f), emptyList(), emptyList()),
      emptySet()
    )
    imageView.setAyahData(ayahCoords)
    imageView.measure(
      View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
    )
    imageView.layout(0, 0, 400, 200)

    val outBmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
    outBmp.eraseColor(Color.WHITE)
    imageView.draw(Canvas(outBmp))

    val outsidePixel = outBmp.getPixel(290, 150)
    assertThat(Color.red(outsidePixel)).isEqualTo(255)
    assertThat(Color.green(outsidePixel)).isEqualTo(255)
    assertThat(Color.blue(outsidePixel)).isEqualTo(255)

    val glyphPixel = outBmp.getPixel(290, 65)
    assertThat(Color.red(glyphPixel)).isGreaterThan(Color.blue(glyphPixel) + 80)
  }

  @Test
  fun testPlayAndReciteButtonDoesNotCrashAndEntersQariStage() {
    val quranInfo = QuranInfo(MadaniDataSource())
    // Create placeholder core files in test filesDir so startThreeStageLoopForCurrentTurn runs full audio path
    val onnxFile = KidsAsrAssetDownloader.getOnnxModelFile(context)
    val vocabFile = KidsAsrAssetDownloader.getVocabFile(context)
    java.io.RandomAccessFile(onnxFile, "rw").use { it.setLength(121_000_000L) }
    vocabFile.writeText("{\"<unk>\":0}" + " ".repeat(6000))

    try {
      org.robolectric.Shadows.shadowOf(context as android.app.Application)
        .grantPermissions(android.Manifest.permission.RECORD_AUDIO)

      KidsClassroomManager.enableClassroomMode(
        context = context,
        currentPage = 582,
        selectedAyah = SuraAyah(78, 1),
        quranInfo = quranInfo
      )

      val overlay = KidsClassroomOverlayView(context)
      KidsClassroomManager.startThreeStageLoopForCurrentTurn(context)
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

      val state = KidsClassroomManager.currentState()
      assertThat(state.isEnabled).isTrue()
      assertThat(state.practiceMode).isEqualTo(KidsPracticeMode.LISTEN_AND_REPEAT)
      assertThat(state.stage).isEqualTo(ClassroomStage.QARI_RECITING)
      assertThat(overlay.visibility).isEqualTo(View.VISIBLE)
    } finally {
      KidsClassroomManager.disableClassroomMode(context)
      onnxFile.delete()
      vocabFile.delete()
    }
  }

  @Test
  fun testReciteOnlyModeDoesNotStopWhenNotPassingAndPromptsRepeatAgain() {
    val quranInfo = QuranInfo(MadaniDataSource())
    val onnxFile = KidsAsrAssetDownloader.getOnnxModelFile(context)
    val vocabFile = KidsAsrAssetDownloader.getVocabFile(context)
    java.io.RandomAccessFile(onnxFile, "rw").use { it.setLength(121_000_000L) }
    vocabFile.writeText("{\"<unk>\":0}" + " ".repeat(6000))

    try {
      org.robolectric.Shadows.shadowOf(context as android.app.Application)
        .grantPermissions(android.Manifest.permission.RECORD_AUDIO)

      KidsClassroomManager.enableClassroomMode(
        context = context,
        currentPage = 582,
        selectedAyah = SuraAyah(78, 1),
        quranInfo = quranInfo
      )
      KidsClassroomManager.startReciteOnlyForCurrentTurn(context)
      assertThat(KidsClassroomManager.currentState().practiceMode).isEqualTo(KidsPracticeMode.RECITE_ONLY)
      assertThat(KidsClassroomManager.currentState().stage).isEqualTo(ClassroomStage.CHILD_RECITING)

      val turn = KidsClassroomManager.currentState().currentTurn!!
      val failedEval = TurnEvaluationResult(
        suraAyah = SuraAyah(78, 1),
        turnIndex = 0,
        totalTurns = 1,
        scorePercent = 52.0,
        wordAccuracy = 50.0,
        tashkeelAccuracy = 60.0,
        confidenceScore = 50.0,
        passed = false,
        noSpeechDetected = false,
        decodedTranscript = "عم",
        wordGrades = listOf(
          WordGrade(0, 1, turn.uthmaniWords[0], turn.normalizedWords[0], WordGradeStatus.CORRECT, 0.9f, "عم"),
          WordGrade(1, 2, turn.uthmaniWords[1], turn.normalizedWords[1], WordGradeStatus.MISSING, 0f, null)
        )
      )

      KidsClassroomManager.applyTurnEvaluationResult(context, failedEval)

      val retryState = KidsClassroomManager.currentState()
      assertThat(retryState.stage).isEqualTo(ClassroomStage.TURN_RETRY)
      assertThat(retryState.practiceMode).isEqualTo(KidsPracticeMode.RECITE_ONLY)
      // Must NOT stop — auto-transition is pending to ask the reader to repeat again!
      assertThat(retryState.isAutoTransitionPending).isTrue()
      assertThat(retryState.isRetryAttempt).isTrue()
    } finally {
      KidsClassroomManager.disableClassroomMode(context)
      onnxFile.delete()
      vocabFile.delete()
    }
  }

  @Test
  fun testElevatedSilenceThresholdRejectsSubtleNoises() {
    // Subtle room / AC noise at RMS ~ 0.015f..0.022f must stay strictly below threshold
    val threshold = KidsClassroomAudioController.computeEffectiveVoiceThreshold(ambientNoiseRms = 0.010f)
    assertThat(threshold).isAtLeast(0.028f)
    assertThat(0.018f).isLessThan(threshold)

    // Subtle noise buffer (peak RMS ~ 0.014f) is rejected as NoSpeech by KidsClassroom minPeakRms
    val subtleNoise = FloatArray(16000) { i ->
      (0.019 * sin(2.0 * Math.PI * 180.0 * i / 16000.0)).toFloat()
    }
    val prep = NeMoMelPreprocessor.process(
      pcmFloat32 = subtleNoise,
      minPeakRms = KidsClassroomManager.KIDS_MIN_SPEECH_PEAK_RMS
    )
    assertThat(prep).isEqualTo(NeMoMelPreprocessor.PreprocessResult.NoSpeech)
  }

  @Test
  fun testMinshawiFullV2NaturalSchemaNullableFieldsBomAndEmptyTurnSanitization() {
    val allSurahs = MinshawiJuz30Repository.getAllSurahs(context)
    assertThat(allSurahs).hasSize(114)
    assertThat(allSurahs.first().surahNumber).isEqualTo(1)
    assertThat(allSurahs.last().surahNumber).isEqualTo(114)
    assertThat(allSurahs.sumOf { it.ayahCount }).isEqualTo(6236)

    val sampleV2Json = """
      {
        "schema": "tilawi-minshawi-teacher-v2-natural",
        "stream_base_url": "[https://everyayah.com/data/Minshawy_Teacher_128kbps/](https://everyayah.com/data/Minshawy_Teacher_128kbps/)",
        "juz": "all",
        "surah_range": [1, 114],
        "total_ayat": 6236,
        "max_speaker_sec": null,
        "verses": {
          "1:1": {
            "surah": 1,
            "ayah": 1,
            "surah_name_ar": "\ufeffالفاتحة",
            "uthmani_full": "\ufeffبِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
            "audio_file": "001001.mp3",
            "audio_url": "[https://everyayah.com/data/Minshawy_Teacher_128kbps/001001.mp3](https://everyayah.com/data/Minshawy_Teacher_128kbps/001001.mp3)",
            "file_duration_sec": 9.80,
            "total_turns": 1,
            "next_surah": 1,
            "next_ayah": 2,
            "turns": [{
              "turn_index": 0,
              "total_turns": 1,
              "word_start_idx": 0,
              "word_end_idx": 4,
              "qari_start_sec": 0.0,
              "qari_end_sec": 4.90,
              "kids_start_sec": 4.90,
              "kids_end_sec": 9.80,
              "contiguous_playback": true,
              "cut_reason": "NATURAL_FULL_AYAH",
              "uthmani_words": ["\ufeffبِسْمِ", "ٱللَّهِ", "ٱلرَّحْمَٰنِ", "ٱلرَّحِيمِ"],
              "normalized_words": ["بِسْمِ", "اللَّهِ", "الرَّحْمَانِ", "الرَّحِيمِ"],
              "qari_onnx_words": ["بِسْمِ", "اللَّهِ", "الرَّحْمَانِ", "الرَّحِيمِ"],
              "token_ids": [11, 12, 13, 14]
            }]
          },
          "2:1": {
            "surah": 2,
            "ayah": 1,
            "surah_name_ar": "البقرة",
            "uthmani_full": "الٓمٓ",
            "audio_file": "002001.mp3",
            "audio_url": "https://everyayah.com/data/Minshawy_Teacher_128kbps/002001.mp3",
            "file_duration_sec": 18.4,
            "total_turns": 2,
            "next_surah": 2,
            "next_ayah": 2,
            "turns": [
              {
                "turn_index": 0,
                "total_turns": 2,
                "word_start_idx": 0,
                "word_end_idx": 0,
                "qari_start_sec": 0.0,
                "qari_end_sec": 4.5,
                "kids_start_sec": 4.5,
                "kids_end_sec": 9.0,
                "contiguous_playback": true,
                "cut_reason": "ISTIADHAH",
                "uthmani_words": [],
                "normalized_words": [],
                "qari_onnx_words": [],
                "token_ids": []
              },
              {
                "turn_index": 1,
                "total_turns": 2,
                "word_start_idx": 0,
                "word_end_idx": 1,
                "qari_start_sec": 9.0,
                "qari_end_sec": 13.7,
                "kids_start_sec": 13.7,
                "kids_end_sec": 18.4,
                "contiguous_playback": true,
                "cut_reason": "NATURAL_FULL_AYAH",
                "uthmani_words": ["الٓمٓ"],
                "normalized_words": ["الم"],
                "qari_onnx_words": ["أَلِفْ لَامْ مِيمْ"],
                "token_ids": [101, 102, 103]
              }
            ]
          }
        }
      }
    """.trimIndent()

    val manifest = MinshawiJuz30Repository.parseManifestOnly(sampleV2Json)
    assertThat(manifest.schema).isEqualTo("tilawi-minshawi-teacher-v2-natural")
    assertThat(manifest.juz).isEqualTo("all")
    assertThat(manifest.maxSpeakerSec).isNull()
    assertThat(manifest.surahRange).containsExactly(1, 114).inOrder()
    assertThat(manifest.totalAyat).isEqualTo(6236)
    assertThat(manifest.streamBaseUrl).isEqualTo("https://everyayah.com/data/Minshawy_Teacher_128kbps/")

    val fatiha1 = manifest.verses["1:1"]!!
    assertThat(fatiha1.surahNameAr).isEqualTo("الفاتحة")
    assertThat(fatiha1.audioUrl).isEqualTo("https://everyayah.com/data/Minshawy_Teacher_128kbps/001001.mp3")
    assertThat(fatiha1.turns[0].uthmaniWords[0]).isEqualTo("بِسْمِ")

    val baqara1 = manifest.verses["2:1"]!!
    assertThat(baqara1.turns).hasSize(1)
    assertThat(baqara1.totalTurns).isEqualTo(1)
    assertThat(baqara1.turns[0].turnIndex).isEqualTo(0)
    assertThat(baqara1.turns[0].uthmaniWords).containsExactly("الٓمٓ")
  }

  @Test
  fun testKidsAssetDownloaderVocabBundleAndDynamicStage3Timeout() {
    val vocabWithExplicitBlank = """
      {"<unk>": 0, "▁بِسْمِ": 10, "▁اللَّهِ": 25, "<blk>": 1024}
    """.trimIndent()
    val bundle1 = KidsAssetDownloader.parseVocabBundle(vocabWithExplicitBlank)
    assertThat(bundle1.ctcTokens.size).isEqualTo(1025)
    assertThat(bundle1.ctcTokens[10]).isEqualTo("▁بِسْمِ")
    assertThat(bundle1.ctcTokens[25]).isEqualTo("▁اللَّهِ")
    assertThat(bundle1.blankId).isEqualTo(1024)

    val vocabWithoutBlankKey = """
      {"<unk>": 0, "▁قُلْ": 5}
    """.trimIndent()
    val bundle2 = KidsAssetDownloader.parseVocabBundle(vocabWithoutBlankKey)
    assertThat(bundle2.ctcTokens.size).isEqualTo(1025)
    assertThat(bundle2.blankId).isEqualTo(1024)

    val shortTurn = TurnSpec(
      turnIndex = 0, totalTurns = 1, wordStartIdx = 0, wordEndIdx = 2,
      qariStartSec = 0.0, qariEndSec = 3.5, kidsStartSec = 3.5, kidsEndSec = 7.0,
      contiguousPlayback = true, cutReason = "SHORT",
      uthmaniWords = listOf("عَمَّ", "يَتَسَآءَلُونَ"),
      normalizedWords = listOf("عَمَّ", "يَتَسَاءَلُونَ"),
      qariOnnxWords = listOf("عَمَّ", "يَتَسَاءَلُونَ"),
      tokenIds = listOf(1, 2)
    )
    assertThat(shortTurn.computeChildMaxTimeoutSec()).isWithin(0.001f).of(12.0f)

    val longTurn = shortTurn.copy(qariStartSec = 2.0, qariEndSec = 10.0) // 8.0s * 1.6 + 4.0 = 16.8s
    assertThat(longTurn.computeChildMaxTimeoutSec()).isWithin(0.01f).of(16.8f)
  }

  @Test
  fun testKidsTasmeeRevealMatcherMonotonicRulesAndRepeatedWordProtection() {
    val spec = KidsTasmeeRevealMatcher.AyahRevealSpec(
      uthmaniWords = listOf("عَمَّ", "يَتَسَآءَلُونَ", "عَنِ", "ٱلنَّبَإِ", "ٱلْعَظِيمِ"),
      normalizedWords = listOf("عَمَّ", "يَتَسَاءَلُونَ", "عَنِ", "النَّبَإِ", "الْعَظِيمِ"),
      qariOnnxWords = listOf("عَمَّ", "يَتَسَاءَلُونَ", "عَنِ", "النَّبَإِ", "الْعَظِيمِ"),
      wordTokenIds = listOf(
        intArrayOf(520, 7),
        intArrayOf(80, 270, 5),
        intArrayOf(90),
        intArrayOf(110, 111),
        intArrayOf(210, 211)
      )
    )
    val emptyLogprobs = emptyArray<FloatArray>()

    // Rule 1a: Exact Rasm match on word 0 ("عَمَّ") -> revealedCount = 1
    val rev1 = KidsTasmeeRevealMatcher.computeNextRevealedCount(
      spec = spec,
      currentRevealedCount = 0,
      hypWords = listOf("عم"),
      logprobs = emptyLogprobs
    )
    assertThat(rev1).isEqualTo(1)

    // Rule 1b: >= 3-letter Rasm prefix on the last spoken word ("يتسا" for "يَتَسَاءَلُونَ") -> revealedCount = 2
    val rev2 = KidsTasmeeRevealMatcher.computeNextRevealedCount(
      spec = spec,
      currentRevealedCount = 1,
      hypWords = listOf("عم", "يتسا"),
      logprobs = emptyLogprobs
    )
    assertThat(rev2).isEqualTo(2)

    // Rule 2: Lookahead `revealedCount + 1` (index 3 = "النَّبَإِ", >= 3 Rasm letters) spoken when index 2 ("عَنِ")
    // was slurred -> reveals BOTH index 2 and index 3 (revealedCount = 4)
    val rev4 = KidsTasmeeRevealMatcher.computeNextRevealedCount(
      spec = spec,
      currentRevealedCount = 2,
      hypWords = listOf("النبإ"),
      logprobs = emptyLogprobs
    )
    assertThat(rev4).isEqualTo(4)

    // Rule 3: Mean peak probability exp(logprobs[t][tid]) >= 0.58f for first 2 BPE token_ids (210, 211)
    // in the trailing 35% of frames (t in 65 until 100)
    val lowTailLogprobs = Array(100) { t ->
      FloatArray(1025) { tid ->
        // Put high prob ONLY in early frames (t < 65), low prob in trailing 35% -> must NOT reveal
        if (t < 50 && (tid == 210 || tid == 211)) -0.1f else -15f
      }
    }
    assertThat(
      KidsTasmeeRevealMatcher.computeNextRevealedCount(
        spec = spec,
        currentRevealedCount = 4,
        hypWords = emptyList(),
        logprobs = lowTailLogprobs
      )
    ).isEqualTo(4)

    val highTailLogprobs = Array(100) { t ->
      FloatArray(1025) { tid ->
        if (t == 80 && tid == 210) ln(0.68f)
        else if (t == 88 && tid == 211) ln(0.62f) // mean = 0.65f >= 0.58f
        else -15f
      }
    }
    assertThat(
      KidsTasmeeRevealMatcher.computeNextRevealedCount(
        spec = spec,
        currentRevealedCount = 4,
        hypWords = emptyList(),
        logprobs = highTailLogprobs
      )
    ).isEqualTo(5)

    // Repeated consecutive words protection (e.g. 89:21 "دَكًّا دَكًّا"):
    // Single spoken "دكا" in the rolling window after word 0 is already revealed must NOT reveal word 1
    // until spoken a second time or after a silence gap.
    val repeatedSpec = KidsTasmeeRevealMatcher.AyahRevealSpec(
      uthmaniWords = listOf("دَكًّا", "دَكًّا"),
      normalizedWords = listOf("دَكًّا", "دَكًّا"),
      qariOnnxWords = listOf("دَكًّا", "دَكًّا"),
      wordTokenIds = listOf(intArrayOf(301), intArrayOf(301))
    )
    assertThat(
      KidsTasmeeRevealMatcher.computeNextRevealedCount(
        spec = repeatedSpec,
        currentRevealedCount = 1,
        hypWords = listOf("دكا"),
        logprobs = emptyLogprobs,
        hadSilenceGapSinceLastReveal = false
      )
    ).isEqualTo(1)

    assertThat(
      KidsTasmeeRevealMatcher.computeNextRevealedCount(
        spec = repeatedSpec,
        currentRevealedCount = 1,
        hypWords = listOf("دكا", "دكا"),
        logprobs = emptyLogprobs,
        hadSilenceGapSinceLastReveal = false
      )
    ).isEqualTo(2)
  }

  @Test
  fun testSubModeToggleSwitchesCleanlyBetweenTeacherRepeatAndStreamingTasmee() {
    val onnxFile = KidsAssetDownloader.getOnnxModelFile(context)
    val vocabFile = KidsAssetDownloader.getVocabFile(context)
    java.io.RandomAccessFile(onnxFile, "rw").use { it.setLength(121_000_000L) }
    vocabFile.writeText("{\"<unk>\":0,\"<blk>\":1024}" + " ".repeat(6000))

    try {
      org.robolectric.Shadows.shadowOf(context as android.app.Application)
        .grantPermissions(android.Manifest.permission.RECORD_AUDIO)

      KidsClassroomManager.ensureKidsTabInitialized(context)
      KidsClassroomManager.selectAyahAndTurn(context, SuraAyah(78, 1))

      KidsClassroomManager.setPracticeMode(context, KidsPracticeMode.RECITE_AND_REVEAL)
      val modeBIdle = KidsClassroomManager.currentState()
      assertThat(modeBIdle.practiceMode).isEqualTo(KidsPracticeMode.RECITE_AND_REVEAL)
      assertThat(modeBIdle.revealedCount).isEqualTo(0)
      assertThat(modeBIdle.ayahWords).containsExactly("عَمَّ", "يَتَسَآءَلُونَ").inOrder()

      KidsClassroomManager.startReciteAndRevealStreaming(context)
      assertThat(KidsClassroomManager.currentState().stage).isEqualTo(ClassroomStage.TASMEE_STREAMING)

      // Switching back to Mode A stops streaming and resets to Teacher & Kids Repeat
      KidsClassroomManager.setPracticeMode(context, KidsPracticeMode.LISTEN_AND_REPEAT)
      val modeAState = KidsClassroomManager.currentState()
      assertThat(modeAState.practiceMode).isEqualTo(KidsPracticeMode.LISTEN_AND_REPEAT)
      assertThat(modeAState.stage).isEqualTo(ClassroomStage.IDLE)
      assertThat(KidsClassroomManager.isMicrophoneRecordingForTest()).isFalse()
    } finally {
      KidsClassroomManager.disableClassroomMode(context)
      onnxFile.delete()
      vocabFile.delete()
    }
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun testVadWaveformVisualizerMathViewAndImmediateMicReleaseOnStop() {
    val history = FloatArray(com.quran.labs.androidquran.memorization.tasmee.VadWaveformMath.DEFAULT_BAR_COUNT)
    val voicedSamples = FloatArray(640) { i ->
      (0.32 * sin(2.0 * Math.PI * 240.0 * i / 16000.0)).toFloat()
    }
    val activeBars = com.quran.labs.androidquran.memorization.tasmee.VadWaveformMath.updateBarsFromFloatChunk(
      history = history,
      samples = voicedSamples,
      sampleCount = voicedSamples.size,
      isVadActive = true
    )
    assertThat(activeBars).hasSize(16)
    assertThat(activeBars.maxOrNull() ?: 0f).isGreaterThan(0.35f)

    val silentSamples = FloatArray(640) { 0f }
    val quietBars = com.quran.labs.androidquran.memorization.tasmee.VadWaveformMath.updateBarsFromFloatChunk(
      history = FloatArray(16),
      samples = silentSamples,
      sampleCount = silentSamples.size,
      isVadActive = false
    )
    assertThat(quietBars.maxOrNull() ?: 1f).isLessThan(0.10f)

    // Verify VadWaveformView draws cleanly in both VAD-active and idle states
    val waveformView = VadWaveformView(context)
    waveformView.measure(
      View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY)
    )
    waveformView.layout(0, 0, 320, 40)
    waveformView.updateWaveformState(vadActive = true, rmsLevel = 0.12f, waveformBars = activeBars)
    val bmp = Bitmap.createBitmap(320, 40, Bitmap.Config.ARGB_8888)
    waveformView.draw(Canvas(bmp))
    waveformView.updateWaveformState(vadActive = false, rmsLevel = 0f, waveformBars = emptyList())
    waveformView.draw(Canvas(bmp))

    // Verify stopping LiveTasmeeManager or KidsClassroomManager immediately resets state & releases mic
    val quranInfo = QuranInfo(MadaniDataSource())
    val onnxFile = KidsAssetDownloader.getOnnxModelFile(context)
    val vocabFile = KidsAssetDownloader.getVocabFile(context)
    java.io.RandomAccessFile(onnxFile, "rw").use { it.setLength(121_000_000L) }
    vocabFile.writeText("{\"<unk>\":0,\"<blk>\":1024}" + " ".repeat(6000))
    try {
      org.robolectric.Shadows.shadowOf(context as android.app.Application)
        .grantPermissions(android.Manifest.permission.RECORD_AUDIO)

      com.quran.labs.androidquran.memorization.tasmee.LiveTasmeeManager.startTasmee(
        context = context,
        currentPage = 604,
        quranInfo = quranInfo
      )
      assertThat(com.quran.labs.androidquran.memorization.tasmee.LiveTasmeeManager.isEnabled()).isTrue()

      com.quran.labs.androidquran.memorization.tasmee.LiveTasmeeManager.stopTasmee()
      val stoppedTasmee = com.quran.labs.androidquran.memorization.tasmee.LiveTasmeeManager.currentState()
      assertThat(stoppedTasmee.isEnabled).isFalse()
      assertThat(stoppedTasmee.isVadActive).isFalse()

      KidsClassroomManager.ensureKidsTabInitialized(context)
      KidsClassroomManager.startReciteOnlyForCurrentTurn(context)
      assertThat(KidsClassroomManager.currentState().stage).isEqualTo(ClassroomStage.CHILD_RECITING)

      KidsClassroomManager.stopActiveLoop()
      assertThat(KidsClassroomManager.currentState().stage).isEqualTo(ClassroomStage.IDLE)
      assertThat(KidsClassroomManager.currentState().isVadActive).isFalse()
      assertThat(KidsClassroomManager.isMicrophoneRecordingForTest()).isFalse()
    } finally {
      com.quran.labs.androidquran.memorization.tasmee.LiveTasmeeManager.stopTasmee()
      com.quran.labs.androidquran.memorization.QuranMemorizationManager.setHiddenModeEnabled(false, 604)
      onnxFile.delete()
      vocabFile.delete()
    }
  }

  @Test
  fun testKidsTasmeeMultiWordCatchUpAndNonObscuringReaderDocks() {
    // 1. Verify KidsTasmeeRevealMatcher catches up across multiple missed immediate words
    val ayah26Spec = KidsTasmeeRevealMatcher.AyahRevealSpec(
      uthmaniWords = listOf(
        "إِنَّ", "ٱلَّذِينَ", "كَفَرُوا۟", "سَوَآءٌ", "عَلَيْهِمْ",
        "ءَأَنذَرْتَهُمْ", "أَمْ", "لَمْ", "تُنذِرْهُمْ", "لَا", "يُؤْمِنُونَ"
      ),
      normalizedWords = listOf(
        "إِنَّ", "الَّذِينَ", "كَفَرُوا", "سَوَاءٌ", "عَلَيْهِمْ",
        "أَأَنذَرْتَهُمْ", "أَمْ", "لَمْ", "تُنذِرْهُمْ", "لَا", "يُؤْمِنُونَ"
      ),
      qariOnnxWords = listOf(
        "إِنَّ", "الَّذِينَ", "كَفَرُوا", "سَوَاءٌ", "عَلَيْهِمْ",
        "أَأَنذَرْتَهُمْ", "أَمْ", "لَمْ", "تُنذِرْهُمْ", "لَا", "يُؤْمِنُونَ"
      ),
      wordTokenIds = List(11) { IntArray(0) },
      surah = 2,
      ayah = 6
    )

    // Model heard "إن" -> 1/11, then missed "الذين كفروا" and heard "سواء عليهم" -> reveals 5/11
    val revCatchUp1 = KidsTasmeeRevealMatcher.computeNextRevealedCount(
      spec = ayah26Spec,
      currentRevealedCount = 1,
      hypWords = listOf("إن", "سواء", "عليهم"),
      logprobs = emptyArray()
    )
    assertThat(revCatchUp1).isEqualTo(5)

    // Next hop still has ["إن", "سواء", "عليهم"] in window + misses 3 words + hears "تنذرهم" -> reveals 9/11
    val revCatchUp2 = KidsTasmeeRevealMatcher.computeNextRevealedCount(
      spec = ayah26Spec,
      currentRevealedCount = 5,
      hypWords = listOf("إن", "سواء", "عليهم", "تنذرهم"),
      logprobs = emptyArray()
    )
    assertThat(revCatchUp2).isEqualTo(9)

    // 2. Verify quran_page_activity.xml keeps top memorization_bar hidden and renders
    //    KidsClassroomOverlayView as a compact Play button so the Mushaf page gets > 90% height!
    val themedContext = android.view.ContextThemeWrapper(
      context,
      com.quran.labs.androidquran.R.style.QuranToolBar_Overlay
    )
    val root = android.view.LayoutInflater.from(themedContext)
      .inflate(com.quran.labs.androidquran.R.layout.quran_page_activity, null, false)
    val memBar = root.findViewById<View>(com.quran.labs.androidquran.R.id.memorization_bar)
    val pageArea = root.findViewById<View>(com.quran.labs.androidquran.R.id.page_area_container)
    val bottomDock = root.findViewById<android.view.ViewGroup>(com.quran.labs.androidquran.R.id.bottom_recitation_dock)
    val topDock = root.findViewById<android.view.ViewGroup>(com.quran.labs.androidquran.R.id.top_recitation_dock)
    val tasmeeBar = root.findViewById<View>(com.quran.labs.androidquran.R.id.tasmee_bottom_recording_bar)
    val kidsOverlay = root.findViewById<KidsClassroomOverlayView>(com.quran.labs.androidquran.R.id.kids_classroom_overlay)

    assertThat(memBar.visibility).isEqualTo(View.GONE)
    assertThat(pageArea).isNotNull()
    assertThat(bottomDock).isNotNull()
    assertThat(topDock).isNotNull()
    assertThat(tasmeeBar.parent).isSameInstanceAs(bottomDock)
    assertThat(kidsOverlay.parent).isSameInstanceAs(bottomDock)

    kidsOverlay.visibility = View.VISIBLE
    root.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY)
    )
    root.layout(0, 0, 1080, 2200)
    // page_area_container ends at or above bottom_recitation_dock.top -> 0px overlap,
    // and minimal Play button takes <= 160px out of 2200px so the Mushaf page stays full-screen!
    assertThat(pageArea.bottom).isAtMost(bottomDock.top)
    assertThat(kidsOverlay.height).isAtMost(160)
    assertThat(pageArea.height).isGreaterThan(1900)

    // Verify quran_menu.xml exposes both Live Tasmee' (Recite & Reveal) and Memorization Options sheet
    val popupMenu = android.widget.PopupMenu(context, View(context))
    popupMenu.menuInflater.inflate(com.quran.labs.androidquran.R.menu.quran_menu, popupMenu.menu)
    assertThat(popupMenu.menu.findItem(com.quran.labs.androidquran.R.id.memorization_live_tasmee)).isNotNull()
    assertThat(popupMenu.menu.findItem(com.quran.labs.androidquran.R.id.memorization_options_sheet)).isNotNull()
    // Cluttered memorization/kids items are hidden from the dropdown list while unified Options icon is visible
    assertThat(popupMenu.menu.findItem(com.quran.labs.androidquran.R.id.memorization_options_sheet).isVisible).isTrue()
    assertThat(popupMenu.menu.findItem(com.quran.labs.androidquran.R.id.children_learning_mode).isVisible).isFalse()
    assertThat(popupMenu.menu.findItem(com.quran.labs.androidquran.R.id.memorization_live_tasmee).isVisible).isFalse()
    assertThat(popupMenu.menu.findItem(com.quran.labs.androidquran.R.id.memorization_word_by_word).isVisible).isFalse()
    // Subtle 1-click options reveal button is present in quran_page_activity.xml
    val subtleRevealBtn = root.findViewById<View>(com.quran.labs.androidquran.R.id.subtle_options_reveal_btn)
    assertThat(subtleRevealBtn).isNotNull()
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun testPageFlipTransformerAndKidsMemorizationOpaqueTajweedWordHiding() {
    // 1. MushafPageFlipTransformer applies smooth spine-hinged 3D rotation and resets cleanly at 0f / 1f
    val transformer = com.quran.labs.androidquran.ui.helpers.MushafPageFlipTransformer()
    val pageView = View(context).apply {
      layout(0, 0, 1080, 1920)
    }
    transformer.transformPage(pageView, -0.5f)
    assertThat(pageView.rotationY).isGreaterThan(0f)
    assertThat(pageView.pivotX).isEqualTo(1080f)
    assertThat(pageView.alpha).isLessThan(1f)

    transformer.transformPage(pageView, 0.5f)
    assertThat(pageView.rotationY).isLessThan(0f)
    assertThat(pageView.pivotX).isEqualTo(0f)

    transformer.transformPage(pageView, 0f)
    assertThat(pageView.rotationY).isEqualTo(0f)
    assertThat(pageView.scaleX).isEqualTo(1f)
    assertThat(pageView.alpha).isEqualTo(1f)

    // 2. Kids Mode Memorization (enableWordHiding = true) hides Mushaf words — including on opaque-white
    //    Tajweed pages — and reveals the turn's words upon evaluation!
    val quranInfo = QuranInfo(MadaniDataSource())
    try {
      val target = KidsClassroomManager.enableClassroomMode(
        context = context,
        currentPage = 582,
        selectedAyah = SuraAyah(78, 1),
        quranInfo = quranInfo,
        enableWordHiding = true
      )
      assertThat(target).isEqualTo(SuraAyah(78, 1))
      assertThat(com.quran.labs.androidquran.memorization.QuranMemorizationManager.isHiddenModeEnabled()).isTrue()

      // Create an opaque-white Tajweed Mushaf bitmap (Color.WHITE background, !hasTransparentPaper)
      val opaqueTajweedBmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
      opaqueTajweedBmp.eraseColor(Color.WHITE)
      for (y in 55..75) {
        for (x in 240..340) opaqueTajweedBmp.setPixel(x, y, Color.rgb(20, 20, 20))
        for (x in 90..190) opaqueTajweedBmp.setPixel(x, y, Color.rgb(210, 35, 35))
      }

      val ay1 = SuraAyah(78, 1)
      val glyphs = listOf(
        GlyphCoords(WordGlyph(ay1, 1, 1), 1, RectF(220f, 40f, 360f, 95f)),
        GlyphCoords(WordGlyph(ay1, 2, 2), 1, RectF(70f, 40f, 210f, 95f)),
        GlyphCoords(AyahEndGlyph(ay1, 3), 1, RectF(20f, 40f, 65f, 95f))
      )
      val ayahCoords = AyahCoordinates(
        582,
        mapOf("78:1" to listOf(AyahBounds(1, 1, RectF(20f, 40f, 360f, 95f)))),
        PageGlyphsCoords(582, glyphs),
        emptyMap(),
        0f
      )

      val imageView = HighlightingImageView(context).apply {
        setImageDrawable(BitmapDrawable(context.resources, opaqueTajweedBmp))
        setPageData(
          PageCoordinates(582, RectF(0f, 0f, 400f, 200f), emptyList(), emptyList()),
          emptySet()
        )
        setAyahData(ayahCoords)
        measure(
          View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
        )
        layout(0, 0, 400, 200)
      }

      val outHidden = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
      outHidden.eraseColor(Color.WHITE)
      imageView.draw(Canvas(outHidden))

      // While hidden on opaque-white Tajweed page, word 1 & word 2 dark calligraphy (R=20) is masked
      // to paper-white with only the subtle translucent classroom pill tint (R≈238, G≈247, B≈247)!
      val hiddenWord1Px = outHidden.getPixel(290, 65)
      assertThat(Color.red(hiddenWord1Px)).isGreaterThan(225)
      assertThat(Color.green(hiddenWord1Px)).isGreaterThan(225)
      assertThat(Color.blue(hiddenWord1Px)).isGreaterThan(225)

      // Now simulate child completing recitation of Ayah 78:1 -> words reveal so calligraphy reappears!
      val lesson781 = MinshawiJuz30Repository.getVerse(context, 78, 1)!!
      val evalPassed = QuranRecitationGrader.gradeDecodedWords(
        suraAyah = SuraAyah(78, 1),
        turn = lesson781.turns.first(),
        hypWords = listOf("عَمَّ", "يَتَسَاءَلُونَ"),
        wordConfidences = floatArrayOf(0.96f, 0.96f)
      )
      KidsClassroomManager.applyTurnEvaluationResult(context, evalPassed)
      imageView.advanceRevealAnimationForTest(400L)

      val outRevealed = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
      outRevealed.eraseColor(Color.WHITE)
      imageView.draw(Canvas(outRevealed))
      val revealedWord1Px = outRevealed.getPixel(290, 65)
      assertThat(Color.red(revealedWord1Px)).isLessThan(120)
    } finally {
      KidsClassroomManager.disableClassroomMode(context)
      com.quran.labs.androidquran.memorization.QuranMemorizationManager.setHiddenModeEnabled(false, 582, quranInfo, context)
    }
  }
}

