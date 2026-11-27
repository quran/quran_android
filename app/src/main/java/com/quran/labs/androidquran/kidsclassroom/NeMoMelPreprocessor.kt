package com.quran.labs.androidquran.kidsclassroom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bit-for-bit implementation of the NVIDIA NeMo `AudioToMelSpectrogramPreprocessor`
 * contract specified in `ANDROID_AI_STUDIO_GUIDE.md` (§4.2):
 *
 * 1. 10ms Frame-RMS VAD Trim (`win=400`, `hop=160`, `top_db=32`, `peakRms >= 0.004f`)
 *    + mandatory `3,520` zero samples (`220 ms`) prepended and appended.
 * 2. Pre-emphasis (`0.97f`), `256`-sample center zero-padding on both ends (`n_fft / 2`),
 *    `400`-sample symmetric Hann window center-padded by `56` zeros on each side to `512`,
 *    `512`-point Real FFT power spectrum (`257` bins), `80`-bin Slaney Mel filterbank (`0..8000 Hz`),
 *    natural log `ln(mel + 2^-24)`, and per-channel Bessel normalization (`ddof = 1`, `eps = 1e-5f`).
 */
object NeMoMelPreprocessor {

  const val SAMPLE_RATE = 16000
  const val N_FFT = 512
  const val NUM_FFT_BINS = N_FFT / 2 + 1 // 257
  const val WIN_LENGTH = 400
  const val HOP_LENGTH = 160
  const val N_MELS = 80
  const val CENTER_PAD = N_FFT / 2 // 256
  const val SILENCE_PAD_SAMPLES = 3520 // 220 ms at 16 kHz
  const val MIN_TRIMMED_SAMPLES = 1600 // 100 ms at 16 kHz
  const val MIN_PEAK_RMS = 0.004f
  const val PREEMPH = 0.97f
  const val LOG_ZERO_GUARD = 5.9604645e-8f // 2^-24
  const val NORM_EPS = 1e-5f

  // Top-dB threshold factor: 10^(-32.0 / 20.0) = 0.025118864f
  private val VAD_TOP_DB_FACTOR = 10.0.pow(-32.0 / 20.0).toFloat()

  /**
   * Result of preprocessing raw 16kHz PCM float32 audio.
   */
  sealed class PreprocessResult {
    data object NoSpeech : PreprocessResult()
    data class MelFeatures(
      val features: FloatArray, // Flattened [1, 80, T_mel] in row-major (melBin * T_mel + t)
      val numFrames: Int
    ) : PreprocessResult()
  }

  /**
   * Symmetric Hann window of length 400 (`w[n] = 0.5 * (1 - cos(2*pi*n / 399))`),
   * center-padded with 56 zeros on the left and 56 zeros on the right to length 512.
   */
  private val PADDED_HANN_WINDOW_512: FloatArray by lazy {
    val padded = FloatArray(N_FFT)
    val leftPad = (N_FFT - WIN_LENGTH) / 2 // 56
    for (n in 0 until WIN_LENGTH) {
      val w = 0.5 * (1.0 - cos((2.0 * PI * n.toDouble()) / (WIN_LENGTH - 1).toDouble()))
      padded[leftPad + n] = w.toFloat()
    }
    padded
  }

  /**
   * 80 x 257 Slaney-normalized Mel filterbank (`sr=16000, n_fft=512, n_mels=80, fmin=0.0, fmax=8000.0`).
   * Matches `librosa.filters.mel(..., htk=False, norm="slaney")`.
   */
  val MEL_BASIS_80X257: Array<FloatArray> by lazy {
    buildSlaneyMelFilterbank(
      sr = SAMPLE_RATE,
      nFft = N_FFT,
      nMels = N_MELS,
      fMin = 0.0,
      fMax = SAMPLE_RATE / 2.0
    )
  }

  // Precomputed radix-2 Cooley-Tukey bit-reversal and twiddle tables for N = 512
  private val BIT_REVERSED_512: IntArray by lazy {
    IntArray(N_FFT) { i ->
      Integer.reverse(i) ushr (32 - 9) // log2(512) = 9
    }
  }

  private val FFT_COS_TABLE: FloatArray by lazy {
    FloatArray(N_FFT / 2) { k -> cos(-2.0 * PI * k / N_FFT).toFloat() }
  }

  private val FFT_SIN_TABLE: FloatArray by lazy {
    FloatArray(N_FFT / 2) { k -> sin(-2.0 * PI * k / N_FFT).toFloat() }
  }

