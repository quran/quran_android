package com.quran.labs.androidquran.test.fakes

import android.content.Intent
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest

/**
 * Fake implementation of PagerActivity methods used by AudioPresenter.
 *
 * Pattern: Capture requests for ArgumentCaptor replacement
 *
 * Usage:
 * ```
 * val fakeActivity = FakePagerActivity()
 * presenter.bind(fakeActivity)
 * presenter.play(...)
 *
 * // Assert playback was triggered
 * val request = fakeActivity.getLastPlaybackRequest()
 * assertThat(request?.start).isEqualTo(expectedStart)
 * ```
 */
class FakePagerActivity {

  private val playbackRequests = mutableListOf<AudioRequest>()
  private val downloadRequests = mutableListOf<DownloadRequest>()

  data class DownloadRequest(
    val intent: Intent?,
    val title: String? = null,
    val url: String? = null,
    val destination: String? = null
  )

  fun handlePlayback(request: AudioRequest?) {
    if (request != null) {
      playbackRequests.add(request)
    }
  }

  fun handleRequiredDownload(downloadIntent: Intent?) {
    downloadRequests.add(DownloadRequest(downloadIntent))
  }

  fun handleRequiredDownload(
    downloadIntent: Intent?,
    title: String,
    url: String,
    destination: String
  ) {
    downloadRequests.add(DownloadRequest(downloadIntent, title, url, destination))
  }

  // Query helpers
  fun getLastPlaybackRequest(): AudioRequest? = playbackRequests.lastOrNull()

  fun getAllPlaybackRequests(): List<AudioRequest> = playbackRequests.toList()

  fun getPlaybackRequestCount(): Int = playbackRequests.size

  fun getLastDownloadRequest(): DownloadRequest? = downloadRequests.lastOrNull()

  fun getAllDownloadRequests(): List<DownloadRequest> = downloadRequests.toList()

  fun getDownloadRequestCount(): Int = downloadRequests.size

  // Assertion helpers
  fun assertPlaybackCalled() {
    require(playbackRequests.isNotEmpty()) {
      "Expected handlePlayback() to be called but it wasn't"
    }
  }

  fun assertPlaybackCalledTimes(times: Int) {
    require(playbackRequests.size == times) {
      "Expected handlePlayback() called $times times but was called ${playbackRequests.size} times"
    }
  }

  fun assertDownloadCalled() {
    require(downloadRequests.isNotEmpty()) {
      "Expected handleRequiredDownload() to be called but it wasn't"
    }
  }

  fun assertPlaybackNotCalled() {
    require(playbackRequests.isEmpty()) {
      "Expected handlePlayback() not to be called but it was called ${playbackRequests.size} times"
    }
  }

  fun assertDownloadNotCalled() {
    require(downloadRequests.isEmpty()) {
      "Expected handleRequiredDownload() not to be called but it was called ${downloadRequests.size} times"
    }
  }

  // Reset for test isolation
  fun reset() {
    playbackRequests.clear()
    downloadRequests.clear()
  }
}
