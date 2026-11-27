package com.quran.labs.androidquran.kidsclassroom

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Parsed configuration from `streaming/streaming_config.json`.
 */
data class StreamingAsrConfig(
  val sampleRate: Int = 16000,
  val nMels: Int = 80,
  val nFft: Int = 512,
  val winLength: Int = 400,
  val hopLength: Int = 160,
  val blankId: Int = 1024,
  val chunkMs: Int = 560,
  val cacheChannelShape: LongArray = longArrayOf(1L, 17L, 70L, 512L),
  val cacheTimeShape: LongArray = longArrayOf(1L, 17L, 512L, 8L),
  val preEncodeMelCacheFrames: Int = 9,
  val dropExtraPreEncoded: Int = 2,
  val welfordPriorCount: Int = 50,
  val welfordPriorMean: FloatArray? = null,
  val welfordPriorStd: FloatArray? = null
) {
  companion object {
    private const val TAG = "StreamingAsrConfig"

    fun parse(file: File?): StreamingAsrConfig {
      if (file == null || !file.exists()) return StreamingAsrConfig()
      return try {
        parseJson(file.readText())
      } catch (t: Throwable) {
        Log.w(TAG, "Failed parsing streaming_config.json, using defaults", t)
        StreamingAsrConfig()
      }
    }

    fun parseJson(jsonText: String): StreamingAsrConfig {
      val root = JSONObject(jsonText)
      val frontendObj = root.optJSONObject("frontend")
      val cacheShapesObj = root.optJSONObject("cache_shapes")

      val sampleRate = frontendObj?.optInt("sample_rate", 16000)
        ?: root.optInt("sample_rate", 16000)
      val nMels = frontendObj?.optInt("n_mels", 80)
        ?: root.optInt("n_mels", root.optInt("features", 80))
      val nFft = frontendObj?.optInt("n_fft", 512) ?: root.optInt("n_fft", 512)
      val winLength = frontendObj?.optInt("win_length", 400) ?: root.optInt("win_length", 400)
      val hopLength = frontendObj?.optInt("hop_length", 160) ?: root.optInt("hop_length", 160)
      val blankId = root.optInt("blank_id", 1024)
      val chunkMs = root.optInt("chunk_ms", root.optInt("default_chunk_ms", 560))

      val cacheChannelShape =
        (cacheShapesObj?.let { readLongArray(it, "cache_last_channel") })
          ?: readLongArray(root, "cache_last_channel_shape")
          ?: longArrayOf(
            1L,
            root.optLong("num_layers", 17L),
            root.optLong("cache_size", 70L),
            root.optLong("d_model", 512L)
          )
      val cacheTimeShape =
        (cacheShapesObj?.let { readLongArray(it, "cache_last_time") })
          ?: readLongArray(root, "cache_last_time_shape")
          ?: longArrayOf(
            1L,
            root.optLong("num_layers", 17L),
            root.optLong("d_model", 512L),
            root.optLong("conv_context", 8L)
          )

      val preEncodeMelCacheFrames =
        cacheShapesObj?.optInt("pre_encode_mel_cache_frames", 9)
          ?: root.optInt("pre_encode_mel_cache_frames", 9)
      val dropExtraPreEncoded =
        cacheShapesObj?.optInt("drop_extra_pre_encoded", 2)
          ?: root.optInt("drop_extra_pre_encoded", 2)

      val welfordObj = frontendObj ?: root.optJSONObject("welford") ?: root.optJSONObject("cmvn_priors")
      val n0 = welfordObj?.optInt(
        "welford_prior_count",
        welfordObj.optInt("n0", welfordObj.optInt("prior_count", 50))
      ) ?: root.optInt("welford_n0", root.optInt("n0", 50))

      val priorMean =
        readFloatArray(welfordObj ?: root, "welford_prior_mean", "mean", "prior_mean", "welford_mean")
          ?: readFloatArray(root, "welford_prior_mean", "mean", "prior_mean", "welford_mean")

      val priorStdDirect =
        readFloatArray(welfordObj ?: root, "welford_prior_std", "std", "prior_std", "welford_std")
          ?: readFloatArray(root, "welford_prior_std", "std", "prior_std", "welford_std")
      val priorVar =
        readFloatArray(welfordObj ?: root, "welford_prior_var", "var", "prior_var", "welford_var")
          ?: readFloatArray(root, "welford_prior_var", "var", "prior_var", "welford_var")
      val priorStd = priorStdDirect ?: priorVar?.let { varArr ->
        FloatArray(varArr.size) { idx -> sqrt(varArr[idx].toDouble().coerceAtLeast(1e-6)).toFloat() }
      }

      return StreamingAsrConfig(
        sampleRate = sampleRate,
        nMels = nMels,
        nFft = nFft,
        winLength = winLength,
        hopLength = hopLength,
        blankId = blankId,
        chunkMs = chunkMs,
        cacheChannelShape = cacheChannelShape,
        cacheTimeShape = cacheTimeShape,
        preEncodeMelCacheFrames = preEncodeMelCacheFrames.coerceAtLeast(0),
        dropExtraPreEncoded = dropExtraPreEncoded.coerceAtLeast(0),
        welfordPriorCount = n0.coerceAtLeast(1),
        welfordPriorMean = priorMean?.takeIf { it.size == nMels },
        welfordPriorStd = priorStd?.takeIf { it.size == nMels }
      )
    }

    private fun readLongArray(obj: JSONObject, key: String): LongArray? {
      val arr = obj.optJSONArray(key) ?: return null
      if (arr.length() == 0) return null
      return LongArray(arr.length()) { arr.optLong(it) }
    }

    private fun readFloatArray(obj: JSONObject, vararg keys: String): FloatArray? {
      for (k in keys) {
        val arr = obj.optJSONArray(k) ?: continue
        if (arr.length() > 0) {
          return FloatArray(arr.length()) { arr.optDouble(it, 0.0).toFloat() }
        }
      }
      return null
    }
  }
}

