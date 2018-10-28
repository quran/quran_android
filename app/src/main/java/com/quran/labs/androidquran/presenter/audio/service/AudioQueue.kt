package com.quran.labs.androidquran.presenter.audio.service

import com.quran.labs.androidquran.dao.audio.AudioPlaybackInfo
import com.quran.labs.androidquran.dao.audio.AudioRequest
import com.quran.labs.androidquran.data.QuranInfo
import com.quran.labs.androidquran.data.SuraAyah

/**
 * This class maintains a virtual audio queue for playback.
 * Given an [AudioRequest], this class maintains the queue for this request,
 * supporting operations such as switching to the next or previous ayahs or
 * jumping to an ayah. This class doesn't do the actual playback, but it
 * dictates what to play (while respecting repeat settings) and where to
 * find it.
 */
class AudioQueue(private val quranInfo: QuranInfo,
                 private val audioRequest: AudioRequest,
                 initialPlaybackInfo: AudioPlaybackInfo = AudioPlaybackInfo(audioRequest.start)) {
  private var playbackInfo: AudioPlaybackInfo = initialPlaybackInfo

  fun playAt(sura: Int, ayah: Int, skipAyahRepeat: Boolean = false): Boolean {
    val updatedPlaybackInfo =
        if (!skipAyahRepeat && shouldRepeat(audioRequest.repeatInfo, playbackInfo.timesPlayed)) {
          playbackInfo.copy(timesPlayed = playbackInfo.timesPlayed + 1)
        } else {
          if (audioRequest.enforceBounds && isOutOfBounds(audioRequest, sura, ayah)) {
            if (shouldRepeat(audioRequest.rangeRepeatInfo, playbackInfo.rangePlayedTimes)) {
              AudioPlaybackInfo(currentAyah = audioRequest.start,
                  rangePlayedTimes = playbackInfo.rangePlayedTimes + 1)
            } else {
              playbackInfo
            }
          } else {
            AudioPlaybackInfo(SuraAyah(sura, ayah))
          }
        }
    val result = updatedPlaybackInfo !== playbackInfo
    playbackInfo = updatedPlaybackInfo
    return result
  }

  fun getCurrentSura() = playbackInfo.currentAyah.sura
  fun getCurrentAyah() = playbackInfo.currentAyah.ayah

  fun playNextAyah(skipAyahRepeat: Boolean = false): Boolean {
    val next = playbackInfo.currentAyah.nextAyah()
    return playAt(next.sura, next.ayah, skipAyahRepeat)
  }

  fun playPreviousAyah(skipAyahRepeat: Boolean = false): Boolean {
    val previous = playbackInfo.currentAyah.previousAyah()
    return playAt(previous.sura, previous.ayah, skipAyahRepeat)
  }

  fun getUrl(): String? {
    val current = playbackInfo.currentAyah
    if (audioRequest.enforceBounds && isOutOfBounds(audioRequest, current.sura, current.ayah)) {
      return null
    }
    return String.format(audioRequest.audioPathInfo.urlFormat, current.sura, current.ayah)
  }

  fun withUpdatedAudioRequest(audioRequest: AudioRequest): AudioQueue {
    return AudioQueue(quranInfo, audioRequest, playbackInfo)
  }

  private fun shouldRepeat(repeatValue: Int, currentPlaybacks: Int): Boolean {
    // subtract 1 from currentPlaybacks because currentPlaybacks starts at 1
    // so repeating once requires having played twice.
    return repeatValue == -1 || (repeatValue > currentPlaybacks - 1)
  }

  private fun SuraAyah.nextAyah(): SuraAyah {
    return when {
      ayah + 1 <= quranInfo.getNumAyahs(sura) -> SuraAyah(sura, ayah + 1)
      sura < 114 -> SuraAyah(sura + 1, 1)
      else -> SuraAyah(1, 1)
    }
  }

  private fun SuraAyah.previousAyah(): SuraAyah {
    return when {
      ayah - 1 > 0 -> SuraAyah(sura, ayah - 1)
      sura > 1 -> SuraAyah(sura - 1, quranInfo.getNumAyahs(sura - 1))
      else -> SuraAyah(114, 6)
    }
  }

  private fun isOutOfBounds(audioRequest: AudioRequest, sura: Int, ayah: Int): Boolean {
    val start = audioRequest.start
    val end = audioRequest.end
    return (sura > end.sura || (end.sura == sura && ayah >= end.ayah) ||
            sura < start.sura || (start.sura == sura && ayah < start.ayah))
  }
}
