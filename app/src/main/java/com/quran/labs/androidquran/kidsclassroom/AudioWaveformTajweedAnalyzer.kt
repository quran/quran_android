package com.quran.labs.androidquran.kidsclassroom

import androidx.compose.ui.graphics.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Real-time acoustic snapshot extracted every ~20 ms directly from Sheikh Al-Minshawi Teacher's
 * decoded PCM / WAV waveform.
 */
data class AudioAcousticFrame(
  val rms: Float = 0f,
  val lowBandRatio: Float = 0f,      // < 420 Hz (nasal Ghunnah hum & fundamental resonance)
  val midBandRatio: Float = 0f,      // 420–2200 Hz (vowel formants F1/F2 for sustained Madd)
  val highBandRatio: Float = 0f,     // > 2200 Hz (fricatives / sibilants / breath)
  val zcr: Float = 0f,               // Zero-crossing rate (low during voiced vowels/nasals)
  val steadiness: Float = 0f,        // 0..1 inverse of short-time amplitude flux
  val qalqalahBurstScore: Float = 0f,// 0..1 stop-closure dip -> crisp voiced plosive release
  val spectralCentroidNorm: Float = 0.5f, // 0 = dark/heavy (Tafkheem), 1 = bright/fricative
  val waveformBars: List<Float> = List(28) { 0f }
)

/**
 * Audio-primary Tajweed clue emitted at 60 fps for [MinshawiAudioCluesCanvas] when Ustadh Noor's
 * figure is removed.
 */
data class AudioTajweedClueSample(
  val activeRuleType: TajweedRuleType = TajweedRuleType.NORMAL,
  val tajweedColor: Color = TajweedRuleType.NORMAL.defaultColor,
  val cueLabelAr: String = "",
  val cueLabelEn: String = "",
  val matchedWordText: String = "",
  val maddProgress: Float = 0f,
  val maddHoldMs: Long = 0L,
  val nasalHoldMs: Long = 0L,
  val qalqalahPulse: Float = 0f,
  val tafkheemIntensity: Float = 0f,
  val isVoiceActive: Boolean = false,
  val audioConfidence: Float = 0f,
  val acousticFrame: AudioAcousticFrame = AudioAcousticFrame(),
  val mood: TeacherMood = TeacherMood.GREETING,
  val isSlowMo: Boolean = false,
  val starsEarned: Int = 0
)

/**
 * Fast, zero-allocation 3-band PCM acoustic feature extractor running directly on ExoPlayer's
 * decoded PCM stream (or raw `.wav` PCM samples) every ~20 ms.
 *
 * Uses two 1st-order IIR low-pass filters (`fc1 = 420 Hz`, `fc2 = 2200 Hz`) to split the live
 * waveform into:
 * - **Low band (`< 420 Hz`)**: nasal Ghunnah & fundamental hum.
 * - **Mid band (`420–2200 Hz`)**: resonant open vowel formants (`ا / و / ي` Mudood).
 * - **High band (`> 2200 Hz`)**: sibilants, unvoiced fricatives, and breath noise.
 *
 * Also detects Qalqalah acoustic dip→burst transients directly from the PCM envelope.
 */