  /**
   * Converts 16-bit PCM samples (`ShortArray`) into normalized `[-1.0f, 1.0f]` float32 samples.
   */
  fun pcm16ToFloat32(pcm16: ShortArray, length: Int = pcm16.size): FloatArray {
    val count = min(length, pcm16.size).coerceAtLeast(0)
    val out = FloatArray(count)
    val inv = 1.0f / 32768.0f
    for (i in 0 until count) {
      out[i] = (pcm16[i].toInt() * inv).coerceIn(-1.0f, 1.0f)
    }
    return out
  }

  /**
   * Runs the full VAD trim + 220ms zero pad + NeMo Mel-Spectrogram + Bessel normalization pipeline.
   */
  fun process(
    pcmFloat32: FloatArray,
    minPeakRms: Float = MIN_PEAK_RMS
  ): PreprocessResult {
    if (pcmFloat32.size < MIN_TRIMMED_SAMPLES) {
      return PreprocessResult.NoSpeech
    }

    // 1. 10ms Frame-RMS VAD Trim + 220ms (3,520 samples) Zero Pad
    val numVadFrames = if (pcmFloat32.size >= WIN_LENGTH) {
      1 + (pcmFloat32.size - WIN_LENGTH) / HOP_LENGTH
    } else {
      0
    }
    if (numVadFrames <= 0) {
      return PreprocessResult.NoSpeech
    }

    val frameRms = FloatArray(numVadFrames)
    var peakRms = 0f
    val invWin = 1.0 / WIN_LENGTH.toDouble()

    for (f in 0 until numVadFrames) {
      val start = f * HOP_LENGTH
      var sumSq = 0.0
      for (i in 0 until WIN_LENGTH) {
        val v = pcmFloat32[start + i].toDouble()
        sumSq += v * v
      }
      val rms = sqrt(sumSq * invWin + 1e-12).toFloat()
      frameRms[f] = rms
      if (rms > peakRms) {
        peakRms = rms
      }
    }

    if (peakRms < minPeakRms) {
      return PreprocessResult.NoSpeech
    }

    val thresh = peakRms * VAD_TOP_DB_FACTOR
    var firstActive = -1
    var lastActive = -1
    for (f in 0 until numVadFrames) {
      if (frameRms[f] >= thresh) {
        if (firstActive == -1) firstActive = f
        lastActive = f
      }
    }

    if (firstActive == -1 || lastActive == -1) {
      return PreprocessResult.NoSpeech
    }

    val sliceStart = (firstActive * HOP_LENGTH).coerceIn(0, pcmFloat32.size)
    val sliceEnd = (lastActive * HOP_LENGTH + WIN_LENGTH).coerceIn(sliceStart, pcmFloat32.size)
    val trimmedLength = sliceEnd - sliceStart
    if (trimmedLength < MIN_TRIMMED_SAMPLES) {
      return PreprocessResult.NoSpeech
    }

    // Prepend and append 3,520 zeros (220ms) around trimmed speech
    val paddedSignalLen = SILENCE_PAD_SAMPLES + trimmedLength + SILENCE_PAD_SAMPLES
    val s = FloatArray(paddedSignalLen)
    System.arraycopy(pcmFloat32, sliceStart, s, SILENCE_PAD_SAMPLES, trimmedLength)

    return computeMelFromSignal(s)
  }

