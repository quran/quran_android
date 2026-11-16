package com.quran.mobile.feature.memorize.engine.sherpa

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import com.quran.mobile.feature.memorize.tracker.engine.RecitationEngine
import com.quran.mobile.feature.memorize.tracker.engine.RecitationStream
import com.quran.mobile.feature.memorize.tracker.engine.RecognizedToken
import java.io.File

/**
 * Runs a streaming Zipformer2-CTC phoneme model with sherpa-onnx, entirely on the device.
 *
 * @param model the int8 ONNX model file
 * @param tokens the model's symbol table (tokens.txt)
 */
class SherpaRecitationEngine(model: File, tokens: File, numThreads: Int = 2) : RecitationEngine {

  private val recognizer = OnlineRecognizer(
    assetManager = null,
    config = OnlineRecognizerConfig(
      featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
      modelConfig = OnlineModelConfig(
        zipformer2Ctc = OnlineZipformer2CtcModelConfig(model = model.absolutePath),
        tokens = tokens.absolutePath,
        numThreads = numThreads,
        provider = "cpu",
      ),
      // Not sherpa's endpointing: it measures silence as frames without new tokens, which is also
      // what a held madd looks like, and starting over there cuts the end of the word.
      enableEndpoint = false,
      decodingMethod = "greedy_search",
    )
  )

  override fun newStream(): RecitationStream = SherpaStream(recognizer)

  override fun close() {
    recognizer.release()
  }

  private class SherpaStream(private val recognizer: OnlineRecognizer) : RecitationStream {
    private val stream: OnlineStream = recognizer.createStream()
    private var emitted = 0
    private var samplesFed = 0L
    // audio time (ms) at which the stream was last reset; result timestamps are relative to it
    private var segmentStartMs = 0L
    private var silentSamples = 0L
    private var lastTokenAt = 0L

    override fun accept(samples: FloatArray, count: Int, speech: Boolean): List<RecognizedToken> {
      stream.acceptWaveform(if (count == samples.size) samples else samples.copyOf(count), SAMPLE_RATE)
      samplesFed += count
      silentSamples = if (speech) 0 else silentSamples + count
      return decode()
    }

    override fun finish(): List<RecognizedToken> {
      stream.inputFinished()
      return decode()
    }

    private fun decode(): List<RecognizedToken> {
      while (recognizer.isReady(stream)) {
        recognizer.decode(stream)
      }
      val result = recognizer.getResult(stream)
      val tokens = ArrayList<RecognizedToken>(result.tokens.size - emitted)
      for (i in emitted until result.tokens.size) {
        val timeMs = segmentStartMs + (result.timestamps.getOrElse(i) { 0f } * 1000).toLong()
        tokens += RecognizedToken(result.tokens[i], timeMs, result.ysProbs.getOrNull(i))
      }
      emitted = result.tokens.size
      if (tokens.isNotEmpty()) lastTokenAt = samplesFed
      if (shouldStartOver()) {
        recognizer.reset(stream)
        emitted = 0
        segmentStartMs = samplesFed * 1000 / SAMPLE_RATE
      }
      return tokens
    }

    /**
     * Starting over keeps the results (and memory) from growing for the whole session, at the cost
     * of the model's context, so it is done in real silence only.
     */
    private fun shouldStartOver(): Boolean {
      if (emitted == 0) return false
      val sinceToken = samplesFed - lastTokenAt
      return (silentSamples >= QUIET_SAMPLES && sinceToken >= QUIET_SAMPLES) ||
        // audio that never goes quiet (e.g. a mosque's loudspeakers): rarely, after a long gap
        (emitted >= MAX_SEGMENT_TOKENS && sinceToken >= LONG_GAP_SAMPLES)
    }

    override fun close() {
      stream.release()
    }
  }

  private companion object {
    const val SAMPLE_RATE = 16_000
    const val QUIET_SAMPLES = 2L * SAMPLE_RATE
    const val LONG_GAP_SAMPLES = 3L * SAMPLE_RATE
    const val MAX_SEGMENT_TOKENS = 1_500
  }
}