class StreamingPcmAcousticTracker(
  private val barCount: Int = 28
) {
  private var sampleRate: Int = 16000
  private var channels: Int = 1
  private var windowFrames: Int = 320 // ~20 ms

  // 1st-order IIR filter coefficients for 420 Hz and 2200 Hz split
  private var alphaLow: Float = 0.15f
  private var alphaMid: Float = 0.58f
  private var lpLowState: Float = 0f
  private var lpMidState: Float = 0f
  private var prevMono: Float = 0f

  // Per-20ms window accumulators
  private var frameCount: Int = 0
  private var channelAcc: Float = 0f
  private var channelIdx: Int = 0
  private var sumSqTotal: Double = 0.0
  private var sumSqLow: Double = 0.0
  private var sumSqMid: Double = 0.0
  private var sumSqHigh: Double = 0.0
  private var zeroCrossings: Int = 0

  // Short-time temporal envelope tracking (for steadiness & Qalqalah dip->burst detection)
  private var smoothedRms: Float = 0f
  private var prevWindowRms: Float = 0f
  private var recentDipWindows: Int = 0
  private var recentVoicedBeforeDip: Boolean = false
  private var barsHistory = FloatArray(barCount)

  @Volatile
  private var latestFrame: AudioAcousticFrame = AudioAcousticFrame(waveformBars = List(barCount) { 0f })

  init {
    configure(16000, 1)
  }

  fun configure(sampleRateHz: Int, channelCount: Int) {
    sampleRate = sampleRateHz.coerceIn(8000, 96000)
    channels = channelCount.coerceIn(1, 8)
    windowFrames = ((sampleRate * 20L) / 1000L).toInt().coerceAtLeast(160)
    // alpha = 1 - exp(-2*pi*fc/fs)
    alphaLow = (1.0 - exp(-2.0 * PI * 420.0 / sampleRate.toDouble())).toFloat().coerceIn(0.01f, 0.95f)
    alphaMid = (1.0 - exp(-2.0 * PI * 2200.0 / sampleRate.toDouble())).toFloat().coerceIn(0.05f, 0.98f)
    reset()
  }

  fun reset() {
    lpLowState = 0f
    lpMidState = 0f
    prevMono = 0f
    frameCount = 0
    channelAcc = 0f
    channelIdx = 0
    sumSqTotal = 0.0
    sumSqLow = 0.0
    sumSqMid = 0.0
    sumSqHigh = 0.0
    zeroCrossings = 0
    smoothedRms = 0f
    prevWindowRms = 0f
    recentDipWindows = 0
    recentVoicedBeforeDip = false
    barsHistory = FloatArray(barCount)
    latestFrame = AudioAcousticFrame(waveformBars = List(barCount) { 0f })
  }

  fun latestFrame(): AudioAcousticFrame = latestFrame

  fun pushInterleavedSample(sample: Float) {
    channelAcc += sample
    channelIdx++
    if (channelIdx < channels) return
    val mono = (channelAcc / channels.toFloat()).coerceIn(-1f, 1f)
    channelAcc = 0f
    channelIdx = 0
    pushMonoSample(mono)
  }

  fun pushMonoSample(mono: Float) {
    // 3-band 1-pole crossover
    lpLowState += alphaLow * (mono - lpLowState)
    lpMidState += alphaMid * (mono - lpMidState)
    val lowSample = lpLowState
    val midSample = lpMidState - lpLowState
    val highSample = mono - lpMidState

    sumSqTotal += (mono * mono).toDouble()
    sumSqLow += (lowSample * lowSample).toDouble()
    sumSqMid += (midSample * midSample).toDouble()
    sumSqHigh += (highSample * highSample).toDouble()

    if ((mono >= 0f && prevMono < 0f) || (mono < 0f && prevMono >= 0f)) {
      zeroCrossings++
    }
    prevMono = mono
    frameCount++

    if (frameCount >= windowFrames) {
      finalizeWindow()
    }
  }

  private fun finalizeWindow() {
    val n = frameCount.coerceAtLeast(1).toDouble()
    val rawRms = sqrt(sumSqTotal / n).toFloat()
    val scaledRms = (rawRms * 3.6f).coerceIn(0f, 1f)
    val alpha = if (scaledRms > smoothedRms) 0.65f else 0.35f
    smoothedRms = (alpha * scaledRms + (1f - alpha) * smoothedRms).coerceIn(0f, 1f)

    val bandSum = (sumSqLow + sumSqMid + sumSqHigh).coerceAtLeast(1e-9)
    val lowRatio = (sumSqLow / bandSum).toFloat().coerceIn(0f, 1f)
    val midRatio = (sumSqMid / bandSum).toFloat().coerceIn(0f, 1f)
    val highRatio = (sumSqHigh / bandSum).toFloat().coerceIn(0f, 1f)
    val zcr = (zeroCrossings.toFloat() / n.toFloat()).coerceIn(0f, 1f)

    val rmsDelta = abs(scaledRms - prevWindowRms)
    val steadiness = (1f - (rmsDelta * 2.8f)).coerceIn(0f, 1f)

    // Qalqalah acoustic signature: brief closure dip (1–5 windows = 20–100 ms) after voicing,
    // immediately followed by a crisp voiced plosive energy jump (not high-freq fricative noise).
    val isDipNow = scaledRms < 0.13f
    var burstScore = 0f
    if (isDipNow) {
      if (prevWindowRms >= 0.16f) {
        recentVoicedBeforeDip = true
        recentDipWindows = 1
      } else if (recentDipWindows in 1..6) {
        recentDipWindows++
      } else {
        recentVoicedBeforeDip = false
        recentDipWindows = 0
      }
    } else {
      val jump = scaledRms - prevWindowRms
      if (recentVoicedBeforeDip && recentDipWindows in 1..6 && jump >= 0.14f && highRatio < 0.45f) {
        burstScore = ((jump - 0.10f) / 0.25f).coerceIn(0.45f, 1.0f)
      }
      recentDipWindows = 0
      if (scaledRms >= 0.16f) {
        recentVoicedBeforeDip = true
      }
    }
    prevWindowRms = scaledRms

    // Spectral darkness/brightness estimate (0 = dark heavy Tafkheem/nasal, 1 = bright sibilant)
    val centroid = (0.12f * lowRatio + 0.50f * midRatio + 0.95f * highRatio).coerceIn(0f, 1f)

    // Shift rolling 28-bar waveform history
    for (i in 0 until barCount - 1) {
      barsHistory[i] = barsHistory[i + 1]
    }
    barsHistory[barCount - 1] = smoothedRms

    latestFrame = AudioAcousticFrame(
      rms = smoothedRms,
      lowBandRatio = lowRatio,
      midBandRatio = midRatio,
      highBandRatio = highRatio,
      zcr = zcr,
      steadiness = steadiness,
      qalqalahBurstScore = burstScore,
      spectralCentroidNorm = centroid,
      waveformBars = barsHistory.toList()
    )

    frameCount = 0
    sumSqTotal = 0.0
    sumSqLow = 0.0
    sumSqMid = 0.0
    sumSqHigh = 0.0
    zeroCrossings = 0
  }
}

