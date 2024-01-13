package com.quran.labs.androidquran.presenter.audio.service

import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.dao.audio.AudioPlaybackInfo
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.extension.requiresBasmallah
import java.util.Locale

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
          playbackInfo.copy(timesPlayed = playbackInfo.timesPlayed + 1, shouldPlayBasmallah = false)
        } else {
          if (audioRequest.enforceBounds && isOutOfBounds(audioRequest, sura, ayah)) {
            if (shouldRepeat(audioRequest.rangeRepeatInfo, playbackInfo.rangePlayedTimes)) {
              val start = audioRequest.start
              playbackInfo.copy(currentAyah = start,
                  timesPlayed = 1,
                  rangePlayedTimes = playbackInfo.rangePlayedTimes + 1,
                  shouldPlayBasmallah = (!audioRequest.isGapless() && start.requiresBasmallah()))
            } else {
              playbackInfo
            }
          } else {
            val currentAyah = playbackInfo.currentAyah
            val updatedAyah = SuraAyah(sura, ayah)
            val playBasmallahFlag = (!audioRequest.isGapless() &&
                currentAyah != updatedAyah &&
                updatedAyah.requiresBasmallah())
            playbackInfo.copy(currentAyah = SuraAyah(
                sura, ayah
            ),
                timesPlayed = 1,
                shouldPlayBasmallah = playBasmallahFlag)
          }
        }
    val result = updatedPlaybackInfo !== playbackInfo
    playbackInfo = updatedPlaybackInfo
    return result
  }

  fun getCurrentSura() = playbackInfo.currentAyah.sura
  fun getCurrentAyah() = playbackInfo.currentAyah.ayah
  fun getCurrentPlaybackAyah() = playbackInfo.currentAyah

  fun playNextAyah(skipAyahRepeat: Boolean = false): Boolean {
    if (playbackInfo.shouldPlayBasmallah) {
      playbackInfo = playbackInfo.copy(shouldPlayBasmallah = false)
      return true
    }
    val next = playbackInfo.currentAyah.nextAyah()
    return playAt(next.sura, next.ayah, skipAyahRepeat)
  }

  fun playPreviousAyah(skipAyahRepeat: Boolean = false): Boolean {
    val previous = playbackInfo.currentAyah.previousAyah()
    val result = playAt(previous.sura, previous.ayah, skipAyahRepeat)
    if (playbackInfo.shouldPlayBasmallah) {
      playbackInfo = playbackInfo.copy(shouldPlayBasmallah = false)
    }
    return result
  }

  fun getUrl(): String? {
    val current = playbackInfo.currentAyah
    val (currentSura, currentAyah) = current.sura to current.ayah
    if (audioRequest.enforceBounds && isOutOfBounds(audioRequest, currentSura, currentAyah)) {
      return null
    }

    val (sura, ayah) = if (playbackInfo.shouldPlayBasmallah) 1 to 1 else currentSura to currentAyah
    return String.format(Locale.US, audioRequest.audioPathInfo.urlFormat, sura, ayah)
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
      ayah + 1 <= quranInfo.getNumberOfAyahs(sura) -> SuraAyah(
          sura, ayah + 1
      )
      sura < 114 -> SuraAyah(sura + 1, 1)
      else -> SuraAyah(1, 1)
    }
  }

  private fun SuraAyah.previousAyah(): SuraAyah {
    return when {
      ayah - 1 > 0 -> SuraAyah(sura, ayah - 1)
      sura > 1 -> SuraAyah(
          sura - 1, quranInfo.getNumberOfAyahs(sura - 1)
      )
      else -> SuraAyah(114, 6)
    }
  }

  private fun isOutOfBounds(audioRequest: AudioRequest, sura: Int, ayah: Int): Boolean {
    val start = audioRequest.start
    val end = audioRequest.end
    return (sura > end.sura || (end.sura == sura && ayah > end.ayah) ||
            sura < start.sura || (start.sura == sura && ayah < start.ayah))
  }
}
