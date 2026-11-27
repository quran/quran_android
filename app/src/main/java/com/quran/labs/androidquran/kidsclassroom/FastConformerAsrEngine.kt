package com.quran.labs.androidquran.kidsclassroom

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Manages the ONNX Runtime session for `fastconformer_quran_int8.onnx` with:
 * - `OptLevel.ALL_OPT`
 * - `intraOpNumThreads = 4`, `interOpNumThreads = 1`
 * - `memoryPatternOptimization = true`
 * - XNNPACK Execution Provider (`addXnnpack(mapOf("intra_op_num_threads" to "4"))`)
 * - Dummy warm-up pass `[1, 80, 160]` on `Dispatchers.Default` so the child's first turn has 0 JIT lag.
 * - `ctcTokens: Array<String>(1025)` and `blankId = vocab["<blk>"] ?: 1024`.
 */
object FastConformerAsrEngine {

  const val VOCAB_SIZE = 1025
  const val BLANK_ID = 1024

  private val mutex = Mutex()

  @Volatile
  private var ortEnv: OrtEnvironment? = null

  @Volatile
  private var ortSession: OrtSession? = null

  @Volatile
  private var vocabById: Array<String>? = null

  @Volatile
  private var resolvedBlankId: Int = BLANK_ID

  data class AsrOutput(
    val logprobs: Array<FloatArray>, // [T_out][1025]
    val vocab: Array<String>,
    val blankId: Int = BLANK_ID
  )

  fun isInitialized(): Boolean = ortSession != null && vocabById != null

  fun peekVocab(): Array<String>? = vocabById

  fun peekBlankId(): Int = resolvedBlankId

  suspend fun getOrLoadVocab(context: Context): Array<String>? = withContext(Dispatchers.Default) {
    vocabById?.let { return@withContext it }
    val vocabFile = KidsAssetDownloader.getVocabFile(context)
    if (!vocabFile.exists() || vocabFile.length() <= 0L) return@withContext null
    try {
      val bundle = parseVocabBundleFile(vocabFile)
      vocabById = bundle.ctcTokens
      resolvedBlankId = bundle.blankId
      bundle.ctcTokens
    } catch (ce: CancellationException) {
      throw ce
    } catch (t: Throwable) {
      Timber.e(t, "Failed to parse vocab.json")
      null
    }
  }

  suspend fun ensureInitialized(context: Context): Boolean = withContext(Dispatchers.Default) {
    if (isInitialized()) return@withContext true
    mutex.withLock {
      if (isInitialized()) return@withContext true
      if (!KidsAssetDownloader.areCoreEngineAssetsReady(context)) {
        return@withContext false
      }
      try {
        val vocabFile = KidsAssetDownloader.getVocabFile(context)
        if (vocabById == null) {
          val bundle = parseVocabBundleFile(vocabFile)
          vocabById = bundle.ctcTokens
          resolvedBlankId = bundle.blankId
        }

        val onnxFile = KidsAssetDownloader.getOnnxModelFile(context)
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
          setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
          setIntraOpNumThreads(4)
          setInterOpNumThreads(1)
          setMemoryPatternOptimization(true)
          try {
            addXnnpack(mapOf("intra_op_num_threads" to "4"))
          } catch (t: Throwable) {
            Timber.w(t, "XNNPACK EP unavailable on this device; falling back to default CPU EP")
          }
        }

        val session = env.createSession(onnxFile.absolutePath, opts)
        ortEnv = env
        ortSession = session

        // Warm-up pass [1, 80, 160] so first child recitation has zero graph partitioning latency
        runWarmupPass(env, session)
        true
      } catch (ce: CancellationException) {
        throw ce
      } catch (t: Throwable) {
        Timber.e(t, "Failed to initialize FastConformer ONNX session")
        false
      }
    }
  }

  private fun runWarmupPass(env: OrtEnvironment, session: OrtSession) {
    val warmupFrames = 160
    val dummyFeatures = FloatArray(NeMoMelPreprocessor.N_MELS * warmupFrames)
    val signalTensor = OnnxTensor.createTensor(
      env,
      FloatBuffer.wrap(dummyFeatures),
      longArrayOf(1L, NeMoMelPreprocessor.N_MELS.toLong(), warmupFrames.toLong())
    )
    val lengthTensor = OnnxTensor.createTensor(
      env,
      LongBuffer.wrap(longArrayOf(warmupFrames.toLong())),
      longArrayOf(1L)
    )
    try {
      session.run(
        mapOf(
          "audio_signal" to signalTensor,
          "length" to lengthTensor
        )
      ).use { /* warm-up complete */ }
    } finally {
      signalTensor.close()
      lengthTensor.close()
    }
  }

  /**
   * Runs single-shot full-context ONNX inference on `[1, 80, T_mel]` Mel features.
   */
  suspend fun runInference(
    context: Context,
    melFeatures: FloatArray,
    numFrames: Int
  ): AsrOutput? = withContext(Dispatchers.Default) {
    if (!ensureInitialized(context)) return@withContext null
    val env = ortEnv ?: return@withContext null
    val session = ortSession ?: return@withContext null
    val vocab = vocabById ?: return@withContext null
    val blankId = resolvedBlankId

    mutex.withLock {
      val signalTensor = OnnxTensor.createTensor(
        env,
        FloatBuffer.wrap(melFeatures),
        longArrayOf(1L, NeMoMelPreprocessor.N_MELS.toLong(), numFrames.toLong())
      )
      val lengthTensor = OnnxTensor.createTensor(
        env,
        LongBuffer.wrap(longArrayOf(numFrames.toLong())),
        longArrayOf(1L)
      )
      try {
        session.run(
          mapOf(
            "audio_signal" to signalTensor,
            "length" to lengthTensor
          )
        ).use { results ->
          val firstVal = results[0].value
          val logprobs2d: Array<FloatArray> = when (firstVal) {
            is Array<*> -> {
              @Suppress("UNCHECKED_CAST")
              val batch3d = firstVal as? Array<Array<FloatArray>>
              if (batch3d != null && batch3d.isNotEmpty()) {
                batch3d[0]
              } else {
                @Suppress("UNCHECKED_CAST")
                (firstVal as? Array<FloatArray>) ?: emptyArray()
              }
            }
            else -> emptyArray()
          }
          AsrOutput(logprobs = logprobs2d, vocab = vocab, blankId = blankId)
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (t: Throwable) {
        Timber.e(t, "Error running FastConformer ONNX inference")
        null
      } finally {
        signalTensor.close()
        lengthTensor.close()
      }
    }
  }

  internal fun parseVocabJson(jsonText: String): Array<String> {
    return KidsAssetDownloader.parseVocabBundle(jsonText).ctcTokens
  }

  private fun parseVocabBundleFile(file: File): KidsAssetDownloader.VocabBundle {
    val text = file.readText(Charsets.UTF_8)
    return KidsAssetDownloader.parseVocabBundle(text)
  }
}