/**
 * **Audio-Primary Tajweed Clue Engine** for the existing **Sheikh Al-Minshawi Teacher** recitation
 * audio stream (decoded live to PCM by ExoPlayer or parsed directly from WAV).
 *
 * Because the Quranic text is not strictly forced-aligned millisecond-by-millisecond to the
 * recitation audio, this analyzer **depends primarily on the live PCM/WAV audio waveform**
 * to decide WHEN a Tajweed phenomenon starts, how long it holds, and when it releases:
 * - **Silence (`RMS < 0.06f` for `>= 80 ms`)**: Always clears cues cleanly.
 * - **Madd (`MADD_2`, `MADD_4_5`, `MADD_6`)**: Driven directly by sustained voiced open-vowel
 *   energy plateaus (`>= 280 ms` → 2 counts, `>= 680 ms` → 4–5 counts, `>= 1200 ms` → 6 counts),
 *   so the vertical Madd bar climbs in exact lockstep with Sheikh Al-Minshawi's real breath.
 * - **Ghunnah (`GHUNNAH_MEEM`, `GHUNNAH_NOON`, `IKHFA`, `IQLAB`)**: Driven directly by sustained
 *   low-frequency nasal resonance (`lowBandRatio >= 0.54f`, `highBandRatio < 0.16f`, `zcr < 0.18f`,
 *   held `>= 180 ms`).
 * - **Qalqalah (`QALQALAH`)**: Triggered directly by stop-closure dip → crisp voiced burst
 *   transients (`qalqalahBurstScore >= 0.58f`), held visibly for `280 ms`.
 * - **Tafkheem (`TAFKHEEM`)**: Triggered by strong dark low-mid resonance (`rms >= 0.22f`,
 *   `spectralCentroidNorm <= 0.36f`) when heavy letters exist in the Ayah's soft text window.
 *
 * The Ayah's [TajweedAyahAnalysis] is used strictly as a **wide-window soft prior (`±0.42` progress)**
 * to supply the exact Arabic word/cluster label and refine Ghunnah sub-types without ever forcing
 * misaligned text timings onto the audio.
 */
