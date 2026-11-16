package com.quran.mobile.feature.memorize.engine.sherpa

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineOmnilingualAsrCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File

/**
 * An offline (whole clip) CTC model on raw audio, run by the sherpa-onnx already in the app: the
 * pieces it hears, as ids. [tokens] names piece i "p<i>" (see bench/tilawi_for_sherpa.py).
 */
class SherpaPieceRecognizer(model: File, tokens: File, numThreads: Int = 2) : AutoCloseable {
  private val recognizer = OfflineRecognizer(
    null,
    OfflineRecognizerConfig(
      featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
      modelConfig = OfflineModelConfig(
        omnilingual = OfflineOmnilingualAsrCtcModelConfig(model.path),
        tokens = tokens.path,
        numThreads = numThreads,
        provider = "cpu",
      ),
    ),
  )

  /** The pieces heard in [audio] (16 kHz mono), blanks and repeats collapsed. */
  fun pieces(audio: FloatArray): IntArray {
    val stream = recognizer.createStream()
    try {
      stream.acceptWaveform(audio, SAMPLE_RATE)
      recognizer.decode(stream)
      return recognizer.getResult(stream).tokens.map { it.removePrefix("p").toInt() }.toIntArray()
    } finally {
      stream.release()
    }
  }

  override fun close() = recognizer.release()

  private companion object {
    const val SAMPLE_RATE = 16_000
  }
}
