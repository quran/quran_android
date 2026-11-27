package com.quran.mobile.feature.audiobar.ui

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.audiobar.state.AudioBarState
import org.junit.Test

class AudioBarStateHelpersTest {

  @Test
  fun `transitionGroupKey groups updates within same playback state class`() {
    val playingA = AudioBarState.Playing(repeat = 0, speed = 1.0f)
    val playingB = AudioBarState.Playing(repeat = 2, speed = 1.5f)
    val pausedA = AudioBarState.Paused(repeat = 0, speed = 1.0f)
    val pausedB = AudioBarState.Paused(repeat = -1, speed = 0.75f)
    val loadingA = AudioBarState.Loading(progress = 10, messageResource = 1)
    val loadingB = AudioBarState.Loading(progress = 85, messageResource = 1)

    assertThat(playingA.transitionGroupKey()).isEqualTo(playingB.transitionGroupKey())
    assertThat(pausedA.transitionGroupKey()).isEqualTo(pausedB.transitionGroupKey())
    assertThat(loadingA.transitionGroupKey()).isEqualTo(loadingB.transitionGroupKey())
  }

  @Test
  fun `transitionGroupKey distinguishes different audio bar state classes`() {
    val states = listOf(
      AudioBarState.Stopped(qariNameResource = 1, enableRecording = false),
      AudioBarState.Loading(progress = 0, messageResource = 1),
      AudioBarState.Downloading(progress = 0, messageResource = 1),
      AudioBarState.Playing(repeat = 0, speed = 1.0f),
      AudioBarState.Paused(repeat = 0, speed = 1.0f),
      AudioBarState.Error(messageResource = 1),
      AudioBarState.Prompt(messageResource = 1),
      AudioBarState.RecitationListening,
      AudioBarState.RecitationPlaying,
      AudioBarState.RecitationStopped
    )

    val keys = states.map { it.transitionGroupKey() }.toSet()
    assertThat(keys).hasSize(states.size)
  }

  @Test
  fun `repeatableButtonStateDescription returns null for default value and formatted text otherwise`() {
    assertThat(
      repeatableButtonStateDescription(value = 0, defaultValue = 0) { it.toString() }
    ).isNull()
    assertThat(
      repeatableButtonStateDescription(value = 2, defaultValue = 0) { it.toString() }
    ).isEqualTo("2")
    assertThat(
      repeatableButtonStateDescription(value = -1, defaultValue = 0) {
        if (it > -1) it.toString() else "∞"
      }
    ).isEqualTo("∞")
    assertThat(
      repeatableButtonStateDescription(value = 1.0f, defaultValue = 1.0f) { it.toString() }
    ).isNull()
    assertThat(
      repeatableButtonStateDescription(value = 1.5f, defaultValue = 1.0f) { it.toString() }
    ).isEqualTo("1.5")
  }

  @Test
  fun `normalizedProgress clamps out-of-range values and scales percentages`() {
    assertThat(normalizedProgress(-1)).isEqualTo(0f)
    assertThat(normalizedProgress(0)).isEqualTo(0f)
    assertThat(normalizedProgress(50)).isEqualTo(0.5f)
    assertThat(normalizedProgress(100)).isEqualTo(1f)
    assertThat(normalizedProgress(150)).isEqualTo(1f)
  }
}