  /**
   * Rolling-window preprocessor for Live Recitation Follow-Along ("Tasmee' / تسميع") Mode:
   * - Verifies live speech energy in the latest [recentHopSamples] (~550 ms) so background room
   *   silence between breaths skips redundant ONNX passes.
   * - Trims leading dead silence using `top_db = 32`, caps active speech to [maxActiveSamples]
   *   (`6.0s` = `96,000` samples at `16 kHz`) so per-channel Bessel normalization (`ddof = 1`)
   *   always has stable full-context statistics, and prepends + appends the mandatory `3,520`
   *   zero-padding samples (`220 ms`).
   */
  fun processRollingTasmeeWindow(
    windowPcm: FloatArray,
    recentHopSamples: Int = 8800,
    maxActiveSamples: Int = 96000,
    minRecentRms: Float = 0.0045f
  ): PreprocessResult {
    if (windowPcm.size < MIN_TRIMMED_SAMPLES) {
      return PreprocessResult.NoSpeech
    }

    val numVadFrames = if (windowPcm.size >= WIN_LENGTH) {
      1 + (windowPcm.size - WIN_LENGTH) / HOP_LENGTH
    } else {
      0
    }
    if (numVadFrames <= 0) {
      return PreprocessResult.NoSpeech
    }

    val recentStartSample = (windowPcm.size - recentHopSamples).coerceAtLeast(0)
    val recentFirstFrame = (recentStartSample / HOP_LENGTH).coerceIn(0, numVadFrames - 1)

    val frameRms = FloatArray(numVadFrames)
    var peakRms = 0f
    var recentPeakRms = 0f
    val invWin = 1.0 / WIN_LENGTH.toDouble()

    for (f in 0 until numVadFrames) {
      val start = f * HOP_LENGTH
      var sumSq = 0.0
      for (i in 0 until WIN_LENGTH) {
        val v = windowPcm[start + i].toDouble()
        sumSq += v * v
      }
      val rms = sqrt(sumSq * invWin + 1e-12).toFloat()
      frameRms[f] = rms
      if (rms > peakRms) {
        peakRms = rms
      }
      if (f >= recentFirstFrame && rms > recentPeakRms) {
        recentPeakRms = rms
      }
    }

    if (peakRms < MIN_PEAK_RMS || recentPeakRms < minRecentRms) {
      return PreprocessResult.NoSpeech
    }

    val thresh = peakRms * VAD_TOP_DB_FACTOR
    var firstActive = -1
    var lastActive = -1
    for (f in 0 until numVadFrames) {
      if (frameRms[f] >= thresh) {
        if (firstActive == -1) firstActive = f
        lastActive = f
      }
    }
    if (firstActive == -1 || lastActive == -1) {
      return PreprocessResult.NoSpeech
    }

    var sliceStart = (firstActive * HOP_LENGTH).coerceIn(0, windowPcm.size)
    val sliceEnd = (lastActive * HOP_LENGTH + WIN_LENGTH).coerceIn(sliceStart, windowPcm.size)
    if (sliceEnd - sliceStart > maxActiveSamples) {
      sliceStart = sliceEnd - maxActiveSamples
    }
    val trimmedLength = sliceEnd - sliceStart
    if (trimmedLength < MIN_TRIMMED_SAMPLES) {
      return PreprocessResult.NoSpeech
    }

    // Mandatory 3,520 zero-padding samples (220 ms) before and after active speech window
    val paddedSignalLen = SILENCE_PAD_SAMPLES + trimmedLength + SILENCE_PAD_SAMPLES
    val s = FloatArray(paddedSignalLen)
    System.arraycopy(windowPcm, sliceStart, s, SILENCE_PAD_SAMPLES, trimmedLength)

    return computeMelFromSignal(s)
  }

  /**
   * Computes `[1, 80, T_mel]` normalized log-Mel features directly from a prepared signal `s`
   * (exposed for deterministic unit testing of the Mel + Bessel normalization stages).
   */
  internal fun computeMelFromSignal(s: FloatArray): PreprocessResult.MelFeatures {
    // 2a. Pre-emphasis (0.97f) + 256-sample center zero pad on both sides
    val totalCenteredLen = CENTER_PAD + s.size + CENTER_PAD
    val centered = FloatArray(totalCenteredLen)
    if (s.isNotEmpty()) {
      centered[CENTER_PAD] = s[0]
      for (t in 1 until s.size) {
        centered[CENTER_PAD + t] = s[t] - PREEMPH * s[t - 1]
      }
    }

    // 2b. Frame count: slice frames of length 512 every 160 samples
    val tMel = 1 + (totalCenteredLen - N_FFT) / HOP_LENGTH
    val rawMel = FloatArray(N_MELS * tMel)

    val re = FloatArray(N_FFT)
    val im = FloatArray(N_FFT)
    val powerSpec = FloatArray(NUM_FFT_BINS)
    val win = PADDED_HANN_WINDOW_512
    val melBasis = MEL_BASIS_80X257

    for (frameIdx in 0 until tMel) {
      val offset = frameIdx * HOP_LENGTH
      for (n in 0 until N_FFT) {
        re[n] = centered[offset + n] * win[n]
        im[n] = 0f
      }

      fft512InPlace(re, im)

      for (k in 0 until NUM_FFT_BINS) {
        val r = re[k]
        val i = im[k]
        powerSpec[k] = r * r + i * i
      }

      for (m in 0 until N_MELS) {
        val weights = melBasis[m]
        var energy = 0.0
        for (k in 0 until NUM_FFT_BINS) {
          val w = weights[k]
          if (w != 0f) {
            energy += w * powerSpec[k]
          }
        }
        rawMel[m * tMel + frameIdx] = ln((energy + LOG_ZERO_GUARD).toFloat())
      }
    }

    // 2c. Per-Channel Bessel Normalization (ddof = 1, dividing sum of squared deviations by T_mel - 1)
    val denomBessel = max(1, tMel - 1).toDouble()
    for (m in 0 until N_MELS) {
      val rowOffset = m * tMel
      var sum = 0.0
      for (t in 0 until tMel) {
        sum += rawMel[rowOffset + t]
      }
      val mean = (sum / tMel.toDouble()).toFloat()

      var sumSqDiff = 0.0
      for (t in 0 until tMel) {
        val diff = (rawMel[rowOffset + t] - mean).toDouble()
        sumSqDiff += diff * diff
      }
      val std = sqrt(sumSqDiff / denomBessel).toFloat()
      val invScale = 1.0f / (std + NORM_EPS)

      for (t in 0 until tMel) {
        rawMel[rowOffset + t] = (rawMel[rowOffset + t] - mean) * invScale
      }
    }

    return PreprocessResult.MelFeatures(features = rawMel, numFrames = tMel)
  }

