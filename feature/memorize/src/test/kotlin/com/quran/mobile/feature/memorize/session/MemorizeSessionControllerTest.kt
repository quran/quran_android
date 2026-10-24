package com.quran.mobile.feature.memorize.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.audio.AudioSource
import com.quran.mobile.feature.memorize.audio.AudioSourceFactory
import com.quran.mobile.feature.memorize.check.DoubleCheck
import com.quran.mobile.feature.memorize.data.LexiconRepository
import com.quran.mobile.feature.memorize.data.LineSource
import com.quran.mobile.feature.memorize.data.PageLines
import com.quran.mobile.feature.memorize.data.Passage
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import com.quran.mobile.feature.memorize.prompt.HintVoice
import com.quran.mobile.feature.memorize.recording.Recorders
import com.quran.mobile.feature.memorize.recording.Recording
import com.quran.mobile.feature.memorize.recording.SessionRecorder
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

  private val recorders = FakeRecorders()
  private val loadedSuras = mutableListOf<IntRange>()

  private val doubleCheck = FakeDoubleCheck()

  private fun TestScope.controller(
    lookaheadWords: Int = 300,
    lines: LineSource = LineSource { emptyMap() },
  ): MemorizeSessionController = MemorizeSessionController.forTest(
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
    lines = lines,
    upcoming = { loadedSuras += it },
    recorders = recorders,
    lookaheadWords = lookaheadWords,
    doubleChecks = { doubleCheck.also { it.opened++ } },
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

    // ...reciting the next words does (a few: a single word can start several ayat)
    val state = controller.state.value!!
    recite(*state.passage.words.drop(state.cursor).take(3).map { it.wasl }.toTypedArray())
    assertThat(controller.state.value!!.cursor).isEqualTo(state.cursor + 3)
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

  @Test
  fun `the session goes on into the next sura instead of ending with this one`() = runTest {
    val controller = controller(lookaheadWords = 5)
    controller.start(SuraAyah(112, 1))
    runCurrent()
    assertThat(controller.state.value!!.passage.words.last().id.sura).isEqualTo(112)

    recite(*pronunciations(SuraAyah(112, 1), 15))

    val state = controller.state.value!!
    assertThat(state.status).isEqualTo(MemorizeState.Status.LISTENING)
    assertThat(state.cursor).isEqualTo(15)
    // Al-Falaq is there before the user gets to it, and hidden
    assertThat(state.passage.words[15].id).isEqualTo(WordId(113, 1, 1))
    assertThat(state.isHidden(15)).isTrue()

    recite(basmala())
    recite(*pronunciations(SuraAyah(113, 1), 4))
    assertThat(controller.state.value!!.cursor).isEqualTo(19)
    assertThat(controller.state.value!!.mistake).isNull()
    assertThat(events.recitationSession()?.currentAyah()).isEqualTo(SuraAyah(113, 2))
  }

  @Test
  fun `after Al-Fatiha the basmala of the next sura isn't a mistake`() = runTest {
    val controller = controller(lookaheadWords = 5)
    controller.start(SuraAyah(1, 1))
    runCurrent()
    val fatiha = controller.state.value!!.passage.words.count { it.id.sura == 1 }

    recite(*pronunciations(SuraAyah(1, 1), fatiha))
    recite(basmala())
    recite(*pronunciations(SuraAyah(2, 1), 3))

    val state = controller.state.value!!
    assertThat(state.cursor).isEqualTo(fatiha + 3)
    assertThat(state.mistake).isNull()
    assertThat(state.passage.words[fatiha].id).isEqualTo(WordId(2, 1, 1))
  }

  @Test
  fun `a session only finishes at the end of the Quran`() = runTest {
    val controller = controller(lookaheadWords = 5)
    controller.start(SuraAyah(114, 1))
    runCurrent()

    recite(*pronunciations(SuraAyah(114, 1), 20))

    assertThat(controller.state.value!!.status).isEqualTo(MemorizeState.Status.FINISHED)
    assertThat(microphone.running).isFalse()
  }

  @Test
  fun `after three single words the hints give the rest of the ayah or of the line`() = runTest {
    preferences.hintDelaySeconds = 2
    // Al-Ikhlas 3 on one line; 4 starts with two words at the end of the next line
    val lines = LineSource {
      mapOf(PageLines.key(112, 3) to intArrayOf(11, 11, 11, 11), PageLines.key(112, 4) to intArrayOf(12, 12, 13, 13, 13))
    }
    val controller = controller(lines = lines)
    controller.start(SuraAyah(112, 3))
    runCurrent()

    microphone.silence(3)
    advanceTimeBy(40_000)

    fun words(ayah: Int, vararg numbers: Int) = numbers.map { WordId(112, ayah, it) }
    assertThat(voice.played.take(6)).containsExactly(
      words(3, 1), words(3, 2), words(3, 3),
      // the rest of ayah 3 (one word), the rest of the line, then the rest of ayah 4 (it fits its line)
      words(3, 4), words(4, 1, 2), words(4, 3, 4, 5),
    ).inOrder()
  }

  @Test
  fun `a recorded session records what it hears and what happened to each word`() = runTest {
    val controller = controller()
    controller.start(SuraAyah(112, 1), record = true)
    runCurrent()
    val recorder = recorders.started.single()
    assertThat(controller.state.value!!.recordingId).isEqualTo(recorder.session)

    recite("قُل", "هُوَ")
    controller.requestHint()
    runCurrent()
    controller.pauseOrResume()
    runCurrent()
    // nothing is recorded while paused
    val frames = recorder.frames
    microphone.frame()
    runCurrent()
    controller.stop()
    runCurrent()

    assertThat(recorder.frames).isEqualTo(frames)
    assertThat(frames).isGreaterThan(0)
    assertThat(recorder.words.map { it.first to it.second }).containsExactly(
      WordId(112, 1, 1) to Recording.WordStatus.RECITED,
      WordId(112, 1, 2) to Recording.WordStatus.RECITED,
      WordId(112, 1, 3) to Recording.WordStatus.HINTED,
    ).inOrder()
    assertThat(recorder.events).containsExactly("pause", "finish").inOrder()
  }

  @Test
  fun `a session isn't recorded unless asked to`() = runTest {
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()
    recite("قُل")

    assertThat(recorders.started).isEmpty()
    assertThat(controller.state.value!!.recordingId).isNull()
  }

  @Test
  fun `the suras a session loads are announced, e g to download their hint voice`() = runTest {
    val controller = controller(lookaheadWords = 5)
    controller.start(SuraAyah(112, 1))
    runCurrent()
    assertThat(loadedSuras).containsExactly(112..112)

    recite(*pronunciations(SuraAyah(112, 1), 15))
    assertThat(loadedSuras).containsExactly(112..112, 113..113).inOrder()
  }

  @Test
  fun `the user can start anywhere on screen, which is hidden from the start`() = runTest {
    val controller = controller()
    // Al-Ikhlas on screen, up to its last ayah
    controller.start(SuraAyah(112, 1), startAnywhereUntil = SuraAyah(112, 4))
    runCurrent()
    assertThat(controller.state.value!!.started).isFalse()
    assertThat(controller.state.value!!.isHidden(0)).isTrue()
    assertThat(controller.state.value!!.isHidden(14)).isTrue()

    // starting at ayah 2 (اللَّهُ الصَّمَدُ, words 4 and 5) and on into ayah 3
    recite(*pronunciations(SuraAyah(112, 2), 4))

    val state = controller.state.value!!
    assertThat(state.started).isTrue()
    assertThat(state.firstWord).isEqualTo(4)
    assertThat(state.cursor).isEqualTo(8)
    // the words before the start are shown again, without counting as skipped
    assertThat(state.isHidden(0)).isFalse()
    assertThat(state.missed).isEmpty()
    assertThat(state.isHidden(8)).isTrue()
  }

  @Test
  fun `reciting what isn't on screen marks the first word, which is then given`() = runTest {
    preferences.hintDelaySeconds = 2
    val controller = controller()
    controller.start(SuraAyah(112, 1), startAnywhereUntil = SuraAyah(112, 4))
    runCurrent()

    // Al-Falaq instead
    recite(*pronunciations(SuraAyah(113, 1), 12))
    assertThat(controller.state.value!!.mistake).isEqualTo(0)
    assertThat(controller.state.value!!.started).isFalse()

    microphone.silence(3)
    advanceTimeBy(5_000)
    assertThat(voice.played.first()).containsExactly(WordId(112, 1, 1))
    assertThat(controller.state.value!!.started).isTrue()
  }

  @Test
  fun `no hint before the user starts, however long they wait`() = runTest {
    preferences.hintDelaySeconds = 2
    val controller = controller()
    controller.start(SuraAyah(112, 1), startAnywhereUntil = SuraAyah(112, 4))
    runCurrent()

    microphone.silence(3)
    advanceTimeBy(30_000)

    assertThat(voice.played).isEmpty()
    assertThat(controller.state.value!!.started).isFalse()
  }

  @Test
  fun `in salah a passage that isn't on screen is shown, not spoken`() = runTest {
    preferences.hintDelaySeconds = 2
    val controller = controller()
    controller.start(SuraAyah(112, 1), MemorizeState.Mode.SALAH, startAnywhereUntil = SuraAyah(112, 4))
    runCurrent()
    // Al-Fatiha, then Al-Falaq instead of the page's sura
    recite(*pronunciations(SuraAyah(1, 2), 29))
    recite(*pronunciations(SuraAyah(113, 1), 20))

    assertThat(controller.state.value!!.mistake).isEqualTo(0)
    microphone.silence(3)
    advanceTimeBy(10_000)
    assertThat(voice.played).isEmpty()
  }

  @Test
  fun `the user can start a few pages before or after the one on screen`() = runTest {
    val controller = controller()
    // the page on screen starts with Al-Ikhlas; Al-Masad before it and Al-Falaq after it are in reach
    controller.start(SuraAyah(111, 1), startAnywhereUntil = SuraAyah(113, 5), home = SuraAyah(112, 1))
    runCurrent()
    val home = controller.state.value!!.passage.words.indexOfFirst { it.id == WordId(112, 1, 1) }
    // until the user starts, the session is where the user is
    assertThat(controller.state.value!!.currentAyah).isEqualTo(SuraAyah(112, 1))

    recite(basmala())
    recite(*pronunciations(SuraAyah(113, 1), 4))

    val state = controller.state.value!!
    assertThat(state.started).isTrue()
    assertThat(state.firstWord).isEqualTo(home + 15)
    assertThat(state.currentAyah).isEqualTo(SuraAyah(113, 2))
  }

  @Test
  fun `starting outside the pages around marks the first word on screen`() = runTest {
    preferences.hintDelaySeconds = 2
    val controller = controller()
    controller.start(SuraAyah(111, 1), startAnywhereUntil = SuraAyah(113, 5), home = SuraAyah(112, 1))
    runCurrent()
    val home = controller.state.value!!.passage.words.indexOfFirst { it.id == WordId(112, 1, 1) }

    // Al-Fatiha is nowhere near
    recite(*pronunciations(SuraAyah(1, 2), 8))
    assertThat(controller.state.value!!.mistake).isEqualTo(home)

    microphone.silence(3)
    advanceTimeBy(5_000)
    assertThat(voice.played.first()).containsExactly(WordId(112, 1, 1))
  }

  @Test
  fun `with the double-check on, a hint for a word the user did say is dropped`() = runTest {
    preferences.doubleCheck = true
    preferences.hintDelaySeconds = 4
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()
    recite("قُل", "هُوَ")
    // the user said the next word, but the speech model missed it
    doubleCheck.saidWords += 2

    microphone.silence(3)
    advanceTimeBy(5_000)

    val state = controller.state.value!!
    assertThat(doubleCheck.asked).contains(2)
    assertThat(voice.played).isEmpty()
    // counted as recited: not hinted
    assertThat(state.cursor).isEqualTo(3)
    assertThat(state.hinted).isEmpty()
  }

  @Test
  fun `with the double-check on, a hint for a word the user didn't say is given`() = runTest {
    preferences.doubleCheck = true
    preferences.hintDelaySeconds = 4
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()
    recite("قُل", "هُوَ")

    microphone.silence(3)
    advanceTimeBy(5_000)

    assertThat(doubleCheck.asked).contains(2)
    assertThat(voice.played).contains(listOf(WordId(112, 1, 3)))
    assertThat(controller.state.value!!.hinted).contains(2)
  }

  @Test
  fun `with the double-check on, a mistake is only shown if the word wasn't said`() = runTest {
    preferences.doubleCheck = true
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()

    recite("قُل", "هُوَ", "سَمِعَ", "مُوسَاا", "كَتَبَ")
    // not before the check has listened again
    assertThat(controller.state.value!!.mistake).isNull()
    advanceTimeBy(1_500)
    runCurrent()

    assertThat(doubleCheck.asked).contains(2)
    assertThat(controller.state.value!!.mistake).isEqualTo(2)
  }

  @Test
  fun `with the double-check on, a mistake on a word that was said isn't shown`() = runTest {
    preferences.doubleCheck = true
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()
    doubleCheck.saidWords += 2

    recite("قُل", "هُوَ", "سَمِعَ", "مُوسَاا", "كَتَبَ")
    advanceTimeBy(1_500)
    runCurrent()

    assertThat(doubleCheck.asked).contains(2)
    assertThat(controller.state.value!!.mistake).isNull()
  }

  @Test
  fun `the double-check never hears the hints`() = runTest {
    preferences.doubleCheck = true
    preferences.hintDelaySeconds = 4
    voice.hold = true
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()
    recite("قُل")
    microphone.silence(3)
    advanceTimeBy(5_000)
    assertThat(voice.played).hasSize(1)

    // the microphone hears the hint being played
    microphone.loud(10)
    runCurrent()
    voice.finish()
    advanceTimeBy(1_000)
    // the next hint's check gets what the user said only
    microphone.silence(3)
    advanceTimeBy(5_000)

    assertThat(doubleCheck.asked).hasSize(2)
    assertThat(doubleCheck.heardHint).isFalse()
  }

  @Test
  fun `the double-check is off unless turned on, and released with the session`() = runTest {
    val controller = controller()
    controller.start(SuraAyah(112, 1))
    runCurrent()
    assertThat(doubleCheck.opened).isEqualTo(0)
    controller.stop()
    runCurrent()

    preferences.doubleCheck = true
    controller.start(SuraAyah(112, 1))
    runCurrent()
    assertThat(doubleCheck.opened).isEqualTo(1)
    controller.stop()
    runCurrent()
    assertThat(doubleCheck.closed).isEqualTo(1)
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

  /** The basmala as the speech model would hear it. */
  private suspend fun TestScope.basmala(): String =
    LexiconRepository.forTest(context, StandardTestDispatcher(testScheduler)).phrases(setOf("basmala")).first().second

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

    /** What the microphone hears while a hint plays. */
    fun loud(frames: Int) = repeat(frames) { send(FloatArray(1_600) { LOUD }, speech = true) }

    fun fail() {
      running = false
      onError?.invoke(IllegalStateException("microphone taken"))
    }

    private fun send(samples: FloatArray, speech: Boolean) {
      if (running) onFrame?.invoke(samples, samples.size, speech)
    }
  }

  private class FakeRecorders : Recorders {
    val started = mutableListOf<FakeRecorder>()

    override fun start(start: SuraAyah, mode: MemorizeState.Mode): SessionRecorder = FakeRecorder().also { started += it }
  }

  private class FakeRecorder : SessionRecorder {
    override val session = "recorded-session"
    var frames = 0
    val words = mutableListOf<Pair<WordId, Recording.WordStatus>>()
    val events = mutableListOf<String>()

    override fun write(samples: FloatArray) {
      frames++
    }

    override fun word(word: WordId, startMs: Long?, endMs: Long?, status: Recording.WordStatus, phonemes: String, endsSura: Boolean) {
      words += word to status
    }

    override fun pause() {
      events += "pause"
    }

    override fun finish() {
      events += "finish"
    }
  }

  private companion object {
    const val LOUD = 0.5f
  }

  private class FakeDoubleCheck : DoubleCheck {
    var opened = 0
    var closed = 0
    val saidWords = mutableSetOf<Int>()
    val asked = mutableListOf<Int>()
    // whether it was given what the microphone heard while a hint played (FakeMicrophone.loud)
    var heardHint = false

    override val lastMs: Long = 600

    override fun said(audio: FloatArray, passage: Passage, k: Int): Boolean {
      asked += k
      if (audio.any { it == LOUD }) heardHint = true
      return k in saidWords
    }

    override fun close() {
      closed++
    }
  }

  private class FakeVoice : HintVoice {
    val played = mutableListOf<List<WordId>>()
    // keep playing until finish()
    var hold = false
    private var playing: (() -> Unit)? = null

    fun finish() {
      playing?.invoke()
      playing = null
    }

    private class Words(val words: List<WordId>) : HintVoice.Clip

    override fun prepare(qariId: Int, words: List<WordId>): HintVoice.Clip = Words(words)

    override fun play(clip: HintVoice.Clip, onStarted: () -> Unit, onFinished: () -> Unit) {
      played += (clip as Words).words
      onStarted()
      if (hold) playing = onFinished else onFinished()
    }

    override fun stop() {}
  }
}
