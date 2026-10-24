package com.quran.mobile.feature.memorize.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.max
import kotlin.math.sqrt

/** Something that delivers 16 kHz mono audio frames (the microphone, or a fake in tests). */
interface AudioSource {
  fun start()
  fun stop()
}

fun interface AudioSourceFactory {
  fun create(
    onFrame: (samples: FloatArray, count: Int, speech: Boolean) -> Unit,
    onError: (Throwable) -> Unit,
  ): AudioSource
}

/**
 * Captures 16 kHz mono audio from the microphone in 100 ms frames on a dedicated thread. Audio
 * only ever lives in memory: frames are handed to [onFrame] and then dropped.
 *
 * VOICE_RECOGNITION is used because it disables the device's noise suppression and automatic gain
 * control by default, which keeps the signal closest to what the speech model was trained on.
 */
class MicAudioSource(
  private val onFrame: (samples: FloatArray, count: Int, speech: Boolean) -> Unit,
  private val onError: (Throwable) -> Unit,
) : AudioSource {
  @Volatile private var running = false
  private var thread: Thread? = null

  /** Requires the RECORD_AUDIO permission. */
  @SuppressLint("MissingPermission")
  override fun start() {
    if (running) return
    running = true
    thread = Thread({ capture() }, "memorize-mic").apply { start() }
  }

  override fun stop() {
    running = false
    thread?.join(500)
    thread = null
  }

  @SuppressLint("MissingPermission")
  private fun capture() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
    val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
    val record = try {
      AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, CHANNEL, ENCODING,
        max(minBuffer, FRAME_SAMPLES * 4 * Float.SIZE_BYTES)
      )
    } catch (e: Exception) {
      running = false
      onError(e)
      return
    }
    if (record.state != AudioRecord.STATE_INITIALIZED) {
      record.release()
      running = false
      onError(IllegalStateException("microphone unavailable"))
      return
    }

    val vad = EnergyVoiceActivity()
    val frame = FloatArray(FRAME_SAMPLES)
    try {
      record.startRecording()
      while (running) {
        var filled = 0
        while (filled < FRAME_SAMPLES && running) {
          val read = record.read(frame, filled, FRAME_SAMPLES - filled, AudioRecord.READ_BLOCKING)
          if (read < 0) throw IllegalStateException("microphone read failed: $read")
          filled += read
        }
        if (filled > 0) onFrame(frame, filled, vad.isSpeech(frame, filled))
      }
    } catch (e: Exception) {
      onError(e)
    } finally {
      running = false
      runCatching { record.stop() }
      record.release()
    }
  }

  companion object {
    const val SAMPLE_RATE = 16_000
    const val FRAME_SAMPLES = SAMPLE_RATE / 10
    private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
  }
}

/**
 * A simple energy based voice activity detector with an adaptive noise floor. It only needs to
 * tell "the reciter is making sound" (including long madd) from silence.
 */
internal class EnergyVoiceActivity {
  private var noiseFloor = 0.003f
  private var speech = false

  fun isSpeech(samples: FloatArray, count: Int): Boolean {
    var sum = 0.0
    for (i in 0 until count) sum += samples[i] * samples[i]
    val rms = sqrt(sum / max(count, 1)).toFloat()
    // the floor follows quiet frames quickly and loud frames very slowly
    noiseFloor = if (rms < noiseFloor) 0.9f * noiseFloor + 0.1f * rms else 0.999f * noiseFloor + 0.001f * rms
    val on = max(noiseFloor * 4f, 0.006f)
    val off = max(noiseFloor * 2.5f, 0.004f)
    speech = if (speech) rms > off else rms > on
    return speech
  }
}
