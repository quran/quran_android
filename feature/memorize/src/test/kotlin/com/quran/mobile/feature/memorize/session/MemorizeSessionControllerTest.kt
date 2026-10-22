package com.quran.mobile.feature.memorize.session

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * The whole session pipeline with a scripted speech engine and a fake microphone: the lexicon,
 * tracker, hint scheduler and state publishing are the real ones.
 */
@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class MemorizeSessionControllerTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val events = RecitationEventPresenter()
  private val engine = ScriptedEngine()
  private val microphone = FakeMicrophone()
  private val voice = FakeVoice()
  @Volatile private var enginesCreated = 0
  @Volatile private var engineFailure: Throwable? = null
  @Volatile private var inCall = false
  private lateinit var controller: MemorizeSessionController

  private lateinit var preferences: MemorizePreferences

  @Before
  fun setUp() {
    preferences = MemorizePreferences(context).apply {
      correctorEnabled = true
      hintDelaySeconds = 15 // long enough that no hint is given on its own during a test
    }
    controller = MemorizeSessionController(
      lexicon = LexiconRepository(context),
      models = SpeechModelStore(context),
      preferences = preferences,
      recitationEventPresenter = events,
      engineFactory = {
        engineFailure?.let { throw it }
        enginesCreated++
        engine
      },
      promptPlayer = voice,
      audioSources = microphone,
      callState = { inCall },
    )
  }

  @After
  fun tearDown() {
    controller.stop()
    idle()
  }

  @Test
  fun `words are revealed as they are recited and the session follows the ayah`() {
    start(SuraAyah(112, 1))

    // Al-Ikhlas 1: قُل هُوَ ٱللَّهُ أَحَدٌ (as pronounced)
    recite("قُل", "هُوَ", "للَااهُ", "ءَحَدڇ")

    awaitUntil { controller.state.value?.cursor == 4 }
    val state = controller.state.value!!
    assertThat(state.isHidden(3)).isFalse()
    assertThat(state.isHidden(4)).isTrue()
    assertThat(state.currentAyah).isEqualTo(SuraAyah(112, 2))
    assertThat(events.recitationSession()?.currentAyah()).isEqualTo(SuraAyah(112, 2))
  }

  @Test
  fun `a wrong word is marked but not revealed`() {
    start(SuraAyah(112, 1))

    recite("قُل", "هُوَ")
    awaitUntil { controller.state.value?.cursor == 2 }
    recite("سَمِعَ", "مُوسَاا", "كَتَبَ")

    awaitUntil { controller.state.value?.mistake == 2 }
    assertThat(controller.state.value!!.isHidden(2)).isTrue()
  }

  @Test
  fun `a hint reveals and speaks the next word`() {
    start(SuraAyah(112, 1))

    controller.requestHint()

    awaitUntil { controller.state.value?.cursor == 1 }
    assertThat(controller.state.value!!.hinted).containsExactly(0)
    awaitUntil { voice.played.isNotEmpty() }
    assertThat(voice.played.single()).containsExactly(WordId(112, 1, 1))
  }

  @Test
  fun `stopping ends the session`() {
    start(SuraAyah(112, 1))

    controller.stop()
    idle()

    assertThat(controller.state.value).isNull()
    assertThat(events.recitationSession()).isNull()
    assertThat(microphone.running).isFalse()
  }

  @Test
  fun `starting again while the session is being prepared is ignored`() {
    controller.start(SuraAyah(112, 1))
    controller.start(SuraAyah(113, 1))
    awaitUntil { controller.state.value?.status == MemorizeState.Status.LISTENING }
    awaitUntil { enginesCreated > 0 }
    Thread.sleep(100)

    assertThat(controller.state.value!!.passage.start).isEqualTo(SuraAyah(112, 1))
    assertThat(enginesCreated).isEqualTo(1)
  }

  @Test
  fun `what is recited while the model loads isn't lost`() {
    val slowEngine = java.util.concurrent.CountDownLatch(1)
    controller = MemorizeSessionController(
      lexicon = LexiconRepository(context),
      models = SpeechModelStore(context),
      preferences = preferences,
      recitationEventPresenter = events,
      engineFactory = {
        slowEngine.await()
        engine
      },
      promptPlayer = voice,
      audioSources = microphone,
      callState = { inCall },
    )
    controller.start(SuraAyah(112, 1))
    awaitUntil { microphone.running }

    // the user starts reciting at once, while the model is still loading
    recite("قُل", "هُوَ")
    assertThat(controller.state.value!!.cursor).isEqualTo(0)
    slowEngine.countDown()

    awaitUntil { controller.state.value?.cursor == 2 }
  }

  @Test
  fun `stopping while the session is being prepared loads nothing`() {
    controller.start(SuraAyah(112, 1))
    controller.stop()
    Thread.sleep(300)
    idle()

    assertThat(controller.state.value).isNull()
    assertThat(enginesCreated).isEqualTo(0)
    assertThat(microphone.running).isFalse()
  }

  @Test
  fun `a session that can't start is reported and ended`() {
    engineFailure = OutOfMemoryError("model too large")
    var failures = 0
    val collector = CoroutineScope(Dispatchers.Main.immediate).launch {
      controller.failures.collect { failures++ }
    }

    controller.start(SuraAyah(112, 1))
    awaitUntil { failures == 1 }

    assertThat(controller.state.value).isNull()
    assertThat(events.recitationSession()).isNull()
    assertThat(microphone.running).isFalse()
    collector.cancel()

    // and the next start works
    engineFailure = null
    start(SuraAyah(112, 1))
  }

  @Test
  fun `in salah a stall while reciting the passage gets a hint`() {
    preferences.hintDelaySeconds = 2
    start(SuraAyah(112, 1), MemorizeState.Mode.SALAH)

    recite("قُل", "هُوَ")
    awaitUntil { controller.state.value?.cursor == 2 }
    microphone.silence(3)
    drain()

    awaitUntil { advanceClock(500); voice.played.isNotEmpty() }
    assertThat(voice.played.first()).containsExactly(WordId(112, 1, 3))
  }

  @Test
  fun `in salah no hint is given once the next rak'ah's Al-Fatiha begins`() {
    preferences.hintDelaySeconds = 2
    start(SuraAyah(108, 1), MemorizeState.Mode.SALAH)
    // إِنَّا أَعْطَيْنَاكَ ...
    recite(*pronunciations(SuraAyah(108, 1), 2))
    awaitUntil { controller.state.value?.cursor == 2 }

    // ruku, sujud... and the next rak'ah starts with Al-Fatiha
    recite(*pronunciations(SuraAyah(1, 2), 8))
    microphone.silence(3)
    drain()
    repeat(10) {
      advanceClock(1_000)
      Thread.sleep(150)
    }
    idle()

    assertThat(voice.played).isEmpty()
    assertThat(controller.state.value!!.cursor).isEqualTo(2)
  }

  @Test
  fun `after too many hints in a row it stays quiet until the user recites again`() {
    preferences.hintDelaySeconds = 2
    start(SuraAyah(112, 1))
    recite("قُل", "هُوَ")
    awaitUntil { controller.state.value?.cursor == 2 }
    microphone.silence(3)
    drain()

    // ten hints without the user going on, then it gives up
    awaitUntil { advanceClock(500); voice.played.size == 10 }
    repeat(5) { advanceClock(2_000); Thread.sleep(120) }
    assertThat(voice.played).hasSize(10)

    // something that isn't the next word doesn't bring the hints back...
    recite("سَمِعَ", "مُوسَاا", "كَتَبَ")
    microphone.silence(3)
    drain()
    repeat(5) { advanceClock(2_000); Thread.sleep(120) }
    assertThat(voice.played).hasSize(10)

    // ...reciting the next word does
    val state = controller.state.value!!
    recite(state.passage.words[state.cursor].wasl)
    awaitUntil { controller.state.value!!.cursor == state.cursor + 1 }
    microphone.silence(3)
    drain()
    awaitUntil { advanceClock(500); voice.played.size == 11 }
  }

  @Test
  fun `no hints during a phone call`() {
    preferences.hintDelaySeconds = 2
    start(SuraAyah(112, 1))
    recite("قُل", "هُوَ")
    awaitUntil { controller.state.value?.cursor == 2 }

    // the call silences the microphone: that's not the user being stuck
    inCall = true
    Thread.sleep(700)
    microphone.silence(3)
    drain()
    repeat(5) { advanceClock(2_000); Thread.sleep(120) }
    assertThat(voice.played).isEmpty()
    assertThat(controller.state.value!!.cursor).isEqualTo(2)

    // after the call, hints are back
    inCall = false
    awaitUntil { advanceClock(500); voice.played.isNotEmpty() }
  }

  @Test
  fun `after the microphone fails and the user resumes, the wait for a hint starts over`() {
    preferences.hintDelaySeconds = 2
    start(SuraAyah(112, 1))
    recite("قُل", "هُوَ")
    awaitUntil { controller.state.value?.cursor == 2 }

    microphone.fail()
    awaitUntil { controller.state.value?.status == MemorizeState.Status.PAUSED }
    Thread.sleep(200)
    advanceClock(30_000)

    controller.pauseOrResume()
    awaitUntil { controller.state.value?.status == MemorizeState.Status.LISTENING }
    microphone.silence(3)
    drain()
    // a pause right after resuming isn't being stuck yet: the delay counts from the resume
    Thread.sleep(300)
    advanceClock(1_200)
    Thread.sleep(300)
    idle()
    assertThat(controller.state.value!!.cursor).isEqualTo(2)
    assertThat(voice.played).isEmpty()

    awaitUntil { advanceClock(500); voice.played.isNotEmpty() }
  }

  // ------------------------------------------------------------------------------------ helpers

  private fun start(ayah: SuraAyah, mode: MemorizeState.Mode = MemorizeState.Mode.MEMORIZE) {
    controller.start(ayah, mode)
    awaitUntil { controller.state.value?.status == MemorizeState.Status.LISTENING }
    assertThat(microphone.running).isTrue()
  }

  /** Each word comes out of the engine for one microphone frame. */
  private fun recite(vararg words: String) {
    words.forEach { engine.script.add(it) }
    repeat(words.size + 2) { microphone.frame() }
  }

  private fun idle() = shadowOf(Looper.getMainLooper()).idle()

  /** How the first [count] words from [start] sound, as the speech model would hear them. */
  private fun pronunciations(start: SuraAyah, count: Int): Array<String> =
    runBlocking { LexiconRepository(context).passage(start) }.words.take(count).map { it.wasl }.toTypedArray()

  /** Moves the session's clock (SystemClock) forward; its timer notices on its next real tick. */
  private fun advanceClock(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

  /** Waits until every microphone frame sent so far has reached the speech engine. */
  private fun drain() = awaitUntil { engine.accepted.get() >= microphone.sent.get() }

  private fun awaitUntil(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 5_000
    while (System.currentTimeMillis() < deadline) {
      idle()
      if (condition()) return
      Thread.sleep(10)
    }
    idle()
    assertThat(condition()).isTrue()
  }

  private class ScriptedEngine : RecitationEngine {
    val script = ConcurrentLinkedQueue<String>()
    val accepted = AtomicInteger()
    private var time = 0L

    override fun newStream(): RecitationStream = object : RecitationStream {
      override fun accept(samples: FloatArray, count: Int, speech: Boolean): List<RecognizedToken> {
        accepted.incrementAndGet()
        time += 100
        val word = script.poll() ?: return emptyList()
        return listOf(RecognizedToken(word, time))
      }

      override fun finish(): List<RecognizedToken> = emptyList()
      override fun close() {}
    }

    override fun close() {}
  }

  private class FakeMicrophone : AudioSourceFactory {
    @Volatile var running = false
    val sent = AtomicInteger()
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
      if (!running) return
      sent.incrementAndGet()
      onFrame?.invoke(samples, samples.size, speech)
    }
  }

  private class FakeVoice : HintVoice {
    val played = ConcurrentLinkedQueue<List<WordId>>()

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