class AudioWaveformTajweedAnalyzer {

  private data class TextRuleCandidate(
    val relativePos: Float,
    val ruleType: TajweedRuleType,
    val tajweedColor: Color,
    val wordText: String,
    val clusterText: String
  )

  private var sustainedVowelMs: Long = 0L
  private var sustainedNasalMs: Long = 0L
  private var silenceAccumMs: Long = 0L
  private var qalqalahHoldRemainingMs: Long = 0L
  private var lastQalqalahCandidate: TextRuleCandidate? = null

  fun reset() {
    sustainedVowelMs = 0L
    sustainedNasalMs = 0L
    silenceAccumMs = 0L
    qalqalahHoldRemainingMs = 0L
    lastQalqalahCandidate = null
  }

  /**
   * Advances the audio-primary Tajweed detector by [deltaMs] using the live [frame] from Sheikh
   * Al-Minshawi's waveform, the approximate [verseProgress] (`0f..1f`), and the Ayah's [analysis].
   */
  fun analyzeStep(
    frame: AudioAcousticFrame,
    deltaMs: Long,
    verseProgress: Float,
    analysis: TajweedAyahAnalysis,
    mood: TeacherMood = TeacherMood.RECITING,
    isSlowMo: Boolean = false,
    starsEarned: Int = 0
  ): AudioTajweedClueSample {
    val dt = deltaMs.coerceIn(1L, 120L)
    val clampedProgress = verseProgress.coerceIn(0f, 1f)
    val candidates = extractCandidates(analysis)

    // 1. Audio Silence Gate (100% WAV-driven): silence >= 80 ms resets holds & clears active cues
    if (frame.rms < TajweedVisemePlanner.SILENCE_RMS_THRESHOLD) {
      silenceAccumMs += dt
      if (silenceAccumMs >= TajweedVisemePlanner.SILENCE_HOLD_MS) {
        sustainedVowelMs = 0L
        sustainedNasalMs = 0L
        qalqalahHoldRemainingMs = 0L
        return AudioTajweedClueSample(
          activeRuleType = TajweedRuleType.NORMAL,
          tajweedColor = TajweedRuleType.NORMAL.defaultColor,
          isVoiceActive = false,
          audioConfidence = 0f,
          acousticFrame = frame,
          mood = mood,
          isSlowMo = isSlowMo,
          starsEarned = starsEarned
        )
      }
    } else {
      silenceAccumMs = 0L
    }

    // 2. Track acoustic Qalqalah dip -> crisp voiced plosive burst
    if (qalqalahHoldRemainingMs > 0L) {
      qalqalahHoldRemainingMs = (qalqalahHoldRemainingMs - dt).coerceAtLeast(0L)
    }
    val qalqalahCandidate = findNearestCandidate(
      candidates = candidates,
      progress = clampedProgress,
      maxWindow = 0.42f
    ) { it.ruleType == TajweedRuleType.QALQALAH }

    val qalqalahThreshold = if (qalqalahCandidate != null) 0.55f else 0.86f
    if (frame.qalqalahBurstScore >= qalqalahThreshold) {
      qalqalahHoldRemainingMs = 280L
      lastQalqalahCandidate = qalqalahCandidate
    }

    // 3. Track acoustic Nasal Ghunnah hum (<420 Hz dominant, low high-band, low ZCR, steady)
    val isAcousticNasal = frame.rms >= 0.08f &&
      frame.lowBandRatio >= 0.54f &&
      frame.highBandRatio < 0.16f &&
      frame.zcr < 0.18f &&
      frame.steadiness >= 0.46f

    if (isAcousticNasal) {
      sustainedNasalMs += dt
    } else {
      sustainedNasalMs = (sustainedNasalMs - dt * 2L).coerceAtLeast(0L)
    }

    // 4. Track acoustic Open Sustained Vowel plateau (strong mid formant band, steady, low ZCR)
    val isAcousticVowelHold = frame.rms >= 0.14f &&
      !isAcousticNasal &&
      frame.midBandRatio >= 0.34f &&
      frame.zcr < 0.24f &&
      frame.steadiness >= 0.50f

    if (isAcousticVowelHold) {
      sustainedVowelMs += dt
    } else if (frame.rms < 0.10f || frame.highBandRatio > 0.35f || isAcousticNasal) {
      sustainedVowelMs = 0L
    } else {
      sustainedVowelMs = (sustainedVowelMs - dt * 2L).coerceAtLeast(0L)
    }

    // 5. Prioritize the strongest live acoustic Tajweed event (WAV first, soft text label second!)

    // 5a. Qalqalah crisp bounce pop
    if (qalqalahHoldRemainingMs > 0L) {
      val pulse = (qalqalahHoldRemainingMs.toFloat() / 280f).coerceIn(0f, 1f)
      val matched = lastQalqalahCandidate ?: qalqalahCandidate
      val color = matched?.tajweedColor ?: TajweedRuleType.QALQALAH.defaultColor
      return AudioTajweedClueSample(
        activeRuleType = TajweedRuleType.QALQALAH,
        tajweedColor = color,
        cueLabelAr = TajweedRuleType.QALQALAH.labelAr,
        cueLabelEn = TajweedRuleType.QALQALAH.labelEn,
        matchedWordText = matched?.wordText ?: "",
        qalqalahPulse = pulse,
        isVoiceActive = true,
        audioConfidence = max(0.75f, frame.qalqalahBurstScore),
        acousticFrame = frame,
        mood = mood,
        isSlowMo = isSlowMo,
        starsEarned = starsEarned
      )
    }

    // 5b. Sustained Madd vowel hold (vertical Madd bar driven directly by sustainedVowelMs!)
    val maddMinMs = if (isSlowMo) 350L else 280L
    if (sustainedVowelMs >= maddMinMs) {
      val speedNormMs = if (isSlowMo) (sustainedVowelMs * 0.75f).toLong() else sustainedVowelMs
      val nearestMaddText = findNearestCandidate(
        candidates = candidates,
        progress = clampedProgress,
        maxWindow = 0.42f
      ) { it.ruleType.maddCounts > 0 }

      // Determine Madd tier primarily from real audio hold duration, softly guided by text ceiling
      val audioMaddRule = when {
        speedNormMs >= 1200L -> TajweedRuleType.MADD_6
        speedNormMs >= 680L -> {
          if (nearestMaddText?.ruleType == TajweedRuleType.MADD_6 && speedNormMs >= 950L) {
            TajweedRuleType.MADD_6
          } else {
            TajweedRuleType.MADD_4_5
          }
        }
        else -> {
          // Early into the hold: stay at MADD_2 until audio duration actually climbs past 680 ms
          TajweedRuleType.MADD_2
        }
      }

      val targetHoldMs = when (audioMaddRule) {
        TajweedRuleType.MADD_2 -> 640f
        TajweedRuleType.MADD_4_5 -> 1240f
        TajweedRuleType.MADD_6 -> 1850f
        else -> 640f
      }
      val liveMaddProgress = (speedNormMs.toFloat() / targetHoldMs).coerceIn(0.12f, 1.0f)
      val color = if (nearestMaddText?.ruleType == audioMaddRule) {
        nearestMaddText.tajweedColor
      } else {
        audioMaddRule.defaultColor
      }

      return AudioTajweedClueSample(
        activeRuleType = audioMaddRule,
        tajweedColor = color,
        cueLabelAr = audioMaddRule.labelAr,
        cueLabelEn = audioMaddRule.labelEn,
        matchedWordText = nearestMaddText?.wordText ?: "",
        maddProgress = liveMaddProgress,
        maddHoldMs = sustainedVowelMs,
        isVoiceActive = true,
        audioConfidence = (0.65f + 0.35f * frame.steadiness).coerceIn(0f, 1f),
        acousticFrame = frame,
        mood = mood,
        isSlowMo = isSlowMo,
        starsEarned = starsEarned
      )
    }

    // 5c. Sustained Nasal Ghunnah hum (separated into GHUNNAH_MEEM / GHUNNAH_NOON / IKHFA / IQLAB via soft text context)
    val nasalMinMs = if (isSlowMo) 220L else 180L
    if (sustainedNasalMs >= nasalMinMs) {
      val nearestGreen = findNearestCandidate(
        candidates = candidates,
        progress = clampedProgress,
        maxWindow = 0.45f
      ) { it.ruleType.isGhunnahFamily }

      val resolvedRule = nearestGreen?.ruleType ?: TajweedRuleType.GHUNNAH_NOON
      val color = nearestGreen?.tajweedColor ?: resolvedRule.defaultColor
      return AudioTajweedClueSample(
        activeRuleType = resolvedRule,
        tajweedColor = color,
        cueLabelAr = resolvedRule.labelAr,
        cueLabelEn = resolvedRule.labelEn,
        matchedWordText = nearestGreen?.wordText ?: "",
        nasalHoldMs = sustainedNasalMs,
        isVoiceActive = true,
        audioConfidence = (0.60f + 0.40f * frame.lowBandRatio).coerceIn(0f, 1f),
        acousticFrame = frame,
        mood = mood,
        isSlowMo = isSlowMo,
        starsEarned = starsEarned
      )
    }

    // 5d. Heavy Tafkheem dark low-mid resonance (requires strong dark audio + heavy letter in wide text window)
    val nearestTafkheem = findNearestCandidate(
      candidates = candidates,
      progress = clampedProgress,
      maxWindow = 0.30f
    ) { it.ruleType == TajweedRuleType.TAFKHEEM }

    if (nearestTafkheem != null &&
      frame.rms >= 0.22f &&
      frame.spectralCentroidNorm <= 0.36f &&
      (frame.lowBandRatio + frame.midBandRatio) >= 0.84f
    ) {
      val intensity = ((0.40f - frame.spectralCentroidNorm) / 0.24f).coerceIn(0.35f, 1f)
      return AudioTajweedClueSample(
        activeRuleType = TajweedRuleType.TAFKHEEM,
        tajweedColor = nearestTafkheem.tajweedColor,
        cueLabelAr = TajweedRuleType.TAFKHEEM.labelAr,
        cueLabelEn = TajweedRuleType.TAFKHEEM.labelEn,
        matchedWordText = nearestTafkheem.wordText,
        tafkheemIntensity = intensity,
        isVoiceActive = true,
        audioConfidence = intensity,
        acousticFrame = frame,
        mood = mood,
        isSlowMo = isSlowMo,
        starsEarned = starsEarned
      )
    }

    // 5e. Ordinary speech: calm waveform, no distracting Tajweed popup
    return AudioTajweedClueSample(
      activeRuleType = TajweedRuleType.NORMAL,
      tajweedColor = TajweedRuleType.NORMAL.defaultColor,
      isVoiceActive = frame.rms >= TajweedVisemePlanner.SILENCE_RMS_THRESHOLD,
      audioConfidence = 0f,
      acousticFrame = frame,
      mood = mood,
      isSlowMo = isSlowMo,
      starsEarned = starsEarned
    )
  }

