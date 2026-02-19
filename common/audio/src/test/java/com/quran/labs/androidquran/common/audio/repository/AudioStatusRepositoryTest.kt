package com.quran.labs.androidquran.common.audio.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.model.playback.AudioPathInfo
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.model.playback.AudioStatus
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AudioStatusRepositoryTest {

  private lateinit var repository: AudioStatusRepository

  private val testQari = QariItem(
    id = 1,
    name = "Test Qari",
    url = "https://example.com/",
    path = "test_qari",
    hasGaplessAlternative = false,
    db = null
  )

  private fun buildPlaybackStatus(
    sura: Int,
    ayah: Int,
    playbackStatus: PlaybackStatus
  ): AudioStatus.Playback {
    val suraAyah = SuraAyah(sura, ayah)
    val audioRequest = AudioRequest(
      start = suraAyah,
      end = suraAyah,
      qari = testQari,
      enforceBounds = false,
      shouldStream = false,
      audioPathInfo = AudioPathInfo("", "", null, emptyList())
    )
    return AudioStatus.Playback(
      currentAyah = suraAyah,
      audioRequest = audioRequest,
      playbackStatus = playbackStatus
    )
  }

  @Before
  fun setup() {
    repository = AudioStatusRepository()
  }

  @Test
  fun `initial state is Stopped`() = runTest {
    repository.audioPlaybackFlow.test {
      // Assert
      assertThat(awaitItem()).isInstanceOf(AudioStatus.Stopped::class.java)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `updateAyahPlayback transitions state to Playback with PLAYING status`() = runTest {
    // Arrange
    val playingStatus = buildPlaybackStatus(1, 1, PlaybackStatus.PLAYING)

    repository.audioPlaybackFlow.test {
      awaitItem() // consume initial Stopped

      // Act
      repository.updateAyahPlayback(playingStatus)

      // Assert
      val emitted = awaitItem()
      assertThat(emitted).isInstanceOf(AudioStatus.Playback::class.java)
      val playback = emitted as AudioStatus.Playback
      assertThat(playback.playbackStatus).isEqualTo(PlaybackStatus.PLAYING)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `updateAyahPlayback transitions state to Playback with PAUSED status`() = runTest {
    // Arrange
    val pausedStatus = buildPlaybackStatus(2, 5, PlaybackStatus.PAUSED)

    repository.audioPlaybackFlow.test {
      awaitItem() // consume initial Stopped

      // Act
      repository.updateAyahPlayback(pausedStatus)

      // Assert
      val emitted = awaitItem()
      assertThat(emitted).isInstanceOf(AudioStatus.Playback::class.java)
      val playback = emitted as AudioStatus.Playback
      assertThat(playback.playbackStatus).isEqualTo(PlaybackStatus.PAUSED)
      assertThat(playback.currentAyah).isEqualTo(SuraAyah(2, 5))
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `updateAyahPlayback with Stopped returns to Stopped state`() = runTest {
    // Arrange — first move to Playback, then back to Stopped
    val playingStatus = buildPlaybackStatus(1, 1, PlaybackStatus.PLAYING)

    repository.audioPlaybackFlow.test {
      awaitItem() // consume initial Stopped

      repository.updateAyahPlayback(playingStatus)
      awaitItem() // consume Playback

      // Act
      repository.updateAyahPlayback(AudioStatus.Stopped)

      // Assert
      assertThat(awaitItem()).isEqualTo(AudioStatus.Stopped)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `multiple collectors receive the same current state`() = runTest {
    // Arrange
    val playingStatus = buildPlaybackStatus(3, 10, PlaybackStatus.PLAYING)
    repository.updateAyahPlayback(playingStatus)

    // Act + Assert — both collectors see the same cached value from StateFlow
    repository.audioPlaybackFlow.test {
      val first = awaitItem()
      assertThat(first).isInstanceOf(AudioStatus.Playback::class.java)
      cancelAndIgnoreRemainingEvents()
    }

    repository.audioPlaybackFlow.test {
      val second = awaitItem()
      assertThat(second).isInstanceOf(AudioStatus.Playback::class.java)
      val playback = second as AudioStatus.Playback
      assertThat(playback.currentAyah).isEqualTo(SuraAyah(3, 10))
      cancelAndIgnoreRemainingEvents()
    }
  }
}