/**
 * Causal online per-mel-bin Welford normalizer (`0 ms` lookahead) seeded with priors (`N0 = 50`)
 * from `streaming/streaming_config.json`.
 *
 * Updates running mean and variance causally frame-by-frame so live streaming inference never waits
 * for future audio frames.
 */
class CausalWelfordOnlineNormalizer(
  private val nMels: Int = 80,
  private val priorCount: Int = 50,
  private val priorMean: FloatArray? = null,
  private val priorStd: FloatArray? = null
) {
  private val counts = DoubleArray(nMels)
  private val means = DoubleArray(nMels)
  private val m2 = DoubleArray(nMels)

  init {
    reset()
  }

  fun reset() {
    val n0 = priorCount.coerceAtLeast(1).toDouble()
    for (m in 0 until nMels) {
      val initMean = priorMean?.getOrNull(m)?.toDouble() ?: DEFAULT_LOG_MEL_MEAN
      val initStd = (priorStd?.getOrNull(m)?.toDouble() ?: DEFAULT_LOG_MEL_STD).coerceAtLeast(1e-3)
      counts[m] = n0
      means[m] = initMean
      m2[m] = initStd * initStd * max(1.0, n0 - 1.0)
    }
  }

  /**
   * Causally updates running statistics with a single log-mel frame `[nMels]` and returns
   * the normalized frame without peeking at future frames.
   */
  fun normalizeFrameInPlace(frame: FloatArray) {
    val limit = minOf(nMels, frame.size)
    for (m in 0 until limit) {
      val x = frame[m].toDouble()
      val nextCount = counts[m] + 1.0
      val delta = x - means[m]
      val nextMean = means[m] + delta / nextCount
      val delta2 = x - nextMean
      val nextM2 = m2[m] + delta * delta2

      counts[m] = nextCount
      means[m] = nextMean
      m2[m] = nextM2

      val variance = if (nextCount > 1.0) nextM2 / (nextCount - 1.0) else 1.0
      val std = sqrt(max(variance, 1e-10)) + 1e-5
      frame[m] = ((x - nextMean) / std).toFloat()
    }
  }

  fun currentCount(melBin: Int = 0): Double = counts.getOrElse(melBin) { 0.0 }

  companion object {
    private const val DEFAULT_LOG_MEL_MEAN = -7.5
    private const val DEFAULT_LOG_MEL_STD = 3.2
  }
}

