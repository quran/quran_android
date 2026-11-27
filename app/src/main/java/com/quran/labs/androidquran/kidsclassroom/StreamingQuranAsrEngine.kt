package com.quran.labs.androidquran.kidsclassroom

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.quran.labs.androidquran.memorization.tasmee.TasmeeFollowAlongAlignment
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Output of a single incremental streaming ASR step (~160 ms, 560 ms, or 1120 ms chunk).
 */
data class StreamingChunkResult(
  val partialTranscript: String = "",
  val decodedWords: List<String> = emptyList(),
  val matchedWordCount: Int = 0,
  val newlyMatchedWordIndices: List<Int> = emptyList(),
  val allWordsMatched: Boolean = false
)

/**
 * Stateful cache-aware streaming ONNX ASR engine (`streaming/fastconformer_quran_streaming_int8.onnx`,
 * `0 ms` lookahead).
 *
 * Maintains explicit FastConformer encoder caches across streaming chunks:
 * - `cache_last_channel`: `[1, 17, 70, 512]`
 * - `cache_last_time`: `[1, 17, 512, 8]`
 * - `cache_last_channel_len`: `[1]`
 *
 * Properly closes previous `OnnxTensor`s and `OrtSession.Result`s on every step to prevent native
 * memory leaks, and performs incremental greedy CTC decoding (`blank_id = 1024`) to highlight
 * matched words in real time as the child recites.
 */
class StreamingQuranAsrEngine {

  companion object {
    private const val TAG = "StreamingQuranAsrEngine"
    const val CHUNK_160_MS = 160
    const val CHUNK_560_MS = 560
    const val CHUNK_1120_MS = 1120
    const val DEFAULT_BLANK_ID = 1024
  }

  private val lock = Any()
  private var ortEnv: OrtEnvironment? = null
  private var ortSession: OrtSession? = null
  private var vocab: Array<String> = emptyArray()
  private var config: StreamingAsrConfig = StreamingAsrConfig()
  private var featureExtractor: MelFeatureExtractor? = null
  private var welfordNormalizer: CausalWelfordOnlineNormalizer? = null

  // Stateful ONNX cache buffers kept across chunks (re-wrapped into fresh OnnxTensors per step)
  private var cacheChannelData: FloatArray = FloatArray(0)
  private var cacheChannelShape: LongArray = longArrayOf(1L, 17L, 70L, 512L)
  private var cacheTimeData: FloatArray = FloatArray(0)
  private var cacheTimeShape: LongArray = longArrayOf(1L, 17L, 512L, 8L)
  private var cacheChannelLen: Long = 0L

  // Incremental CTC decode state across chunk boundaries
  private var lastCtcTokenId: Int = -1
  private val emittedTokenIds = ArrayList<Int>(128)
  private var matchedWordCount: Int = 0

  @Volatile
  var chunkDurationMs: Int = CHUNK_560_MS
    private set

  @Volatile
  var isLoaded: Boolean = false
    private set

  fun chunkSamples(): Int = (config.sampleRate * chunkDurationMs) / 1000

  fun setChunkDurationMs(ms: Int) {
    chunkDurationMs = when (ms) {
      CHUNK_160_MS, CHUNK_560_MS, CHUNK_1120_MS -> ms
      else -> CHUNK_560_MS
    }
  }

