package com.quran.mobile.feature.memorize.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.audio.AudioSource
import com.quran.mobile.feature.memorize.audio.AudioSourceFactory
import com.quran.mobile.feature.memorize.data.LexiconRepository
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import com.quran.mobile.feature.memorize.prompt.HintVoice
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import com.quran.mobile.feature.memorize.tracker.WordId
import com.quran.mobile.feature.memorize.tracker.engine.RecitationEngine
import com.quran.mobile.feature.memorize.tracker.engine.RecitationStream
import com.quran.mobile.feature.memorize.tracker.engine.RecognizedToken
import com.quran.recitation.events.RecitationEventPresenter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The whole session pipeline with a scripted speech engine and a fake microphone: the lexicon,
 * tracker, hint scheduler and state publishing are the real ones. Everything runs on the test's
 * scheduler and virtual clock, so the hint delays are exact and nothing waits for real time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class MemorizeSessionControllerTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val events = RecitationEventPresenter()
  private val engine = ScriptedEngine()
  private val microphone = FakeMicrophone()
  private val voice = FakeVoice()
  private lateinit var preferences: MemorizePreferences
  private var enginesCreated = 0
  private var engineFailure: Throwable? = null
  // audio the user says while the speech model is still loading
  private var duringModelLoad: (() -> Unit)? = null
  private var inCall = false

  @Before
  fun setUp() {
    preferences = MemorizePreferences(context).apply {
      correctorEnabled = true
      hintDelaySeconds = 15 // long enough that no hint is given on its own unless a test wants one
    }
  }

  private fun TestScope.controller(): MemorizeSessionController = MemorizeSessionController.forTest(
    lexicon = LexiconRepository.forTest(context, StandardTestDispatcher(testScheduler)),
    models = SpeechModelStore(context),
    preferences = preferences,
    recitationEventPresenter = events,
    engineFactory = {
      engineFailure?.let { throw it }
      enginesCreated++
      duringModelLoad?.invoke()
      engine
    },
    promptPlayer = voice,
    audioSources = microphone,
    callState = { inCall },
    scope = backgroundScope,
    worker = StandardTestDispatcher(testScheduler),
    clock = { testScheduler.currentTime },
  )

  @Test
  fun `words are revealed as they are recited and the session follows the ayah`() = runTest {
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()

    // Al-Ikhlas 1: قُل هُوَ ٱللَّهُ أَحَدٌ (as pronounced)
    recite("قُل", "هُوَ", "للَااهُ", "ءَحَدڇ")

    val state = controller.state.value!!
    assertThat(state.cursor).isEqualTo(4)
    assertThat(state.isHidden(3)).isFalse()
    assertThat(state.isHidden(4)).isTrue()
    assertThat(state.currentAyah).isEqualTo(SuraAyah(112, 2))
    assertThat(events.recitationSession()?.currentAyah()).isEqualTo(SuraAyah(112, 2))
  }

  @Test
  fun `a wrong word is marked but not revealed`() = runTest {
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()

    recite("قُل", "هُوَ", "سَمِعَ", "مُوسَاا", "كَتَبَ")

    assertThat(controller.state.value!!.mistake).isEqualTo(2)
    assertThat(controller.state.value!!.isHidden(2)).isTrue()
  }

  @Test
  fun `a hint reveals and speaks the next word`() = runTest {
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()

    controller.requestHint()
    runCurrent()

    assertThat(controller.state.value!!.cursor).isEqualTo(1)
    assertThat(controller.state.value!!.hinted).containsExactly(0)
    assertThat(voice.played).containsExactly(listOf(WordId(112, 1, 1)))
  }

  @Test
  fun `stopping ends the session`() = runTest {
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()

    controller.stop()
    runCurrent()

    assertThat(controller.state.value).isNull()
    assertThat(events.recitationSession()).isNull()
    assertThat(microphone.running).isFalse()
  }

  @Test
  fun `starting again while the session is being prepared is ignored`() = runTest {
    val controller = controller()

    controller.start(SuraAyah(112, 1))
    controller.start(SuraAyah(113, 1))
    runCurrent()

    assertThat(controller.state.value!!.passage.start).isEqualTo(SuraAyah(112, 1))
    assertThat(enginesCreated).isEqualTo(1)
  }

  @Test
  fun `what is recited while the model loads isn't lost`() = runTest {
    val controller = controller()
    duringModelLoad = {
      // the microphone is already on: the user starts reciting at once
      assertThat(microphone.running).isTrue()
      engine.script += listOf("قُل", "هُوَ")
      repeat(4) { microphone.frame() }
    }

    controller.start(SuraAyah(112, 1))
    runCurrent()

    assertThat(controller.state.value!!.cursor).isEqualTo(2)
  }

  @Test
  fun `stopping while the session is being prepared loads nothing`() = runTest {
    val controller = controller()

    controller.start(SuraAyah(112, 1))
    controller.stop()
    runCurrent()

    assertThat(controller.state.value).isNull()
    assertThat(enginesCreated).isEqualTo(0)
    assertThat(microphone.running).isFalse()
  }

  @Test
  fun `a session that can't start is reported and ended`() = runTest {
    val controller = controller()
    engineFailure = OutOfMemoryError("model too large")

    controller.failures.test {
      controller.start(SuraAyah(112, 1))
      awaitItem()
    }
    runCurrent()

    assertThat(controller.state.value).isNull()
    assertThat(events.recitationSession()).isNull()
    assertThat(microphone.running).isFalse()

    // and the next start works
    engineFailure = null
    controller.start(SuraAyah(112, 1))
    runCurrent()
    assertThat(controller.state.value!!.status).isEqualTo(MemorizeState.Status.LISTENING)
  }

  @Test
  fun `in salah a stall while reciting the passage gets a hint`() = runTest {
    preferences.hintDelaySeconds = 2
    val controller = controller()
    controller.start(SuraAyah(112, 1), MemorizeState.Mode.SALAH)
    runCurrent()
    recite("قُل", "هُوَ")

    microphone.silence(3)
    advanceTimeBy(5_000)

    assertThat(voice.played.first()).containsExactly(WordId(112, 1, 3))
  }

  @Test
  fun `in salah no hint is given once the next rak'ah's Al-Fatiha begins`() = runTest {
    preferences.hintDelaySeconds = 2
    val controller = controller()
    controller.start(SuraAyah(108, 1), MemorizeState.Mode.SALAH)
    runCurrent()
    // إِنَّا أَعْطَيْنَاكَ ...
    recite(*pronunciations(SuraAyah(108, 1), 2))
    assertThat(controller.state.value!!.cursor).isEqualTo(2)

    // ruku, sujud... and the next rak'ah starts with Al-Fatiha
    recite(*pronunciations(SuraAyah(1, 2), 8))
    microphone.silence(3)
    advanceTimeBy(10_000)

    assertThat(voice.played).isEmpty()
    assertThat(controller.state.value!!.cursor).isEqualTo(2)
  }

  @Test
  fun `after too many hints in a row it stays quiet until the user recites again`() = runTest {
    preferences.hintDelaySeconds = 2
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()
    recite("قُل", "هُوَ")
    microphone.silence(3)

    // ten hints without the user going on, then it gives up
    advanceTimeBy(60_000)
    assertThat(voice.played).hasSize(10)

    // something that isn't the next word doesn't bring the hints back...
    recite("سَمِعَ", "مُوسَاا", "كَتَبَ")
    microphone.silence(3)
    advanceTimeBy(20_000)
    assertThat(voice.played).hasSize(10)

    // ...reciting the next word does
    val state = controller.state.value!!
    recite(state.passage.words[state.cursor].wasl)
    assertThat(controller.state.value!!.cursor).isEqualTo(state.cursor + 1)
    microphone.silence(3)
    advanceTimeBy(3_000)
    assertThat(voice.played).hasSize(11)
  }

  @Test
  fun `no hints during a phone call`() = runTest {
    preferences.hintDelaySeconds = 2
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()
    recite("قُل", "هُوَ")

    // the call silences the microphone: that's not the user being stuck
    inCall = true
    advanceTimeBy(1_000)
    microphone.silence(3)
    advanceTimeBy(10_000)
    assertThat(voice.played).isEmpty()
    assertThat(controller.state.value!!.cursor).isEqualTo(2)

    // after the call, hints are back
    inCall = false
    advanceTimeBy(5_000)
    assertThat(voice.played).isNotEmpty()
  }

  @Test
  fun `after the microphone fails and the user resumes, the wait for a hint starts over`() = runTest {
    preferences.hintDelaySeconds = 2
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()
    recite("قُل", "هُوَ")

    microphone.fail()
    runCurrent()
    assertThat(controller.state.value!!.status).isEqualTo(MemorizeState.Status.PAUSED)
    advanceTimeBy(30_000)

    controller.pauseOrResume()
    runCurrent()
    assertThat(controller.state.value!!.status).isEqualTo(MemorizeState.Status.LISTENING)
    microphone.silence(3)
    // a pause right after resuming isn't being stuck yet: the delay counts from the resume
    advanceTimeBy(1_200)
    assertThat(controller.state.value!!.cursor).isEqualTo(2)
    assertThat(voice.played).isEmpty()

    advanceTimeBy(5_000)
    assertThat(voice.played).isNotEmpty()
  }

  // ------------------------------------------------------------------------------------ helpers

  /** Each word comes out of the engine for one microphone frame. */
  private fun TestScope.recite(vararg words: String) {
    engine.script += words
    repeat(words.size + 2) { microphone.frame() }
    runCurrent()
  }

  /** How the first [count] words from [start] sound, as the speech model would hear them. */
  private suspend fun TestScope.pronunciations(start: SuraAyah, count: Int): Array<String> =
    LexiconRepository.forTest(context, StandardTestDispatcher(testScheduler)).passage(start)
      .words.take(count).map { it.wasl }.toTypedArray()

  private class ScriptedEngine : RecitationEngine {
    val script = ArrayDeque<String>()
    private var time = 0L

    override fun newStream(): RecitationStream = object : RecitationStream {
      override fun accept(samples: FloatArray, count: Int, speech: Boolean): List<RecognizedToken> {
        time += 100
        val word = script.removeFirstOrNull() ?: return emptyList()
        return listOf(RecognizedToken(word, time))
      }

      override fun finish(): List<RecognizedToken> = emptyList()
      override fun close() {}
    }

    override fun close() {}
  }

  private class FakeMicrophone : AudioSourceFactory {
    var running = false
    private var onFrame: ((FloatArray, Int, Boolean) -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null

    override fun create(
      onFrame: (FloatArray, Int, Boolean) -> Unit,
      onError: (Throwable) -> Unit,
    ): AudioSource {
      this.onFrame = onFrame
      this.onError = onError
      return object : AudioSource {
        override fun start() { running = true }
        override fun stop() { running = false }
      }
    }

    fun frame() = send(FloatArray(1_600) { 0.1f }, speech = true)

    fun silence(frames: Int) = repeat(frames) { send(FloatArray(1_600), speech = false) }

    fun fail() {
      running = false
      onError?.invoke(IllegalStateException("microphone taken"))
    }

    private fun send(samples: FloatArray, speech: Boolean) {
      if (running) onFrame?.invoke(samples, samples.size, speech)
    }
  }

  private class FakeVoice : HintVoice {
    val played = mutableListOf<List<WordId>>()

    private class Words(val words: List<WordId>) : HintVoice.Clip

    override fun prepare(qariId: Int, words: List<WordId>): HintVoice.Clip = Words(words)

    override fun play(clip: HintVoice.Clip, onStarted: () -> Unit, onFinished: () -> Unit) {
      played += (clip as Words).words
      onStarted()
      onFinished()
    }

    override fun stop() {}
  }
}
