package com.quran.labs.feature.autoquran.service

import android.os.SystemClock
import androidx.media3.common.MediaItem

internal class RecentPlaybackRecorder(
  private val delayedExecutor: DelayedExecutor,
  private val thresholdMs: Long = RECENT_PLAYBACK_THRESHOLD_MS,
  private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
  private val onRecord: (qariId: Int, sura: Int) -> Unit,
) {

  private var currentCandidate: RecentPlaybackCandidate? = null
  private var remainingThresholdMs = thresholdMs
  private var countdownStartedAtMs: Long? = null
  private var recordedCurrent = false
  private val countdownRunnable = Runnable {
    countdownStartedAtMs = null
    remainingThresholdMs = 0L
    maybeRecordCurrent()
  }

  fun onMediaItemTransition(mediaItem: MediaItem?, isPlaying: Boolean) {
    stopCountdown()
    maybeRecordCurrent()

    currentCandidate = RecentPlaybackCandidate.from(mediaItem)
    remainingThresholdMs = thresholdMs
    recordedCurrent = false

    if (isPlaying) {
      startCountdownIfNeeded()
    }
  }

  fun onIsPlayingChanged(isPlaying: Boolean) {
    if (isPlaying) {
      startCountdownIfNeeded()
    } else {
      stopCountdown()
      maybeRecordCurrent()
    }
  }

  fun clear() {
    stopCountdown()
    currentCandidate = null
    remainingThresholdMs = thresholdMs
    recordedCurrent = false
  }

  private fun startCountdownIfNeeded() {
    if (currentCandidate == null || recordedCurrent || countdownStartedAtMs != null) return
    if (remainingThresholdMs <= 0L) {
      maybeRecordCurrent()
      return
    }

    countdownStartedAtMs = nowMs()
    delayedExecutor.postDelayed(countdownRunnable, remainingThresholdMs)
  }

  private fun stopCountdown() {
    val startedAtMs = countdownStartedAtMs
    if (startedAtMs != null) {
      remainingThresholdMs = (remainingThresholdMs - (nowMs() - startedAtMs)).coerceAtLeast(0L)
      countdownStartedAtMs = null
    }
    delayedExecutor.removeCallbacks(countdownRunnable)
  }

  private fun maybeRecordCurrent() {
    val candidate = currentCandidate ?: return
    if (recordedCurrent || remainingThresholdMs > 0L) return

    recordedCurrent = true
    onRecord(candidate.qariId, candidate.sura)
  }

  private data class RecentPlaybackCandidate(
    val sura: Int,
    val qariId: Int,
  ) {
    companion object {
      fun from(mediaItem: MediaItem?): RecentPlaybackCandidate? {
        val mediaId = mediaItem?.mediaId ?: return null
        if (!mediaId.startsWith("sura_")) return null

        val parts = mediaId.split("_")
        if (parts.size != 3) return null

        val sura = parts[1].toIntOrNull() ?: return null
        val qariId = parts[2].toIntOrNull() ?: return null
        return RecentPlaybackCandidate(sura = sura, qariId = qariId)
      }
    }
  }

  internal interface DelayedExecutor {
    fun postDelayed(runnable: Runnable, delayMs: Long)
    fun removeCallbacks(runnable: Runnable)
  }

  companion object {
    const val RECENT_PLAYBACK_THRESHOLD_MS = 5_000L
  }
}
