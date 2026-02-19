package com.quran.mobile.feature.audiobar.presenter

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.audiobar.state.AudioBarEvent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AudioBarEventRepositoryTest {

  private fun createRepository(): AudioBarEventRepository = AudioBarEventRepository()

  @Test
  fun `flow does not emit on creation before any event is sent`() = runTest {
    val repository = createRepository()

    repository.audioBarEventFlow.test {
      expectNoEvents()
      cancel()
    }
  }

  @Test
  fun `emitting a Play event delivers Play to a collector`() = runTest {
    val repository = createRepository()

    repository.audioBarEventFlow.test {
      repository.onAudioBarEvent(AudioBarEvent.Play)
      assertThat(awaitItem()).isEqualTo(AudioBarEvent.Play)
      cancel()
    }
  }

  @Test
  fun `emitting a Pause event delivers Pause to a collector`() = runTest {
    val repository = createRepository()

    repository.audioBarEventFlow.test {
      repository.onAudioBarEvent(AudioBarEvent.Pause)
      assertThat(awaitItem()).isEqualTo(AudioBarEvent.Pause)
      cancel()
    }
  }

  @Test
  fun `emitting a Stop event delivers Stop to a collector`() = runTest {
    val repository = createRepository()

    repository.audioBarEventFlow.test {
      repository.onAudioBarEvent(AudioBarEvent.Stop)
      assertThat(awaitItem()).isEqualTo(AudioBarEvent.Stop)
      cancel()
    }
  }

  @Test
  fun `emitting a ResumePlayback event delivers ResumePlayback to a collector`() = runTest {
    val repository = createRepository()

    repository.audioBarEventFlow.test {
      repository.onAudioBarEvent(AudioBarEvent.ResumePlayback)
      assertThat(awaitItem()).isEqualTo(AudioBarEvent.ResumePlayback)
      cancel()
    }
  }

  @Test
  fun `emitting a ChangeQari event delivers ChangeQari to a collector`() = runTest {
    val repository = createRepository()

    repository.audioBarEventFlow.test {
      repository.onAudioBarEvent(AudioBarEvent.ChangeQari)
      assertThat(awaitItem()).isEqualTo(AudioBarEvent.ChangeQari)
      cancel()
    }
  }

  @Test
  fun `SetRepeat event carries the correct repeat value`() = runTest {
    val repository = createRepository()
    val expectedRepeat = 3

    repository.audioBarEventFlow.test {
      repository.onAudioBarEvent(AudioBarEvent.SetRepeat(expectedRepeat))
      val received = awaitItem()
      assertThat(received).isInstanceOf(AudioBarEvent.SetRepeat::class.java)
      assertThat((received as AudioBarEvent.SetRepeat).repeat).isEqualTo(expectedRepeat)
      cancel()
    }
  }

  @Test
  fun `SetSpeed event carries the correct speed value`() = runTest {
    val repository = createRepository()
    val expectedSpeed = 1.5f

    repository.audioBarEventFlow.test {
      repository.onAudioBarEvent(AudioBarEvent.SetSpeed(expectedSpeed))
      val received = awaitItem()
      assertThat(received).isInstanceOf(AudioBarEvent.SetSpeed::class.java)
      assertThat((received as AudioBarEvent.SetSpeed).speed).isEqualTo(expectedSpeed)
      cancel()
    }
  }

  @Test
  fun `events are delivered in emission order`() = runTest {
    val repository = createRepository()

    repository.audioBarEventFlow.test {
      repository.onAudioBarEvent(AudioBarEvent.Play)
      repository.onAudioBarEvent(AudioBarEvent.Pause)
      assertThat(awaitItem()).isEqualTo(AudioBarEvent.Play)
      assertThat(awaitItem()).isEqualTo(AudioBarEvent.Pause)
      cancel()
    }
  }

  @Test
  fun `multiple collectors each receive emitted events`() = runTest {
    val repository = createRepository()

    repository.audioBarEventFlow.test {
      val secondCollector = repository.audioBarEventFlow.test {
        repository.onAudioBarEvent(AudioBarEvent.FastForward)
        assertThat(awaitItem()).isEqualTo(AudioBarEvent.FastForward)
        cancel()
      }
      assertThat(awaitItem()).isEqualTo(AudioBarEvent.FastForward)
      cancel()
    }
  }

  @Test
  fun `Cancel event is delivered correctly`() = runTest {
    val repository = createRepository()

    repository.audioBarEventFlow.test {
      repository.onAudioBarEvent(AudioBarEvent.Cancel)
      assertThat(awaitItem()).isEqualTo(AudioBarEvent.Cancel)
      cancel()
    }
  }

  @Test
  fun `Rewind event is delivered correctly`() = runTest {
    val repository = createRepository()

    repository.audioBarEventFlow.test {
      repository.onAudioBarEvent(AudioBarEvent.Rewind)
      assertThat(awaitItem()).isEqualTo(AudioBarEvent.Rewind)
      cancel()
    }
  }
}