/**
 * Full-utterance CMVN normalizer delegating to [NeMoMelPreprocessor] for Stage-4 offline batch
 * grading (`fastconformer_quran_int8.onnx`, `4.72%` WER).
 */
class UtteranceCmvnNormalizer {
  fun extractNormalized(
    pcm16k: ShortArray,
    minPeakRms: Float = NeMoMelPreprocessor.MIN_PEAK_RMS
  ): NeMoMelPreprocessor.PreprocessResult {
    val floatPcm = NeMoMelPreprocessor.pcm16ToFloat32(pcm16k)
    return NeMoMelPreprocessor.process(floatPcm, minPeakRms = minPeakRms)
  }

  fun extractNormalizedFloat(
    pcmFloat32: FloatArray,
    minPeakRms: Float = NeMoMelPreprocessor.MIN_PEAK_RMS
  ): NeMoMelPreprocessor.PreprocessResult =
    NeMoMelPreprocessor.process(pcmFloat32, minPeakRms = minPeakRms)
}

/**
 * Dual-mode Mel feature extractor supporting:
 * 1. Exact binary filterbank loading from `streaming/mel_basis_80x257.bin` (little-endian `float32[80][257]`)
 *    with causal online Welford normalization (`0 ms` lookahead) for [StreamingQuranAsrEngine].
 * 2. Full-utterance CMVN extraction via [UtteranceCmvnNormalizer] for [OfflineBatchQuranAsrEngine].
 */