  private fun fft512InPlace(re: FloatArray, im: FloatArray) {
    val bitRev = BIT_REVERSED_512
    for (i in 0 until N_FFT) {
      val j = bitRev[i]
      if (i < j) {
        val tr = re[i]
        re[i] = re[j]
        re[j] = tr
        val ti = im[i]
        im[i] = im[j]
        im[j] = ti
      }
    }

    val cosTable = FFT_COS_TABLE
    val sinTable = FFT_SIN_TABLE

    var len = 2
    while (len <= N_FFT) {
      val halfLen = len shr 1
      val step = N_FFT / len
      var i = 0
      while (i < N_FFT) {
        var k = 0
        for (j in 0 until halfLen) {
          val uIdx = i + j
          val vIdx = uIdx + halfLen
          val wr = cosTable[k]
          val wi = sinTable[k]
          val vr = re[vIdx] * wr - im[vIdx] * wi
          val vi = re[vIdx] * wi + im[vIdx] * wr

          re[vIdx] = re[uIdx] - vr
          im[vIdx] = im[uIdx] - vi
          re[uIdx] += vr
          im[uIdx] += vi
          k += step
        }
        i += len
      }
      len = len shl 1
    }
  }

  /**
   * Builds Slaney-style Mel filterbank (`htk = false`, `norm = "slaney"`).
   */
  private fun buildSlaneyMelFilterbank(
    sr: Int,
    nFft: Int,
    nMels: Int,
    fMin: Double,
    fMax: Double
  ): Array<FloatArray> {
    val numBins = nFft / 2 + 1
    val fftFreqs = DoubleArray(numBins) { k -> (k.toDouble() * sr.toDouble()) / nFft.toDouble() }

    val minMel = hzToMelSlaney(fMin)
    val maxMel = hzToMelSlaney(fMax)
    val melPts = DoubleArray(nMels + 2) { i ->
      minMel + (maxMel - minMel) * i.toDouble() / (nMels + 1).toDouble()
    }
    val hzPts = DoubleArray(nMels + 2) { i -> melToHzSlaney(melPts[i]) }
    val fDiff = DoubleArray(nMels + 1) { i -> hzPts[i + 1] - hzPts[i] }

    val weights = Array(nMels) { FloatArray(numBins) }
    for (m in 0 until nMels) {
      val enorm = 2.0 / (hzPts[m + 2] - hzPts[m])
      for (k in 0 until numBins) {
        val freq = fftFreqs[k]
        val lower = (freq - hzPts[m]) / fDiff[m]
        val upper = (hzPts[m + 2] - freq) / fDiff[m + 1]
        val w = max(0.0, min(lower, upper)) * enorm
        weights[m][k] = w.toFloat()
      }
    }
    return weights
  }

  private fun hzToMelSlaney(hz: Double): Double {
    val fMin = 0.0
    val fSp = 200.0 / 3.0
    val minLogHz = 1000.0
    val minLogMel = (minLogHz - fMin) / fSp
    val logStep = ln(6.4) / 27.0
    return if (hz >= minLogHz) {
      minLogMel + ln(hz / minLogHz) / logStep
    } else {
      (hz - fMin) / fSp
    }
  }

  private fun melToHzSlaney(mel: Double): Double {
    val fMin = 0.0
    val fSp = 200.0 / 3.0
    val minLogHz = 1000.0
    val minLogMel = (minLogHz - fMin) / fSp
    val logStep = ln(6.4) / 27.0
    return if (mel >= minLogMel) {
      minLogHz * kotlin.math.exp(logStep * (mel - minLogMel))
    } else {
      fMin + fSp * mel
    }
  }
}
