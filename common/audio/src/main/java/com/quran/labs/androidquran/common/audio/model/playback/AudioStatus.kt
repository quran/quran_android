package com.quran.labs.androidquran.common.audio.model.playback

import com.quran.data.model.SuraAyah

sealed class AudioStatus {
  data object Stopped : AudioStatus()
  data class Playback(
    val currentAyah: SuraAyah,
    val audioRequest: AudioRequest,
    val playbackStatus: PlaybackStatus
  ) : AudioStatus()
}

fun AudioStatus.currentPlaybackAyah() =
  (this as? AudioStatus.Playback)?.currentAyah
