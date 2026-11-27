package com.quran.labs.androidquran.kidsclassroom

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Media3 [BaseAudioProcessor] attached directly to `ExoPlayer`'s `DefaultAudioSink` to compute a
 * real-time smoothed PCM RMS envelope (`0.0f..1.0f`) every ~20 ms without modifying the audio
 * stream. Supports both `PCM_16BIT` and `PCM_FLOAT` (used by Media3 Sonic at `0.75×` Slow-Mo).
 */
@OptIn(UnstableApi::class)
class RmsEnvelopeAudioProcessor : BaseAudioProcessor() {

  private val _currentRms = MutableStateFlow(0f)
  val currentRms: StateFlow<Float> = _currentRms.asStateFlow()

  @Volatile
  private var smoothedRms: Float = 0f

  @Volatile
  private var hasSeenPcmStream: Boolean = false

  private val acousticTracker = StreamingPcmAcousticTracker()

  private var activeEncoding: Int = C.ENCODING_INVALID
  private var windowSamples = 320 // ~20ms at 16 kHz; updated on configure()
  private var accSumSq = 0.0
  private var accCount = 0

  fun latestRms(): Float = smoothedRms

  /**
   * Returns the latest 20 ms 3-band acoustic frame (`rms`, `lowBandRatio`, `midBandRatio`,
   * `highBandRatio`, `zcr`, `steadiness`, `qalqalahBurstScore`, `waveformBars`) computed directly
   * from ExoPlayer's live PCM stream.
   */
  fun latestAcousticFrame(): AudioAcousticFrame = acousticTracker.latestFrame()

  /**
   * Returns live PCM RMS once ExoPlayer has begun pushing decoded PCM buffers; returns a neutral
   * `0.42f` speech envelope only if hardware offload/buffering hasn't delivered any PCM frame yet.
   */
  fun latestRmsForPlayback(): Float = if (hasSeenPcmStream) smoothedRms else 0.42f

  fun resetEnvelope() {
    accSumSq = 0.0
    accCount = 0
    smoothedRms = 0f
    _currentRms.value = 0f
    acousticTracker.reset()
  }

  override fun onConfigure(
    inputAudioFormat: AudioProcessor.AudioFormat
  ): AudioProcessor.AudioFormat {
    val enc = inputAudioFormat.encoding
    if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
      activeEncoding = C.ENCODING_INVALID
      return AudioProcessor.AudioFormat.NOT_SET
    }
    activeEncoding = enc
    val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
    val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
    acousticTracker.configure(sampleRate, channels)
    // 20 ms window across all channels
    windowSamples = ((sampleRate * 20L) / 1000L).toInt().coerceAtLeast(160) * channels
    return inputAudioFormat
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val remaining = inputBuffer.remaining()
    if (remaining <= 0) return
    hasSeenPcmStream = true

    val outBuffer = replaceOutputBuffer(remaining)
    val startPos = inputBuffer.position()
    val limit = inputBuffer.limit()

    if (activeEncoding == C.ENCODING_PCM_FLOAT) {
      val ordered = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
      var pos = startPos
      while (pos + 3 < limit) {
        val sample = ordered.getFloat(pos).coerceIn(-1f, 1f)
        pushSample(sample)
        pos += 4
      }
    } else {
      var pos = startPos
      while (pos + 1 < limit) {
        val low = inputBuffer.get(pos).toInt() and 0xFF
        val high = inputBuffer.get(pos + 1).toInt()
        val sample = ((high shl 8) or low).toShort().toFloat() / 32768f
        pushSample(sample)
        pos += 2
      }
    }

    outBuffer.put(inputBuffer)
    outBuffer.flip()
  }

  private fun pushSample(sample: Float) {
    acousticTracker.pushInterleavedSample(sample)
    accSumSq += (sample * sample).toDouble()
    accCount++
    if (accCount >= windowSamples) {
      val rawRms = sqrt(accSumSq / accCount.toDouble()).toFloat()
      val scaled = (rawRms * 3.6f).coerceIn(0f, 1f)
      val alpha = if (scaled > smoothedRms) 0.65f else 0.35f
      smoothedRms = (alpha * scaled + (1f - alpha) * smoothedRms).coerceIn(0f, 1f)
      _currentRms.value = smoothedRms
      accSumSq = 0.0
      accCount = 0
    }
  }

  override fun onFlush() {
    resetEnvelope()
  }

  override fun onReset() {
    hasSeenPcmStream = false
    activeEncoding = C.ENCODING_INVALID
    resetEnvelope()
  }
}