class MelFeatureExtractor(
  melBasisFile: File? = null
) {
  companion object {
    private const val TAG = "MelFeatureExtractor"
    const val N_MELS = 80
    const val N_FREQ_BINS = 257
    const val N_FFT = 512
    const val WIN_LENGTH = 400
    const val HOP_LENGTH = 160
    const val PREEMPH = 0.97f
    private const val LOG_GUARD = 5.9604645e-8 // 2^-24

    /**
     * Loads a little-endian `float32[80][257]` binary Mel filterbank matrix from [file].
     */
    fun loadMelBasisBinary(file: File): Array<FloatArray>? {
      val expectedBytes = N_MELS * N_FREQ_BINS * 4
      if (!file.exists() || file.length() < expectedBytes) return null
      return try {
        val raw = file.readBytes()
        if (raw.size < expectedBytes) return null
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        Array(N_MELS) {
          FloatArray(N_FREQ_BINS) { bb.float }
        }
      } catch (t: Throwable) {
        Log.w(TAG, "Failed loading mel_basis_80x257.bin; falling back to built-in Slaney basis", t)
        null
      }
    }
  }

  private val melBasis: Array<FloatArray> =
    melBasisFile?.let { loadMelBasisBinary(it) } ?: NeMoMelPreprocessor.MEL_BASIS_80X257

  // Center-padded 400-sample symmetric Hann window inside 512-point FFT (56 zeros on each side),
  // matching NeMo AudioToMelSpectrogramPreprocessor / torch.stft(center=True).
  private val paddedHannWindow512: FloatArray = FloatArray(N_FFT).apply {
    val leftPad = (N_FFT - WIN_LENGTH) / 2 // 56
    for (n in 0 until WIN_LENGTH) {
      this[leftPad + n] = (0.5 - 0.5 * cos(2.0 * PI * n / (WIN_LENGTH - 1))).toFloat()
    }
  }

  // Carry-over buffer between streaming PCM chunks so STFT framing is phase-continuous
  // Initialized with N_FFT/2 = 256 zeros for NeMo center=True STFT alignment on chunk 0.
  private var tailSample: Float = 0f
  private var carrySamples: FloatArray = FloatArray(N_FFT / 2)
  private var hasStartedStream: Boolean = false
  private var preEncodeMelCache: Array<FloatArray> = emptyArray()

  fun resetStreamingState() {
    tailSample = 0f
    carrySamples = FloatArray(N_FFT / 2)
    hasStartedStream = false
    preEncodeMelCache = emptyArray()
  }

  /**
   * Extracts raw log-mel frames `[T][80]` for an incoming 16 kHz PCM chunk with streaming carry-over.
   */
  fun extractChunkLogMelFrames(pcmChunk: ShortArray): Array<FloatArray> {
    if (pcmChunk.isEmpty()) return emptyArray()

    // Apply streaming pre-emphasis continuously across chunk boundaries
    val preemph = FloatArray(pcmChunk.size)
    var prev = tailSample
    for (i in pcmChunk.indices) {
      val x = pcmChunk[i].toFloat() / 32768f
      preemph[i] = if (!hasStartedStream && i == 0) x else (x - PREEMPH * prev)
      prev = x
    }
    tailSample = prev
    hasStartedStream = true

    // Concatenate with leftover samples from previous chunk
    val combined = FloatArray(carrySamples.size + preemph.size)
    if (carrySamples.isNotEmpty()) {
      System.arraycopy(carrySamples, 0, combined, 0, carrySamples.size)
    }
    System.arraycopy(preemph, 0, combined, carrySamples.size, preemph.size)

    if (combined.size < N_FFT) {
      carrySamples = combined
      return emptyArray()
    }

    val numFrames = 1 + (combined.size - N_FFT) / HOP_LENGTH
    val consumed = numFrames * HOP_LENGTH
    val remaining = (combined.size - consumed).coerceAtLeast(0)
    carrySamples = if (remaining > 0) {
      combined.copyOfRange(consumed, combined.size)
    } else {
      FloatArray(0)
    }

    val frames = Array(numFrames) { FloatArray(N_MELS) }
    val real = DoubleArray(N_FFT)
    val imag = DoubleArray(N_FFT)
    val power = DoubleArray(N_FREQ_BINS)

    for (t in 0 until numFrames) {
      val offset = t * HOP_LENGTH
      for (i in 0 until N_FFT) {
        real[i] = (combined[offset + i] * paddedHannWindow512[i]).toDouble()
        imag[i] = 0.0
      }
      fftRadix2(real, imag)

      for (k in 0 until N_FREQ_BINS) {
        power[k] = real[k] * real[k] + imag[k] * imag[k]
      }

      val melFrame = frames[t]
      for (m in 0 until N_MELS) {
        val weights = melBasis[m]
        var energy = 0.0
        for (k in 0 until N_FREQ_BINS) {
          val w = weights[k]
          if (w != 0f) energy += w * power[k]
        }
        melFrame[m] = ln(energy + LOG_GUARD).toFloat()
      }
    }

    return frames
  }

  /**
   * Extracts a causally normalized `[1, 80, T]` flattened tensor buffer ready for
   * [StreamingQuranAsrEngine] ONNX inference.
   */
  fun extractCausalNormalizedChunk(
    pcmChunk: ShortArray,
    normalizer: CausalWelfordOnlineNormalizer
  ): Pair<FloatArray, Int> {
    val frames = extractChunkLogMelFrames(pcmChunk)
    val numFrames = frames.size
    if (numFrames == 0) return FloatArray(0) to 0

    for (t in 0 until numFrames) {
      normalizer.normalizeFrameInPlace(frames[t])
    }

    // Transpose [T][80] -> [80][T] contiguous layout for ONNX [1, 80, T]
    val features = FloatArray(N_MELS * numFrames)
    for (m in 0 until N_MELS) {
      val rowOffset = m * numFrames
      for (t in 0 until numFrames) {
        features[rowOffset + t] = frames[t][m]
      }
    }
    return features to numFrames
  }

  /**
   * Extracts causally normalized mel frames and prepends `preEncodeCacheFrames` (9 frames)
   * from the previous chunk (or zeros on chunk 0), matching NeMo's cache-aware FastConformer
   * streaming contract (`input_mel_window = preEncodeCacheFrames + newFrames`, e.g. `9 + 56 = 65`).
   *
   * @return Triple of `(flattened [1, 80, totalFrames], totalFrames, newFrames)`
   */
  fun extractCausalNormalizedWindowWithPreEncodeCache(
    pcmChunk: ShortArray,
    normalizer: CausalWelfordOnlineNormalizer,
    preEncodeCacheFrames: Int
  ): Triple<FloatArray, Int, Int> {
    val newFrames = extractChunkLogMelFrames(pcmChunk)
    val newCount = newFrames.size
    if (newCount == 0) return Triple(FloatArray(0), 0, 0)

    for (t in 0 until newCount) {
      normalizer.normalizeFrameInPlace(newFrames[t])
    }

    val cacheLen = preEncodeCacheFrames.coerceAtLeast(0)
    val prefix: Array<FloatArray> = if (cacheLen == 0) {
      emptyArray()
    } else if (preEncodeMelCache.size == cacheLen) {
      preEncodeMelCache
    } else {
      Array(cacheLen) { idx ->
        val srcIdx = idx - (cacheLen - preEncodeMelCache.size)
        if (srcIdx in preEncodeMelCache.indices) preEncodeMelCache[srcIdx] else FloatArray(N_MELS)
      }
    }

    val totalFrames = prefix.size + newCount
    val features = FloatArray(N_MELS * totalFrames)
    for (m in 0 until N_MELS) {
      val rowOffset = m * totalFrames
      for (t in prefix.indices) {
        features[rowOffset + t] = prefix[t][m]
      }
      for (t in 0 until newCount) {
        features[rowOffset + prefix.size + t] = newFrames[t][m]
      }
    }

    if (cacheLen > 0) {
      val nextCache = Array(cacheLen) { FloatArray(N_MELS) }
      val allCount = prefix.size + newCount
      val start = (allCount - cacheLen).coerceAtLeast(0)
      for (i in 0 until cacheLen) {
        val src = start + i
        val frame = if (src < prefix.size) prefix[src] else newFrames[src - prefix.size]
        System.arraycopy(frame, 0, nextCache[i], 0, N_MELS)
      }
      preEncodeMelCache = nextCache
    }

    return Triple(features, totalFrames, newCount)
  }

  private fun fftRadix2(real: DoubleArray, imag: DoubleArray) {
    val n = real.size
    var j = 0
    for (i in 1 until n) {
      var bit = n shr 1
      while (j and bit != 0) {
        j = j xor bit
        bit = bit shr 1
      }
      j = j xor bit
      if (i < j) {
        val tr = real[i]
        real[i] = real[j]
        real[j] = tr
        val ti = imag[i]
        imag[i] = imag[j]
        imag[j] = ti
      }
    }
    var len = 2
    while (len <= n) {
      val ang = -2.0 * PI / len
      val wLenR = cos(ang)
      val wLenI = kotlin.math.sin(ang)
      var i = 0
      while (i < n) {
        var wr = 1.0
        var wi = 0.0
        val half = len shr 1
        for (k in 0 until half) {
          val uR = real[i + k]
          val uI = imag[i + k]
          val vR = real[i + k + half] * wr - imag[i + k + half] * wi
          val vI = real[i + k + half] * wi + imag[i + k + half] * wr
          real[i + k] = uR + vR
          imag[i + k] = uI + vI
          real[i + k + half] = uR - vR
          imag[i + k + half] = uI - vI
          val nextWr = wr * wLenR - wi * wLenI
          wi = wr * wLenI + wi * wLenR
          wr = nextWr
        }
        i += len
      }
      len = len shl 1
    }
  }
}