  private fun extractCandidates(analysis: TajweedAyahAnalysis): List<TextRuleCandidate> {
    val tokens = analysis.allTokens.filter { it.ruleType != TajweedRuleType.SILENT }
    if (tokens.isEmpty()) return emptyList()
    val total = tokens.size.coerceAtLeast(1)
    val wordLookup = analysis.words.associateBy({ it.wordIndex }, { it.rawWord })
    val out = ArrayList<TextRuleCandidate>()
    tokens.forEachIndexed { idx, tok ->
      if (tok.ruleType.isArticulatedTajweedRule) {
        out.add(
          TextRuleCandidate(
            relativePos = (idx + 0.5f) / total.toFloat(),
            ruleType = tok.ruleType,
            tajweedColor = tok.tajweedColor,
            wordText = wordLookup[tok.wordIndex] ?: tok.clusterText,
            clusterText = tok.clusterText
          )
        )
      }
    }
    return out
  }

  private fun findNearestCandidate(
    candidates: List<TextRuleCandidate>,
    progress: Float,
    maxWindow: Float,
    predicate: (TextRuleCandidate) -> Boolean
  ): TextRuleCandidate? {
    var best: TextRuleCandidate? = null
    var bestDist = Float.MAX_VALUE
    for (c in candidates) {
      if (!predicate(c)) continue
      val dist = abs(c.relativePos - progress)
      if (dist <= maxWindow && dist < bestDist) {
        bestDist = dist
        best = c
      }
    }
    return best
  }

