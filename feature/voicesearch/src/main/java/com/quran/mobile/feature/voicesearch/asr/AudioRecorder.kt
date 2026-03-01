package com.quran.mobile.feature.voicesearch.asr

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import kotlin.math.abs

sealed interface RecordingState {
  data class Recording(val amplitude: Float) : RecordingState
  data object Stopped : RecordingState
  data class Error(val message: String) : RecordingState
}

class AudioRecorder {

  @Volatile
  private var isRecording = false

  private var sampleBuffer = FloatArray(INITIAL_BUFFER_SIZE)
  private var sampleCount = 0
  private val samplesLock = Any()

  fun stopRecording() {
    isRecording = false
  }

  fun getAccumulatedSamples(): FloatArray {
    synchronized(samplesLock) {
      return sampleBuffer.copyOf(sampleCount)
    }
  }

  @SuppressLint("MissingPermission")
  fun startRecording(): Flow<RecordingState> = flow {
    val sampleRate = SAMPLE_RATE
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
      emit(RecordingState.Error("Failed to get valid buffer size"))
      return@flow
    }

    var recorder: AudioRecord? = null

    try {
      recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        channelConfig,
        audioFormat,
        bufferSize * 2
      )

      if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        emit(RecordingState.Error("AudioRecord failed to initialize"))
        return@flow
      }

      val buffer = ShortArray(bufferSize / 2)
      synchronized(samplesLock) {
        sampleBuffer = FloatArray(INITIAL_BUFFER_SIZE)
        sampleCount = 0
      }
      isRecording = true
      recorder.startRecording()

      while (isRecording) {
        val readCount = recorder.read(buffer, 0, buffer.size)
        if (readCount > 0) {
          // Convert shorts to floats outside the lock
          val floats = FloatArray(readCount)
          var maxAmp = 0
          for (i in 0 until readCount) {
            val s = buffer[i].toInt()
            floats[i] = s.toFloat() / Short.MAX_VALUE
            val a = abs(s)
            if (a > maxAmp) maxAmp = a
          }

          // Append to accumulated buffer under lock
          synchronized(samplesLock) {
            val required = sampleCount + readCount
            if (required > sampleBuffer.size) {
              val newSize = maxOf(sampleBuffer.size * 2, required)
              sampleBuffer = sampleBuffer.copyOf(newSize)
            }
            floats.copyInto(sampleBuffer, sampleCount)
            sampleCount += readCount
          }

          val normalizedAmplitude = maxAmp.toFloat() / Short.MAX_VALUE
          emit(RecordingState.Recording(normalizedAmplitude))
        }
      }

      emit(RecordingState.Stopped)
    } catch (e: Exception) {
      Timber.e(e, "Recording error")
      emit(RecordingState.Error(e.message ?: "Recording failed"))
    } finally {
      try {
        recorder?.stop()
        recorder?.release()
      } catch (e: Exception) {
        Timber.e(e, "Error releasing recorder")
      }
    }
  }.flowOn(Dispatchers.IO)

  companion object {
    const val SAMPLE_RATE = 16000
    private const val INITIAL_BUFFER_SIZE = SAMPLE_RATE * 10 // ~10 seconds
  }
}