  fun load(context: Context): Boolean = synchronized(lock) {
    if (isLoaded && ortSession != null) return true
    val modelFile = AsrModelManager.getStreamingOnnxFile(context)
    val vocabFile = AsrModelManager.getVocabFile(context)
    val configFile = AsrModelManager.getStreamingConfigFile(context)
    val melBasisFile = AsrModelManager.getStreamingMelBasisFile(context)

    if (!modelFile.exists() || !vocabFile.exists()) {
      Log.w(TAG, "Streaming ASR files not ready on disk yet.")
      return false
    }

    return try {
      config = StreamingAsrConfig.parse(configFile)
      setChunkDurationMs(config.chunkMs)
      val vocabBundle = KidsAssetDownloader.parseVocabBundle(vocabFile.readText(Charsets.UTF_8))
      vocab = vocabBundle.ctcTokens
      if (config.blankId != vocabBundle.blankId) {
        config = config.copy(blankId = vocabBundle.blankId)
      }
      featureExtractor = MelFeatureExtractor(melBasisFile)
      welfordNormalizer = CausalWelfordOnlineNormalizer(
        nMels = config.nMels,
        priorCount = config.welfordPriorCount,
        priorMean = config.welfordPriorMean,
        priorStd = config.welfordPriorStd
      )

      val env = OrtEnvironment.getEnvironment()
      ortEnv = env
      val opts = OrtSession.SessionOptions().apply {
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        setIntraOpNumThreads(threads)
        setInterOpNumThreads(1)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        try {
          addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
        } catch (_: Throwable) {
          // Standard CPU EP fallback
        }
      }
      val session = env.createSession(modelFile.absolutePath, opts)
      ortSession = session
      resetStreamStateLocked()
      isLoaded = true
      val nonEmptyVocab = vocab.count { it.isNotEmpty() }
      Log.i(
        TAG,
        "StreamingQuranAsrEngine loaded: chunk=${chunkDurationMs}ms, vocab=$nonEmptyVocab, " +
          "blankId=${config.blankId}, welfordPriors=${config.welfordPriorMean != null}/${config.welfordPriorStd != null}, " +
          "preEncodeCache=${config.preEncodeMelCacheFrames}, inputs=${session.inputNames}, outputs=${session.outputNames}"
      )
      true
    } catch (t: Throwable) {
      Log.e(TAG, "Failed loading StreamingQuranAsrEngine", t)
      closeLocked()
      false
    }
  }

  /**
   * Resets encoder caches, causal Welford normalizer, and CTC decoder state before a new turn.
   */
  fun resetStreamState() = synchronized(lock) {
    resetStreamStateLocked()
  }

  private fun resetStreamStateLocked() {
    cacheChannelShape = config.cacheChannelShape.clone()
    cacheTimeShape = config.cacheTimeShape.clone()
    cacheChannelData = FloatArray(productOf(cacheChannelShape))
    cacheTimeData = FloatArray(productOf(cacheTimeShape))
    cacheChannelLen = 0L
    lastCtcTokenId = -1
    emittedTokenIds.clear()
    matchedWordCount = 0
    featureExtractor?.resetStreamingState()
    welfordNormalizer?.reset()
  }