  companion object {
    /**
     * Parses a standard PCM-16 little-endian RIFF `.wav` byte array into `(sampleRateHz, monoFloatPcm)`.
     * Supports mono or multi-channel WAV recordings (downmixed cleanly to mono `[-1f, 1f]`).
     */
    fun parseWavPcm16(wavBytes: ByteArray): Pair<Int, FloatArray>? {
      if (wavBytes.size < 44) return null
      if (wavBytes[0] != 'R'.code.toByte() ||
        wavBytes[1] != 'I'.code.toByte() ||
        wavBytes[2] != 'F'.code.toByte() ||
        wavBytes[3] != 'F'.code.toByte()
      ) {
        return null
      }
      val bb = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
      var offset = 12
      var sampleRate = 16000
      var channels = 1
      var bitsPerSample = 16
      var dataStart = -1
      var dataSize = 0

      while (offset + 8 <= wavBytes.size) {
        val chunkId = String(wavBytes, offset, 4, Charsets.US_ASCII)
        val chunkSize = bb.getInt(offset + 4).coerceAtLeast(0)
        val payloadStart = offset + 8
        when (chunkId) {
          "fmt " -> {
            if (payloadStart + 16 <= wavBytes.size) {
              channels = bb.getShort(payloadStart + 2).toInt().coerceIn(1, 8)
              sampleRate = bb.getInt(payloadStart + 4).coerceIn(8000, 96000)
              bitsPerSample = bb.getShort(payloadStart + 14).toInt()
            }
          }
          "data" -> {
            dataStart = payloadStart
            dataSize = min(chunkSize, wavBytes.size - payloadStart)
            break
          }
        }
        offset = payloadStart + chunkSize + (chunkSize and 1)
      }

      if (dataStart < 0 || dataSize < 2 || bitsPerSample != 16) return null
      val bytesPerFrame = channels * 2
      val frameCount = dataSize / bytesPerFrame
      val mono = FloatArray(frameCount)
      var pos = dataStart
      for (i in 0 until frameCount) {
        var acc = 0f
        for (ch in 0 until channels) {
          val s = bb.getShort(pos).toFloat() / 32768f
          acc += s
          pos += 2
        }
        mono[i] = (acc / channels.toFloat()).coerceIn(-1f, 1f)
      }
      return sampleRate to mono
    }
  }
}
