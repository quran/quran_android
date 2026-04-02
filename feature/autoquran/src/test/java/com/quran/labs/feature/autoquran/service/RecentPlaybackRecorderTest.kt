package com.quran.labs.feature.autoquran.service

import androidx.media3.common.MediaItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentPlaybackRecorderTest {

  @Test
  fun `does not record when item is queued but not playing`() {
    val recorded = mutableListOf<Pair<Int, Int>>()
    val delayedExecutor = FakeDelayedExecutor()
    val recorder = RecentPlaybackRecorder(
      delayedExecutor = delayedExecutor,
      thresholdMs = 3_000L,
      nowMs = { delayedExecutor.nowMs },
    ) { qariId, sura ->
      recorded.add(qariId to sura)
    }

    recorder.onMediaItemTransition(mediaItem("sura_2_7"), isPlaying = false)

    assertThat(recorded).isEmpty()
  }

  @Test
  fun `records after threshold of active playback`() {
    val recorded = mutableListOf<Pair<Int, Int>>()
    val delayedExecutor = FakeDelayedExecutor()
    val recorder = RecentPlaybackRecorder(
      delayedExecutor = delayedExecutor,
      thresholdMs = 3_000L,
      nowMs = { delayedExecutor.nowMs },
    ) { qariId, sura ->
      recorded.add(qariId to sura)
    }

    recorder.onMediaItemTransition(mediaItem("sura_2_7"), isPlaying = true)

    delayedExecutor.advanceTimeBy(2_999L)
    assertThat(recorded).isEmpty()

    delayedExecutor.advanceTimeBy(1L)
    assertThat(recorded).containsExactly(7 to 2)
  }

  @Test
  fun `skipped intermediates are not marked recent`() {
    val recorded = mutableListOf<Pair<Int, Int>>()
    val delayedExecutor = FakeDelayedExecutor()
    val recorder = RecentPlaybackRecorder(
      delayedExecutor = delayedExecutor,
      thresholdMs = 3_000L,
      nowMs = { delayedExecutor.nowMs },
    ) { qariId, sura ->
      recorded.add(qariId to sura)
    }

    recorder.onMediaItemTransition(mediaItem("sura_1_7"), isPlaying = true)
    delayedExecutor.advanceTimeBy(1_000L)

    recorder.onMediaItemTransition(mediaItem("sura_2_7"), isPlaying = true)
    delayedExecutor.advanceTimeBy(1_000L)

    recorder.onMediaItemTransition(mediaItem("sura_3_7"), isPlaying = true)
    delayedExecutor.advanceTimeBy(2_999L)
    assertThat(recorded).isEmpty()

    delayedExecutor.advanceTimeBy(1L)
    assertThat(recorded).containsExactly(7 to 3)
  }

  @Test
  fun `playback time accumulates across pauses`() {
    val recorded = mutableListOf<Pair<Int, Int>>()
    val delayedExecutor = FakeDelayedExecutor()
    val recorder = RecentPlaybackRecorder(
      delayedExecutor = delayedExecutor,
      thresholdMs = 3_000L,
      nowMs = { delayedExecutor.nowMs },
    ) { qariId, sura ->
      recorded.add(qariId to sura)
    }

    recorder.onMediaItemTransition(mediaItem("sura_2_7"), isPlaying = true)
    delayedExecutor.advanceTimeBy(1_000L)

    recorder.onIsPlayingChanged(false)
    delayedExecutor.advanceTimeBy(5_000L)
    assertThat(recorded).isEmpty()

    recorder.onIsPlayingChanged(true)
    delayedExecutor.advanceTimeBy(1_999L)
    assertThat(recorded).isEmpty()

    delayedExecutor.advanceTimeBy(1L)
    assertThat(recorded).containsExactly(7 to 2)
  }

  private fun mediaItem(mediaId: String): MediaItem {
    return MediaItem.Builder()
      .setMediaId(mediaId)
      .build()
  }

  private class FakeDelayedExecutor : RecentPlaybackRecorder.DelayedExecutor {
    var nowMs: Long = 0L
      private set

    private val pendingTasks = mutableListOf<ScheduledTask>()

    override fun postDelayed(runnable: Runnable, delayMs: Long) {
      removeCallbacks(runnable)
      pendingTasks.add(ScheduledTask(runnable, nowMs + delayMs))
    }

    override fun removeCallbacks(runnable: Runnable) {
      pendingTasks.removeAll { it.runnable === runnable }
    }

    fun advanceTimeBy(deltaMs: Long) {
      nowMs += deltaMs
      while (true) {
        val nextTask = pendingTasks
          .filter { it.runAtMs <= nowMs }
          .minByOrNull { it.runAtMs }
          ?: return
        pendingTasks.remove(nextTask)
        nextTask.runnable.run()
      }
    }

    private data class ScheduledTask(
      val runnable: Runnable,
      val runAtMs: Long,
    )
  }
}