  /**
   * Processes one 16 kHz PCM chunk (`~560 ms` by default) through the stateful cache-aware ONNX
   * encoder, updates caches safely while closing all temporary `OnnxTensor`s, and returns live
   * word-match updates against [targetNormalizedWords] / [targetQariWords].
   */
  fun processAudioChunk(
    pcmChunk: ShortArray,
    targetNormalizedWords: List<String>,
    targetQariWords: List<String> = targetNormalizedWords
  ): StreamingChunkResult = synchronized(lock) {
    val session = ortSession ?: return StreamingChunkResult()
    val env = ortEnv ?: return StreamingChunkResult()
    val extractor = featureExtractor ?: return StreamingChunkResult()
    val normalizer = welfordNormalizer ?: return StreamingChunkResult()

    if (pcmChunk.isEmpty()) return StreamingChunkResult()
    val (features, numFrames, newFrames) = extractor.extractCausalNormalizedWindowWithPreEncodeCache(
      pcmChunk = pcmChunk,
      normalizer = normalizer,
      preEncodeCacheFrames = config.preEncodeMelCacheFrames
    )
    if (numFrames <= 0 || newFrames <= 0) {
      return buildCurrentResult(targetNormalizedWords, targetQariWords, emptyList())
    }
    val cacheLenBefore = cacheChannelLen
    val expectedNewOutFrames = ((newFrames + 7) / 8).coerceAtLeast(1)

    val inputNames = session.inputNames
    val signalName = inputNames.firstOrNull {
      it.contains("signal") && !it.contains("length")
    } ?: "audio_signal"
    val lengthName = inputNames.firstOrNull {
      it.contains("length") && !it.contains("cache")
    } ?: "length"
    val cacheChName = inputNames.firstOrNull { it.contains("cache_last_channel") && !it.contains("len") }
      ?: "cache_last_channel"
    val cacheTimeName = inputNames.firstOrNull { it.contains("cache_last_time") }
      ?: "cache_last_time"
    val cacheLenName = inputNames.firstOrNull { it.contains("cache_last_channel_len") }
      ?: "cache_last_channel_len"

    var signalTensor: OnnxTensor? = null
    var lengthTensor: OnnxTensor? = null
    var cacheChTensor: OnnxTensor? = null
    var cacheTimeTensor: OnnxTensor? = null
    var cacheLenTensor: OnnxTensor? = null

    try {
      signalTensor = OnnxTensor.createTensor(
        env,
        FloatBuffer.wrap(features),
        longArrayOf(1L, config.nMels.toLong(), numFrames.toLong())
      )
      lengthTensor = OnnxTensor.createTensor(
        env,
        LongBuffer.wrap(longArrayOf(numFrames.toLong())),
        longArrayOf(1L)
      )
      cacheChTensor = OnnxTensor.createTensor(
        env,
        FloatBuffer.wrap(cacheChannelData),
        cacheChannelShape
      )
      cacheTimeTensor = OnnxTensor.createTensor(
        env,
        FloatBuffer.wrap(cacheTimeData),
        cacheTimeShape
      )
      cacheLenTensor = OnnxTensor.createTensor(
        env,
        LongBuffer.wrap(longArrayOf(cacheChannelLen)),
        longArrayOf(1L)
      )

      val inputs = LinkedHashMap<String, OnnxTensor>(5).apply {
        put(signalName, signalTensor)
        put(lengthName, lengthTensor)
        if (cacheChName in inputNames) put(cacheChName, cacheChTensor)
        if (cacheTimeName in inputNames) put(cacheTimeName, cacheTimeTensor)
        if (cacheLenName in inputNames) put(cacheLenName, cacheLenTensor)
      }

      session.run(inputs).use { results ->
        // 1. Extract logprobs from output[0] and decode incremental CTC frames
        val logitsTensor = results[0] as? OnnxTensor
        if (logitsTensor != null) {
          decodeLogitsTensorIncremental(
            logitsTensor = logitsTensor,
            cacheLenBefore = cacheLenBefore,
            expectedNewOutFrames = expectedNewOutFrames
          )
        }

        // 2. Copy updated encoder caches out before closing OrtSession.Result
        updateCachesFromOutputs(session, results)
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Streaming ONNX chunk inference error", t)
    } finally {
      // Strictly close all input OnnxTensors every chunk step to prevent native heap leaks
      runCatching { signalTensor?.close() }
      runCatching { lengthTensor?.close() }
      runCatching { cacheChTensor?.close() }
      runCatching { cacheTimeTensor?.close() }
      runCatching { cacheLenTensor?.close() }
    }

    val prevMatched = matchedWordCount
    val nextMatched = computeMonotonicWordMatches(
      targetNormalizedWords = targetNormalizedWords,
      targetQariWords = targetQariWords,
      currentMatched = prevMatched,
      decodedWords = decodeEmittedWords()
    )
    matchedWordCount = nextMatched
    val newlyMatched = if (nextMatched > prevMatched) {
      (prevMatched until nextMatched).toList()
    } else {
      emptyList()
    }
    return buildCurrentResult(targetNormalizedWords, targetQariWords, newlyMatched)
  }

  private fun decodeLogitsTensorIncremental(
    logitsTensor: OnnxTensor,
    cacheLenBefore: Long,
    expectedNewOutFrames: Int
  ) {
    val info = logitsTensor.info
    val shape = info.shape
    if (shape.size < 3) return
    val timeSteps = shape[1].toInt()
    val vocabSize = shape[2].toInt()
    val fb = logitsTensor.floatBuffer ?: return
    val blankId = config.blankId.coerceIn(0, vocabSize - 1)

    // If the ONNX graph did not drop pre-encoded overlap frames internally on subsequent chunks,
    // skip the redundant leading frames so chunk boundaries never double-emit tokens.
    val startT = if (cacheLenBefore > 0L && timeSteps > expectedNewOutFrames && config.dropExtraPreEncoded > 0) {
      (timeSteps - expectedNewOutFrames).coerceIn(0, config.dropExtraPreEncoded)
    } else {
      0
    }

    for (t in startT until timeSteps) {
      var bestId = blankId
      var bestVal = Float.NEGATIVE_INFINITY
      val base = t * vocabSize
      for (v in 0 until vocabSize) {
        val score = fb.get(base + v)
        if (score > bestVal) {
          bestVal = score
          bestId = v
        }
      }
      if (bestId != blankId && bestId != lastCtcTokenId) {
        emittedTokenIds.add(bestId)
      }
      lastCtcTokenId = bestId
    }
  }

  private fun updateCachesFromOutputs(session: OrtSession, results: OrtSession.Result) {
    val outNames = session.outputNames.toList()
    outNames.forEachIndexed { idx, name ->
      val value = results[idx] as? OnnxTensor ?: return@forEachIndexed
      when {
        name.contains("cache_last_channel") && name.contains("len") -> {
          val lb = value.longBuffer
          if (lb != null && lb.remaining() > 0) {
            cacheChannelLen = lb.get(0)
          }
        }
        name.contains("cache_last_channel") -> {
          val fb = value.floatBuffer
          if (fb != null) {
            val shape = value.info.shape
            val total = productOf(shape)
            if (cacheChannelData.size != total) {
              cacheChannelData = FloatArray(total)
            }
            fb.rewind()
            fb.get(cacheChannelData, 0, minOf(total, fb.remaining()))
            cacheChannelShape = shape.clone()
          }
        }
        name.contains("cache_last_time") -> {
          val fb = value.floatBuffer
          if (fb != null) {
            val shape = value.info.shape
            val total = productOf(shape)
            if (cacheTimeData.size != total) {
              cacheTimeData = FloatArray(total)
            }
            fb.rewind()
            fb.get(cacheTimeData, 0, minOf(total, fb.remaining()))
            cacheTimeShape = shape.clone()
          }
        }
      }
    }
  }

  private fun decodeEmittedWords(): List<String> {
    if (emittedTokenIds.isEmpty() || vocab.isEmpty()) return emptyList()
    val sb = StringBuilder(emittedTokenIds.size * 3)
    for (id in emittedTokenIds) {
      val piece = vocab.getOrNull(id) ?: continue
      if (piece == "<blk>" || piece == "<unk>" || piece == "<pad>") continue
      sb.append(piece.replace('\u2581', ' '))
    }
    return sb.toString().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
  }

  private fun computeMonotonicWordMatches(
    targetNormalizedWords: List<String>,
    targetQariWords: List<String>,
    currentMatched: Int,
    decodedWords: List<String>
  ): Int {
    if (targetNormalizedWords.isEmpty() || decodedWords.isEmpty()) return currentMatched
    val spec = KidsTasmeeRevealMatcher.AyahRevealSpec(
      uthmaniWords = targetNormalizedWords,
      normalizedWords = targetNormalizedWords,
      qariOnnxWords = targetQariWords,
      wordTokenIds = List(targetNormalizedWords.size) { IntArray(0) }
    )
    return KidsTasmeeRevealMatcher.computeNextRevealedCount(
      spec = spec,
      currentRevealedCount = currentMatched,
      hypWords = decodedWords,
      logprobs = emptyArray(),
      hadSilenceGapSinceLastReveal = false
    )
  }

  private fun buildCurrentResult(
    targetNormalizedWords: List<String>,
    targetQariWords: List<String>,
    newlyMatched: List<Int>
  ): StreamingChunkResult {
    val words = decodeEmittedWords()
    val totalTarget = targetNormalizedWords.size
    return StreamingChunkResult(
      partialTranscript = words.joinToString(" "),
      decodedWords = words,
      matchedWordCount = matchedWordCount,
      newlyMatchedWordIndices = newlyMatched,
      allWordsMatched = totalTarget > 0 && matchedWordCount >= totalTarget
    )
  }

  fun close() = synchronized(lock) {
    closeLocked()
  }

  private fun closeLocked() {
    runCatching { ortSession?.close() }
    ortSession = null
    isLoaded = false
  }

  private fun productOf(shape: LongArray): Int {
    var prod = 1L
    for (d in shape) prod *= d.coerceAtLeast(1L)
    return prod.toInt().coerceAtLeast(1)
  }

  internal fun parseVocab(vocabFile: File): Array<String> {
    return KidsAssetDownloader.parseVocabBundle(vocabFile.readText(Charsets.UTF_8)).ctcTokens
  }
}
