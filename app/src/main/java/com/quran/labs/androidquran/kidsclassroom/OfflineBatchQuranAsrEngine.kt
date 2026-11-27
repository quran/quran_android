package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import com.quran.data.model.SuraAyah

/**
 * Final Stage-4 offline batch grading result (`fastconformer_quran_int8.onnx`, `4.72%` CTC WER)
 * enriched with kid-friendly star rating (`0..3`) and missed word indices for tap-to-Slow-Mo replay.
 */
data class OfflineGradingOutcome(
  val evaluation: TurnEvaluationResult,
  val starsEarned: Int,
  val missedWordIndices: List<Int>,
  val tashkeelHintWordIndices: List<Int>
)

/**
 * Runs a full-utterance pass on the Stage-4 Offline Batch INT8 model (`fastconformer_quran_int8.onnx`,
 * `4.72%` WER) using full-utterance CMVN ([UtteranceCmvnNormalizer]) at the end of a child's turn
 * to award accurate 1–3 star ratings and pin-point any word to replay in `0.75×` Slow-Mo.
 */
class OfflineBatchQuranAsrEngine {

  private val cmvnNormalizer = UtteranceCmvnNormalizer()

  val isReady: Boolean
    get() = FastConformerAsrEngine.isInitialized()

  suspend fun ensureLoaded(context: Context): Boolean =
    FastConformerAsrEngine.ensureInitialized(context)

  fun vocabArray(): Array<String>? = FastConformerAsrEngine.peekVocab()

  suspend fun gradeFullUtteranceFloat(
    context: Context,
    pcmFloat32: FloatArray,
    suraAyah: SuraAyah,
    turn: TurnSpec,
    minPeakRms: Float = NeMoMelPreprocessor.MIN_PEAK_RMS,
    passThresholdPercent: Double = 65.0,
    minWordAccuracy: Double = 0.65
  ): OfflineGradingOutcome {
    val prep = cmvnNormalizer.extractNormalizedFloat(pcmFloat32, minPeakRms = minPeakRms)
    val evalResult = when (prep) {
      is NeMoMelPreprocessor.PreprocessResult.NoSpeech -> {
        QuranRecitationGrader.buildNoSpeechResult(suraAyah, turn)
      }
      is NeMoMelPreprocessor.PreprocessResult.MelFeatures -> {
        val asrOut = FastConformerAsrEngine.runInference(
          context = context,
          melFeatures = prep.features,
          numFrames = prep.numFrames
        )
        if (asrOut == null) {
          QuranRecitationGrader.buildNoSpeechResult(suraAyah, turn)
        } else {
          QuranRecitationGrader.evaluateTurn(
            suraAyah = suraAyah,
            turn = turn,
            logprobs = asrOut.logprobs,
            vocab = asrOut.vocab,
            blankId = asrOut.blankId,
            passThresholdPercent = passThresholdPercent,
            minWordAccuracy = minWordAccuracy
          )
        }
      }
    }

    val stars = RewardsDataStore.computeStarsFromGrade(evalResult)
    val missed = evalResult.wordGrades.mapNotNull { g ->
      if (g.status == WordGradeStatus.WORD_ERROR || g.status == WordGradeStatus.MISSING) {
        g.wordIndexInTurn
      } else {
        null
      }
    }
    val tashkeelHints = evalResult.wordGrades.mapNotNull { g ->
      if (g.status == WordGradeStatus.TASHKEEL_ERROR) g.wordIndexInTurn else null
    }
    return OfflineGradingOutcome(
      evaluation = evalResult,
      starsEarned = stars,
      missedWordIndices = missed,
      tashkeelHintWordIndices = tashkeelHints
    )
  }

  suspend fun gradeFullUtterance(
    context: Context,
    pcm16k: ShortArray,
    suraAyah: SuraAyah,
    turn: TurnSpec,
    minPeakRms: Float = NeMoMelPreprocessor.MIN_PEAK_RMS,
    passThresholdPercent: Double = 65.0,
    minWordAccuracy: Double = 0.65
  ): OfflineGradingOutcome {
    val floatPcm = NeMoMelPreprocessor.pcm16ToFloat32(pcm16k)
    return gradeFullUtteranceFloat(
      context = context,
      pcmFloat32 = floatPcm,
      suraAyah = suraAyah,
      turn = turn,
      minPeakRms = minPeakRms,
      passThresholdPercent = passThresholdPercent,
      minWordAccuracy = minWordAccuracy
    )
  }
}
