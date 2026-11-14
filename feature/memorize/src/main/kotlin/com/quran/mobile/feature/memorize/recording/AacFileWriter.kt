package com.quran.mobile.feature.memorize.recording

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder

/**
 * Encodes 16 kHz mono audio to AAC in an .m4a file with the platform's encoder: about 15 MB per hour
 * of recitation. Runs on the caller's thread (the recorder's own).
 */
internal class AacFileWriter(private val file: File) : AudioFileWriter {
  private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
  private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
  private val info = MediaCodec.BufferInfo()
  private var track = -1
  private var samples = 0L
  private var wroteAudio = false

  init {
    val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
      setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
      setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
      setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
    }
    try {
      codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      codec.start()
    } catch (e: Exception) {
      codec.release()
      muxer.release()
      file.delete()
      throw e
    }
  }

  override fun write(pcm: ShortArray, count: Int) {
    var offset = 0
    while (offset < count) {
      val index = codec.dequeueInputBuffer(TIMEOUT_US)
      if (index < 0) {
        drain(endOfStream = false)
        continue
      }
      val buffer = codec.getInputBuffer(index) ?: continue
      buffer.clear()
      val n = minOf(count - offset, buffer.remaining() / Short.SIZE_BYTES)
      buffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm, offset, n)
      codec.queueInputBuffer(index, 0, n * Short.SIZE_BYTES, timeUs(), 0)
      samples += n
      offset += n
      drain(endOfStream = false)
    }
  }

  override fun close(): Boolean {
    try {
      val index = codec.dequeueInputBuffer(TIMEOUT_US * 10)
      if (index >= 0) codec.queueInputBuffer(index, 0, 0, timeUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
      drain(endOfStream = true)
      codec.stop()
    } finally {
      codec.release()
      if (wroteAudio) runCatching { muxer.stop() }
      muxer.release()
    }
    if (!wroteAudio) file.delete()
    return wroteAudio
  }

  private fun timeUs() = samples * 1_000_000L / SAMPLE_RATE

  private fun drain(endOfStream: Boolean) {
    var idle = 0
    while (true) {
      val index = codec.dequeueOutputBuffer(info, if (endOfStream) TIMEOUT_US else 0)
      when {
        index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || ++idle > MAX_IDLE_DRAINS) return
        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
          track = muxer.addTrack(codec.outputFormat)
          muxer.start()
        }
        index >= 0 -> {
          val buffer = codec.getOutputBuffer(index)
          val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
          if (buffer != null && !config && info.size > 0 && track >= 0) {
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            muxer.writeSampleData(track, buffer, info)
            wroteAudio = true
          }
          codec.releaseOutputBuffer(index, false)
          if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        }
      }
    }
  }

  private companion object {
    const val SAMPLE_RATE = 16_000
    const val BIT_RATE = 32_000
    const val MAX_INPUT_BYTES = 16 * 1024
    const val TIMEOUT_US = 10_000L
    // when finishing: how long (in TIMEOUT_US steps) to wait for the encoder's last output
    const val MAX_IDLE_DRAINS = 100
  }
}
