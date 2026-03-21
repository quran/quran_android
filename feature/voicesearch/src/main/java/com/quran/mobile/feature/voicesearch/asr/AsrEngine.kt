package com.quran.mobile.feature.voicesearch.asr

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.quran.data.di.AppScope
import com.quran.common.search.SearchTextUtil
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

@SingleIn(AppScope::class)
class AsrEngine @Inject constructor(
  private val modelManager: AsrModelManager
) {

  @Volatile
  private var recognizer: OfflineRecognizer? = null
  private val recognizerLock = Any()

  private fun getOrCreateRecognizer(): OfflineRecognizer {
    recognizer?.let { return it }
    synchronized(recognizerLock) {
      recognizer?.let { return it }

      // Validate model files exist before passing to native code
      val encoderFile = File(modelManager.encoderPath)
      val decoderFile = File(modelManager.decoderPath)
      val tokensFile = File(modelManager.tokensPath)

      if (!encoderFile.exists() || !decoderFile.exists() || !tokensFile.exists()) {
        throw IllegalStateException(
          "Model files missing: encoder=${encoderFile.exists()}, decoder=${decoderFile.exists()}, tokens=${tokensFile.exists()}"
        )
      }

      Timber.d("Creating recognizer with encoder=%s (%d bytes), decoder=%s (%d bytes), tokens=%s (%d bytes)",
        encoderFile.name, encoderFile.length(),
        decoderFile.name, decoderFile.length(),
        tokensFile.name, tokensFile.length()
      )

      val whisperConfig = OfflineWhisperModelConfig(
        encoder = modelManager.encoderPath,
        decoder = modelManager.decoderPath,
        language = "ar",
        task = "transcribe"
      )

      val modelConfig = OfflineModelConfig(
        whisper = whisperConfig,
        tokens = modelManager.tokensPath,
        numThreads = 4,
        debug = true
      )

      val config = OfflineRecognizerConfig(
        modelConfig = modelConfig,
        decodingMethod = "greedy_search"
      )

      return OfflineRecognizer(config = config).also { recognizer = it }
    }
  }

  suspend fun transcribeSamples(samples: FloatArray, sampleRate: Int): TranscriptionResult {
    return withContext(Dispatchers.Default) {
      val startTime = System.currentTimeMillis()
      try {
        val rec = getOrCreateRecognizer()

        val stream = rec.createStream()
        stream.acceptWaveform(samples, sampleRate)
        rec.decode(stream)
        val result = rec.getResult(stream)
        stream.release()

        val text = result.text.trim()
        val durationMs = System.currentTimeMillis() - startTime

        TranscriptionResult(
          text = text,
          normalizedText = SearchTextUtil.normalizeArabic(text),
          durationMs = durationMs
        )
      } catch (e: Exception) {
        Timber.e(e, "Sample transcription failed")
        throw e
      }
    }
  }

  fun release() {
    synchronized(recognizerLock) {
      recognizer?.release()
      recognizer = null
    }
  }
}
