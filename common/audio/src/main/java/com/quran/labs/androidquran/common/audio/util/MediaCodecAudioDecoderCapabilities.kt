package com.quran.labs.androidquran.common.audio.util

import android.media.MediaCodecList
import android.media.MediaFormat
import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * [AudioDecoderCapabilities] implementation backed by the platform [MediaCodecList]. The result
 * is computed once and cached, since the set of available decoders does not change at runtime.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MediaCodecAudioDecoderCapabilities @Inject constructor() : AudioDecoderCapabilities {
  private val opusSupported: Boolean by lazy { hasDecoderFor(MediaFormat.MIMETYPE_AUDIO_OPUS) }

  override fun isOpusSupported(): Boolean = opusSupported

  private fun hasDecoderFor(mimeType: String): Boolean = runCatching {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
      !codec.isEncoder && codec.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
    }
  }.getOrDefault(false)
}
