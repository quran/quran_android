package com.quran.mobile.feature.memorize.session

import android.os.SystemClock
import android.util.Log
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.mobile.progress.session.AyahResult
import com.quran.mobile.progress.session.SessionRecord
import com.quran.mobile.feature.memorize.audio.AudioSource
import com.quran.mobile.feature.memorize.audio.AudioSourceFactory
import com.quran.mobile.feature.memorize.audio.CallState
import com.quran.mobile.feature.memorize.check.DoubleCheck
import com.quran.mobile.feature.memorize.check.DoubleCheckSource
import com.quran.mobile.feature.memorize.data.LexiconRepository
import com.quran.mobile.feature.memorize.data.LineSource
import com.quran.mobile.feature.memorize.data.PageLines
import com.quran.mobile.feature.memorize.data.Passage
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import com.quran.mobile.feature.memorize.prompt.HintVoice
import com.quran.mobile.feature.memorize.prompt.UpcomingSuras
import com.quran.mobile.feature.memorize.recording.Recorders
import com.quran.mobile.feature.memorize.recording.Recording
import com.quran.mobile.feature.memorize.recording.SessionEvent
import com.quran.mobile.feature.memorize.recording.SessionRecorder
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import com.quran.mobile.feature.memorize.tracker.PhonemeNormalizer
import com.quran.mobile.feature.memorize.tracker.RecitationTracker
import com.quran.mobile.feature.memorize.tracker.TimedPhoneme
import com.quran.mobile.feature.memorize.tracker.TrackerConfig
import com.quran.mobile.feature.memorize.tracker.TrackerEvent
import com.quran.mobile.feature.memorize.tracker.WordId
import com.quran.mobile.feature.memorize.tracker.engine.RecitationEngine
import com.quran.mobile.feature.memorize.tracker.engine.RecitationStream
import com.quran.mobile.feature.memorize.tracker.engine.RecognizedToken
import com.quran.mobile.feature.memorize.tracker.hint.HintScheduler
import com.quran.mobile.feature.memorize.tracker.salah.SalahFlow
import com.quran.recitation.common.Recitation
import com.quran.recitation.common.RecitationSession
import com.quran.recitation.common.RecitedAyah
import com.quran.recitation.events.RecitationEventPresenter
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Creates the speech engine; replaced by a fake in tests. */
fun interface RecitationEngineFactory {
  fun create(models: SpeechModelStore): RecitationEngine
}

/**
 * Runs memorization sessions: microphone -> speech engine -> follower (tracker) -> hint scheduler
 * -> state, plus the spoken hints.
 *
 * A session doesn't end with a sura: the user may go on to the next one (e.g. in taraweeh), so the
 * next suras are loaded (and hidden) before the user gets to them, up to the end of the Quran.
 *
 * All recognition and tracking happens on one serialized worker so audio, timer ticks and hints
 * never race. Audio frames are dropped as soon as they are decoded (with the optional double-check,
 * the last 8 seconds stay in memory for it to listen to again), unless the user records the session.
 * When a session ends, its counts (words recited, given, missed and marked, per ayah) go to
 * [SessionResults], which keeps them on the device for Progress if the user lets it; no audio and
 * no heard words.
 * (Diagnostics, for developers: `adb shell setprop log.tag.MemorizeTrace DEBUG` logs what each
 * session hears and decides, from its next start; off by default.)
 */
@SingleIn(AppScope::class)
class MemorizeSessionController private constructor(
  private val lexicon: LexiconRepository,
  private val models: SpeechModelStore,
  private val preferences: MemorizePreferences,
  private val recitationEventPresenter: RecitationEventPresenter,
  private val engineFactory: RecitationEngineFactory,
  private val promptPlayer: HintVoice,
  private val audioSources: AudioSourceFactory,
  private val callState: CallState,
  private val lines: LineSource,
  private val upcoming: UpcomingSuras,
  private val recorders: Recorders,
  private val doubleChecks: DoubleCheckSource,
  private val results: SessionResults,
  private val scope: CoroutineScope,
  /** Runs all recognition and tracking, one task at a time. */
  private val worker: CoroutineDispatcher,
  /** Runs the double-check (seconds at a time), one at a time, so it never holds up the worker. */
  private val checker: CoroutineDispatcher,
  private val clock: () -> Long,
  /** How many words to keep loaded ahead of the user. */
  private val lookaheadWords: Int,
  /** Where a session's diagnostics go, if anywhere (checked at each start). */
  private val traceSink: () -> ((String) -> Unit)?,
) {
  @OptIn(ExperimentalCoroutinesApi::class)
  @Inject constructor(
    lexicon: LexiconRepository,
    models: SpeechModelStore,
    preferences: MemorizePreferences,
    recitationEventPresenter: RecitationEventPresenter,
    engineFactory: RecitationEngineFactory,
    promptPlayer: HintVoice,
    audioSources: AudioSourceFactory,
    callState: CallState,
    lines: LineSource,
    upcoming: UpcomingSuras,
    recorders: Recorders,
    doubleChecks: DoubleCheckSource,
    results: SessionResults,
  ) : this(
    lexicon, models, preferences, recitationEventPresenter, engineFactory, promptPlayer, audioSources, callState,
    lines, upcoming, recorders, doubleChecks, results,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    worker = Dispatchers.Default.limitedParallelism(1),
    checker = Dispatchers.Default.limitedParallelism(1),
    clock = SystemClock::elapsedRealtime,
    lookaheadWords = LOOKAHEAD_WORDS,
    traceSink = { if (Log.isLoggable(TRACE_TAG, Log.DEBUG)) { message -> Log.d(TRACE_TAG, message) } else null },
  )

  private val _state = MutableStateFlow<MemorizeState?>(null)
  val state: StateFlow<MemorizeState?> = _state.asStateFlow()

  private val _failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  /** Emits when a session couldn't start; that session is over (the state is back to null). */
  val failures: SharedFlow<Unit> = _failures.asSharedFlow()

  // the following are only touched on the worker
  private var engine: RecitationEngine? = null
  private var stream: RecitationStream? = null
  private var follower: Follower? = null
  private var scheduler: HintScheduler? = null
  private var keepShortVowels = false
  private var silentSinceMs = -1L
  private var pauseHandled = true
  private var lastRecognizedMs = 0L
  private var gapHandled = true
  // after too many hints in a row, hints wait until the user recites a word on their own
  private var gaveUp = false
  // during a call the microphone hears nothing useful and hints must stay quiet
  private var inCall = false
  // how much audio the session has heard (the speech engine's clock)
  private var heardMs = 0L
  // the double-check, if on and loaded, and the last seconds the user said for it to hear (the
  // check itself only ever runs on the checker)
  private var check: DoubleCheck? = null
  private var heard: FloatArray? = null
  private var heardEnd = 0L
  // diagnostics (see the class comment)
  private var traceTo: ((String) -> Unit)? = null
  private var tracedSpeech = false
  private var tracedStatusAt = 0L
  // where each loaded word is on the pages (see PageLines), for hints of the rest of a line
  private val wordLines = HashMap<Int, IntArray>()
  // the session whose next suras are being loaded, if any
  private var extendingFor: String? = null
  // the basmala before a sura, once the passage goes beyond its first sura (see prepare)
  private var basmala: List<Pair<String, String>> = emptyList()
  // how many times a mistake was marked on each word (passage index), for its recording
  private val mistakeCounts = HashMap<Int, Int>()
  // the session whose results were last handed to SessionResults, so it goes there once
  private var resultsFor: String? = null
  // what a recording made to contribute was last told (see report)
  private var reportedSpeech: Boolean? = null
  private var reportedPrompting = false

  // the following are only touched on the main thread
  private var startJob: Job? = null
  private var mic: AudioSource? = null
  private var frames: Channel<Frame>? = null
  private var jobs: List<Job> = emptyList()
  // completed once the session's engine and follower can take frames
  private var ready = CompletableDeferred<Unit>()
  // while a hint is being played the microphone hears the reciter, not the user
  @Volatile private var prompting = false
  // where the session's audio goes when the user chose to record it
  @Volatile private var recorder: SessionRecorder? = null
  // bumped by every hint, by stopping and by a call: a hint only plays (and only ends the wait for
  // it) while its number is still the current one
  private val hintGeneration = AtomicInteger()

  private class Frame(val samples: FloatArray, val speech: Boolean)

  val isActive: Boolean get() = _state.value?.isActive == true

  /**
   * Starts a session at [startAyah]; with [record], the user's recitation is recorded too, and with
   * [contribute] the recording keeps what the speech model heard, to help improve the models.
   *
   * With [startAnywhereUntil] the user may start anywhere from [startAyah] up to that ayah (e.g. the
   * pages around the one on screen), and is expected at [home] (e.g. the first ayah on screen, where
   * starting elsewhere is marked): the words are hidden once it is known where they started.
   */
  fun start(
    startAyah: SuraAyah,
    mode: MemorizeState.Mode = MemorizeState.Mode.MEMORIZE,
    record: Boolean = false,
    startAnywhereUntil: SuraAyah? = null,
    home: SuraAyah = startAyah,
    contribute: Boolean = false,
  ) {
    if (isActive || startJob?.isActive == true) return
    val sessionId = UUID.randomUUID().toString()
    startJob = scope.launch {
      try {
        prepare(sessionId, startAyah, mode, record || contribute, contribute, startAnywhereUntil, home)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        // e.g. no storage to unpack the lexicon, or not enough memory for the speech model
        _failures.tryEmit(Unit)
        stop()
      }
    }
  }

  /** Loads what the session needs; false if the session was stopped in the meantime. */
  private suspend fun prepare(
    sessionId: String,
    startAyah: SuraAyah,
    mode: MemorizeState.Mode,
    record: Boolean,
    contribute: Boolean,
    startAnywhereUntil: SuraAyah?,
    home: SuraAyah,
  ): Boolean {
    val passage = lexicon.passage(startAyah, minWords = lookaheadWords, throughSura = startAnywhereUntil?.sura ?: startAyah.sura)
    require(passage.words.isNotEmpty()) { "nothing to recite from $startAyah" }
    // the words the user may start at, and where they are expected to
    val startWindow = startAnywhereUntil?.let { end ->
      passage.words.indexOfLast { it.id.sura < end.sura || (it.id.sura == end.sura && it.id.ayah <= end.ayah) } + 1
    } ?: 0
    val homeIndex = passage.words.indexOfFirst { it.id.sura == home.sura && it.id.ayah == home.ayah }.coerceAtLeast(0)
    val recorder = if (record) recorders.start(startAyah, mode, contribute, if (contribute) sessionInfo(mode) else emptyMap()) else null
    this.recorder = recorder
    _state.value = MemorizeState(
      sessionId, passage, System.currentTimeMillis(), MemorizeState.Status.PREPARING, mode, recordingId = recorder?.session,
      cursor = homeIndex,
      started = startWindow <= 1,
      firstWord = homeIndex,
      showMistakes = mode != MemorizeState.Mode.RECITE || preferences.reciteMarkMistakes,
    )
    publishSession()
    // Listen right away: loading the model takes seconds on slow phones, and what the user says
    // meanwhile waits in the frame queue until the session is ready to take it.
    val ready = CompletableDeferred<Unit>().also { this.ready = it }
    startListening()

    val salah = mode == MemorizeState.Mode.SALAH
    val kinds = if (salah) SalahFlow.PRAYER_PHRASES else setOf("isti'adha", BASMALA)
    val allPhrases = lexicon.phrases(kinds)
    // Al-Fatiha starts with the basmala itself, and At-Tawba has none: there it isn't said before
    // the passage, only before the suras that follow
    val basmalaFirst = salah || (home.sura != 1 && home.sura != 9)
    val phraseList = allPhrases.filter { (kind, _) -> kind != BASMALA || basmalaFirst }
    val fatihaWords = if (salah) lexicon.passage(SuraAyah(1, 2)).words else emptyList()
    val pageLines = lines.forSuras(startAyah.sura..passage.words.last().id.sura)
    upcoming.loaded(startAyah.sura..passage.words.last().id.sura)

    return withContext(worker) {
      // stop() cleans up on this worker too: if it already ran, don't load anything it won't free
      if (_state.value?.id != sessionId) return@withContext false
      keepShortVowels = preferences.strictTashkeel
      val phrases = phraseList.map { (kind, phonemes) -> kind to PhonemeNormalizer.normalize(phonemes, keepShortVowels) }
      basmala = if (basmalaFirst) {
        emptyList()
      } else {
        allPhrases.filter { it.first == BASMALA }.map { (kind, phonemes) -> kind to PhonemeNormalizer.normalize(phonemes, keepShortVowels) }
      }
      wordLines.clear()
      wordLines.putAll(pageLines)
      mistakeCounts.clear()
      reportedSpeech = null
      reportedPrompting = false
      extendingFor = null
      val words = passage.words.map { PhonemeNormalizer.normalize(it.wasl, keepShortVowels) }
      val fatiha = fatihaWords.map { PhonemeNormalizer.normalize(it.wasl, keepShortVowels) }
      // which ayah each word is in: restarts are found behind, and (but in salah) no ayah is left out
      val ayahs = passage.words.map { ayahId(it.id) }
      val engine = engine ?: engineFactory.create(models).also { engine = it }
      stream = engine.newStream()
      follower = if (salah) {
        SalahFollower(
          SalahFlow(
            words, fatiha, phrases, startWindow = startWindow, startIndex = homeIndex,
            passageAyahs = ayahs, fatihaAyahs = fatihaWords.map { ayahId(it.id) },
          )
        )
      } else {
        MemorizeFollower(
          RecitationTracker(
            words, trackerConfig(), startIndex = homeIndex, phrases = phrases, startWindow = startWindow, ayahs = ayahs,
          )
        )
      }
      // the session's clock starts now (the trace's times too)
      heardMs = 0L
      traceTo = traceSink()
      tracedSpeech = false
      tracedStatusAt = 0L
      loadDoubleCheck(sessionId)
      follower?.trace { message -> trace { message } }
      trace {
        "start $mode ${passage.words.first().id}..${passage.words.last().id} (${passage.words.size} words) " +
          "window=$startWindow home=$homeIndex ${passage.words[homeIndex].id} record=$record"
      }
      scheduler = HintScheduler(preferences.hintConfig(mode))
      silentSinceMs = -1L
      pauseHandled = true
      gapHandled = true
      gaveUp = false
      inCall = false
      armIfAllowed()
      ready.complete(Unit)
      true
    }
  }

  fun pauseOrResume() {
    val current = _state.value ?: return
    when (current.status) {
      MemorizeState.Status.LISTENING -> pause()
      MemorizeState.Status.PAUSED -> {
        startListening()
        scope.launch(worker) { armIfAllowed() }
      }
      else -> Unit
    }
  }

  private fun pause() {
    stopListening()
    // what was recorded so far can be listened to
    recorder?.pause()
    scope.launch(worker) { scheduler?.disarm()?.let { applyEffects(it) } }
    update { it.copy(status = MemorizeState.Status.PAUSED) }
    recitationEventPresenter.onListeningStateChange(false)
  }

  fun togglePeek() = update { it.copy(peeking = !it.peeking) }

  /** Gives the next word(s) now, as if the hint delay had passed (once the session is ready). */
  fun requestHint() {
    val ready = ready
    scope.launch(worker) {
      ready.await()
      giveHint(preferences.wordsPerHint, HintScheduler.Extent.WORDS)
    }
  }

  fun stop() {
    startJob?.cancel()
    startJob = null
    hintGeneration.incrementAndGet()
    promptPlayer.stop()
    prompting = false
    stopListening()
    finishRecording()
    val ended = _state.value
    scope.launch(worker) {
      ended?.let { recordResults(it, finished = false) }
      releaseEngine()
      follower = null
      scheduler = null
    }
    _state.value = null
    recitationEventPresenter.onListeningStateChange(false)
    recitationEventPresenter.onRecitationSessionChange(null)
  }

  // ------------------------------------------------------------------------------- listening

  private fun startListening() {
    val channel = Channel<Frame>(capacity = MAX_QUEUED_FRAMES, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    frames = channel
    val mic = audioSources.create(
      onFrame = { samples, count, speech -> channel.trySend(Frame(samples.copyOf(count), speech)) },
      onError = { scope.launch { onMicrophoneError() } },
    )
    this.mic = mic
    val ready = ready
    jobs = listOf(
      scope.launch(worker) {
        ready.await()
        for (frame in channel) process(frame)
      },
      scope.launch(worker) {
        var ticks = 0
        while (isActive) {
          delay(TICK_MS)
          if (ticks++ % CALL_CHECK_TICKS == 0) checkCall()
          // before the user started, only starting elsewhere gets a hint (the first word), and not
          // while what they recite is heard at two places and the start waits for the difference
          if ((follower?.started != false || _state.value?.mistake != null) && follower?.startPending != true) {
            scheduler?.onTick(now())?.let { applyEffects(it) }
          }
        }
      },
    )
    mic.start()
    update { it.copy(status = MemorizeState.Status.LISTENING) }
    recitationEventPresenter.onListeningStateChange(true)
  }

  private fun stopListening() {
    mic?.stop()
    mic = null
    frames?.close()
    frames = null
    jobs.forEach { it.cancel() }
    jobs = emptyList()
  }

  private fun onMicrophoneError() {
    if (_state.value?.status == MemorizeState.Status.LISTENING) pause()
  }

  private fun process(frame: Frame) {
    val stream = stream ?: return
    val follower = follower ?: return
    val scheduler = scheduler ?: return
    // everything the microphone heard, hints included
    recorder?.write(frame.samples)
    val frameStartMs = heardMs
    heardMs += frame.samples.size * 1000L / SAMPLE_RATE
    reportEdges(frameStartMs, frame.speech)
    heard?.let { buffer ->
      // silence while a hint plays: the check must not hear Husary say the word
      for (x in frame.samples) buffer[(heardEnd++ % buffer.size).toInt()] = if (prompting) 0f else x
    }
    if (prompting) {
      // keep the stream's timeline continuous, but don't let the hint be recognized as the user
      stream.accept(FloatArray(frame.samples.size), speech = false)
      return
    }
    val now = now()
    scheduler.onSpeechActivity(now, frame.speech)
    val tokens = stream.accept(frame.samples, speech = frame.speech)
    if (tokens.isNotEmpty()) recorder?.takeIf { it.keepsDetails }?.heard(tokens)
    if (traceTo != null) {
      if (frame.speech != tracedSpeech) {
        tracedSpeech = frame.speech
        trace { if (frame.speech) "speech" else "silence" }
      }
      if (tokens.isNotEmpty()) trace { "heard ${tokens.joinToString("") { it.text }}" }
      if (heardMs - tracedStatusAt >= TRACE_STATUS_MS) {
        tracedStatusAt = heardMs
        trace {
          "status cursor=${follower.cursor} started=${follower.started} canHint=${follower.canHint} " +
            "armed=${scheduler.isArmed} gaveUp=$gaveUp mistake=${_state.value?.mistake}"
        }
      }
    }
    if (tokens.isNotEmpty()) {
      val events = follower.feed(tokens.toPhonemes())
      if (events.isNotEmpty()) onTrackerEvents(events)
    }
    trackPause(frame.speech, recognized = tokens.isNotEmpty(), now)
    // e.g. Al-Fatiha of the next rak'ah has begun, which the follower reports without events
    disarmIfNotAllowed()
  }

  /**
   * Tells the follower when the user has paused (e.g. for ruku after the takbir): after a moment of
   * silence, or once nothing new has been recognized for a while, because a mosque's loudspeakers
   * and the people around may never sound silent.
   */
  private fun trackPause(speech: Boolean, recognized: Boolean, now: Long) {
    if (speech) {
      silentSinceMs = -1L
      pauseHandled = false
    } else {
      if (silentSinceMs < 0) silentSinceMs = now
      if (!pauseHandled && now - silentSinceMs >= PAUSE_MS) {
        pauseHandled = true
        onPause()
      }
    }
    if (recognized) {
      lastRecognizedMs = now
      gapHandled = false
    } else if (!gapHandled && now - lastRecognizedMs >= RECOGNITION_GAP_MS) {
      gapHandled = true
      onPause()
    }
  }

  private fun onPause() {
    trace { "pause" }
    val events = follower?.onPause().orEmpty()
    if (events.isNotEmpty()) onTrackerEvents(events)
  }

  /** No hints while the phone rings or is in a call; they start over once it ends. */
  private fun checkCall() {
    val calling = callState.isInCall()
    if (calling == inCall) return
    inCall = calling
    report(if (calling) SessionEvent.Kind.CALL_STARTED else SessionEvent.Kind.CALL_ENDED, heardMs)
    if (calling) {
      // a hint cut short never finishes: stop waiting for it
      hintGeneration.incrementAndGet()
      scope.launch {
        promptPlayer.stop()
        prompting = false
      }
      scheduler?.disarm()?.let { applyEffects(it) }
    } else {
      armIfAllowed()
    }
  }

  private fun List<RecognizedToken>.toPhonemes(): List<TimedPhoneme> = flatMap { token ->
    PhonemeNormalizer.normalize(token.text, keepShortVowels).map { TimedPhoneme(it, token.timeMs) }
  }

  // --------------------------------------------------------------------------- tracker events

  private fun onTrackerEvents(events: List<TrackerEvent>) {
    val follower = follower ?: return
    val scheduler = scheduler ?: return
    val passage = _state.value?.passage ?: return
    val effects = ArrayList<HintScheduler.Effect>()
    val missed = HashSet<Int>()
    if (events.any { it is TrackerEvent.WordRecited }) gaveUp = false
    armIfAllowed()
    for (event in events) {
      trace {
        val word = passage.words.getOrNull(event.index)?.id
        when (event) {
          is TrackerEvent.Mistake -> "mistake ${event.kind} at ${event.index} $word"
          is TrackerEvent.NonTarget -> "phrase ${event.kind}"
          // names spelled out: class names don't survive R8 in release builds
          is TrackerEvent.WordRecited -> "WordRecited ${event.index} $word"
          is TrackerEvent.WordMissed -> "WordMissed ${event.index} $word"
          is TrackerEvent.Repetition -> "Repetition ${event.index} $word"
        }
      }
      when (event) {
        is TrackerEvent.WordRecited -> recordWord(passage, event.index, event.startMs ?: event.timeMs, event.timeMs, Recording.WordStatus.RECITED)
        is TrackerEvent.WordMissed -> {
          missed += event.index
          recordWord(passage, event.index, null, null, Recording.WordStatus.MISSED)
        }
        is TrackerEvent.Repetition -> {
          effects += scheduler.onRepetition(now())
          report(SessionEvent.Kind.REPETITION, event.timeMs, passage.words.getOrNull(event.index)?.id) {
            passage.words.getOrNull(event.lastIndex)?.id?.let { "${it.sura}:${it.ayah}:${it.word}" }
          }
        }
        is TrackerEvent.Mistake -> {
          report(SessionEvent.Kind.MISTAKE, event.timeMs, passage.words.getOrNull(event.index)?.id) {
            event.kind.name.lowercase(Locale.ROOT)
          }
          if (scheduler.isArmed) {
            effects += scheduler.onMistake(now(), passage.words[event.index].id)
          } else if (event.kind == TrackerEvent.Mistake.Kind.ELSEWHERE) {
            // e.g. in salah, before the passage: shown, not spoken
            showMistake(event.index)
          }
        }
        is TrackerEvent.NonTarget -> report(SessionEvent.Kind.PHRASE, event.timeMs) { event.kind }
      }
    }
    val progressed = events.any { it is TrackerEvent.WordRecited || it is TrackerEvent.WordMissed }
    if (progressed) {
      effects += scheduler.onWordRecited(now(), atAyahBoundary = passage.atAyahBoundary(follower.cursor))
    }
    val cursor = follower.cursor
    val started = follower.started
    val firstRecited = events.filterIsInstance<TrackerEvent.WordRecited>().minOfOrNull { it.index }
    update { state ->
      val justStarted = started && !state.started
      state.copy(
        cursor = cursor,
        missed = state.missed + missed,
        started = started,
        firstWord = if (justStarted) firstRecited ?: cursor else state.firstWord,
        // the start found: a mark for starting elsewhere has served its purpose
        mistake = if (justStarted) null else state.mistake,
      )
    }
    applyEffects(effects)
    disarmIfNotAllowed()
    onPassageProgress()
  }

  /** Worker only. Prepares what comes next: the basmala before the next sura, more suras, the end. */
  private fun onPassageProgress() {
    val follower = follower ?: return
    val state = _state.value ?: return
    val passage = state.passage
    if (basmala.isNotEmpty() && follower.cursor < passage.words.size &&
      passage.words[follower.cursor].id.sura != passage.words[0].id.sura
    ) {
      follower.addPhrases(basmala)
      basmala = emptyList()
    }
    if (follower.isDone && passage.reachesEnd) {
      finish()
      return
    }
    if (extendingFor == state.id || passage.reachesEnd || passage.words.size - follower.cursor > lookaheadWords) return
    extendingFor = state.id
    val session = state.id
    val lastSura = passage.words.last().id.sura
    scope.launch(worker) {
      try {
        val more = lexicon.nextSuras(lastSura, lookaheadWords)
        if (more.words.isEmpty()) return@launch
        val moreLines = lines.forSuras(more.words.first().id.sura..more.words.last().id.sura)
        // the session may have ended (or another started) while the suras were read
        if (_state.value?.id != session || this@MemorizeSessionController.follower !== follower) return@launch
        follower.extend(more.words.map { PhonemeNormalizer.normalize(it.wasl, keepShortVowels) }, more.words.map { ayahId(it.id) })
        trace { "passage extended to ${more.words.last().id}" }
        wordLines.putAll(moreLines)
        update { if (it.id == session) it.copy(passage = it.passage + more) else it }
        upcoming.loaded(more.words.first().id.sura..more.words.last().id.sura)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        // e.g. no storage left to read from: tried again on the next word
      } finally {
        if (extendingFor == session) extendingFor = null
      }
      onPassageProgress()
    }
  }

  /** In salah, hints stop as soon as the passage isn't being recited. */
  private fun disarmIfNotAllowed() {
    val follower = follower ?: return
    val scheduler = scheduler ?: return
    if (!follower.canHint && scheduler.isArmed) applyEffects(scheduler.disarm())
  }

  /** Hints are only armed when the follower allows them (always, or in salah while reciting). */
  private fun armIfAllowed() {
    val follower = follower ?: return
    val scheduler = scheduler ?: return
    val passage = _state.value?.passage ?: return
    if (follower.canHint && !scheduler.isArmed && !gaveUp && !inCall &&
      _state.value?.status != MemorizeState.Status.PAUSED
    ) {
      applyEffects(scheduler.arm(now(), atAyahBoundary = passage.atAyahBoundary(follower.cursor)))
    }
  }

  private fun applyEffects(effects: List<HintScheduler.Effect>) {
    for (effect in effects) {
      trace { "hint scheduler: $effect" }
      when (effect) {
        is HintScheduler.Effect.MarkMistake -> if (!checkMistake(effect.word)) {
          _state.value?.passage?.words?.indexOfFirst { it.id == effect.word }?.takeIf { it >= 0 }?.let { showMistake(it) }
        }
        is HintScheduler.Effect.ClearMistake -> {
          if (_state.value?.mistake != null) report(SessionEvent.Kind.MARK_CLEARED, heardMs, effect.word)
          update { it.copy(mistake = null) }
        }
        is HintScheduler.Effect.GiveHint -> if (!checkHint(effect.count, effect.extent)) giveHint(effect.count, effect.extent)
        HintScheduler.Effect.GiveUp -> {
          gaveUp = true
          report(SessionEvent.Kind.GAVE_UP, heardMs)
          // ten hints and the user went on regardless: rather than stay where they aren't, follow
          // them (what was never heard is marked as skipped)
          follower?.releaseHold()
        }
        // no hint will give the word the user went on past: after the delay, follow them (what
        // they left out is marked as skipped)
        HintScheduler.Effect.FollowOn -> follower?.releaseHold()
      }
    }
  }

  private fun giveHint(count: Int, extent: HintScheduler.Extent) {
    val follower = follower ?: return
    val scheduler = scheduler ?: return
    val passage = _state.value?.passage ?: return
    val first = follower.cursor
    follower.markHinted(hintLength(passage, first, count, extent))
    val hinted = (first until follower.cursor).toList()
    if (hinted.isEmpty()) return
    hinted.forEach { recordWord(passage, it, heardMs, null, Recording.WordStatus.HINTED) }
    report(SessionEvent.Kind.HINT, heardMs, passage.words[first].id) { hinted.size.toString() }
    scheduler.onHintStarted()
    update { it.copy(cursor = follower.cursor, hinted = it.hinted + hinted, mistake = null, started = follower.started) }
    val words = hinted.map { passage.words[it].id }.filter { it.sura == passage.words[first].id.sura }
    val generation = hintGeneration.incrementAndGet()
    // finding the audio reads files: here on the worker, not on the main thread
    val clip = promptPlayer.prepare(preferences.promptQariId, words)
    scope.launch {
      if (generation != hintGeneration.get()) return@launch
      if (clip == null) {
        hintFinished(generation)
      } else {
        promptPlayer.play(
          clip,
          onStarted = { if (generation == hintGeneration.get()) prompting = true },
          onFinished = { hintFinished(generation) },
        )
      }
    }
  }

  /**
   * How many words a hint gives from [first]: [count], or for [HintScheduler.Extent.AYAH_OR_LINE] the
   * rest of the ayah if it is no longer than the line, else the rest of the line (and the next line
   * if only one word is left on this one), as the page type in use breaks its lines.
   */
  private fun hintLength(passage: Passage, first: Int, count: Int, extent: HintScheduler.Extent): Int {
    val words = passage.words
    if (extent == HintScheduler.Extent.WORDS || first >= words.size) return count
    var ayahEnd = first
    while (!passage.endsAyah(ayahEnd)) ayahEnd++
    val restOfAyah = ayahEnd - first + 1
    fun lineOf(index: Int): Int? = words[index].id.let { wordLines[PageLines.key(it.sura, it.ayah)]?.getOrNull(it.word - 1) }
    val line = lineOf(first) ?: return maxOf(count, minOf(restOfAyah, UNKNOWN_LINE_WORDS))
    var lineStart = first
    while (lineStart > 0 && lineOf(lineStart - 1) == line) lineStart--
    var lineEnd = first
    while (lineEnd + 1 < words.size && lineOf(lineEnd + 1) == line) lineEnd++
    if (restOfAyah <= lineEnd - lineStart + 1) return maxOf(count, restOfAyah)
    if (lineEnd == first && lineEnd + 1 < words.size) {
      val next = lineOf(lineEnd + 1)
      while (lineEnd + 1 < words.size && lineOf(lineEnd + 1) == next) lineEnd++
    }
    return maxOf(count, lineEnd - first + 1)
  }

  /** On the main thread, once hint number [generation] has been played (or had nothing to play). */
  private fun hintFinished(generation: Int) {
    scope.launch {
      // let the room go quiet before listening to the user again
      if (prompting) delay(PROMPT_TAIL_MS)
      if (generation != hintGeneration.get()) return@launch
      prompting = false
      withContext(worker) {
        scheduler?.onHintFinished(now())
        onPassageProgress()
      }
    }
  }

  private fun finish() {
    scope.launch {
      stopListening()
      finishRecording()
      update { it.copy(status = MemorizeState.Status.FINISHED, mistake = null) }
      recitationEventPresenter.onListeningStateChange(false)
      // the passage is done; the user may keep reading, but the model's memory isn't needed
      withContext(worker) {
        _state.value?.let { recordResults(it, finished = true) }
        releaseEngine()
      }
    }
  }

  /**
   * Worker only. Hands what [state]'s session came to to [results], once per session: from the word
   * the user started at to where they got. A session that never got going (it didn't start, or
   * nothing was revealed) has nothing to give.
   */
  private fun recordResults(state: MemorizeState, finished: Boolean) {
    if (resultsFor == state.id) return
    if (state.status == MemorizeState.Status.PREPARING || !state.started) return
    val words = state.passage.words
    val from = state.firstWord
    val until = state.cursor.coerceAtMost(words.size)
    if (until <= from) return
    resultsFor = state.id
    val ayahs = LinkedHashMap<SuraAyah, IntArray>()
    var hinted = 0
    var missed = 0
    for (index in from until until) {
      val counts = ayahs.getOrPut(state.passage.ayahOf(index)) { IntArray(4) }
      counts[0]++
      when (index) {
        in state.hinted -> { counts[1]++; hinted++ }
        in state.missed -> { counts[2]++; missed++ }
      }
    }
    mistakeCounts.forEach { (index, count) ->
      if (index < words.size) ayahs.getOrPut(state.passage.ayahOf(index)) { IntArray(4) }[3] += count
    }
    val ayahWords = words.groupingBy { SuraAyah(it.id.sura, it.id.ayah) }.eachCount()
    val first = state.passage.ayahOf(from)
    val last = state.passage.ayahOf(until - 1)
    val count = until - from
    results.record(
      SessionRecord(
        id = state.id,
        mode = state.mode.name,
        startedAtMs = state.startedAt,
        endedAtMs = System.currentTimeMillis(),
        listenedMs = heardMs,
        startSura = first.sura,
        startAyah = first.ayah,
        endSura = last.sura,
        endAyah = last.ayah,
        words = count,
        recited = count - hinted - missed,
        hinted = hinted,
        missed = missed,
        mistakes = mistakeCounts.values.sum(),
        finished = finished,
        recordingId = state.recordingId,
        ayahs = ayahs.map { (ayah, counts) ->
          AyahResult(
            sura = ayah.sura,
            ayah = ayah.ayah,
            ayahWords = ayahWords[ayah] ?: counts[0],
            words = counts[0],
            hinted = counts[1],
            missed = counts[2],
            mistakes = counts[3],
          )
        },
      )
    )
  }

  /** Worker only. The model takes a lot of memory; it is loaded again for the next session. */
  private fun releaseEngine() {
    runCatching { stream?.close() }
    runCatching { engine?.close() }
    engine = null
    stream = null
    releaseDoubleCheck()
  }

  // ----------------------------------------------------------------------------- double-check

  /** Worker only. Loads the double-check if it is on (seconds: the session doesn't wait for it). */
  private fun loadDoubleCheck(sessionId: String) {
    releaseDoubleCheck()
    if (!preferences.doubleCheck) return
    scope.launch(checker) {
      val loaded = runCatching { doubleChecks.open() }.getOrNull() ?: return@launch
      withContext(worker) {
        if (_state.value?.id != sessionId || follower == null) {
          withContext(checker) { loaded.close() }
          return@withContext
        }
        check = loaded
        heard = FloatArray(CHECK_SECONDS * SAMPLE_RATE)
        heardEnd = 0L
        trace { "double-check loaded" }
      }
    }
  }

  /** Worker only. */
  private fun releaseDoubleCheck() {
    val loaded = check ?: return
    check = null
    heard = null
    // after any check still running
    scope.launch(checker) { loaded.close() }
  }

  /** The last seconds the user said, oldest first. */
  private fun heardAudio(): FloatArray {
    val buffer = heard ?: return FloatArray(0)
    val n = minOf(heardEnd, buffer.size.toLong()).toInt()
    return FloatArray(n) { buffer[((heardEnd - n + it) % buffer.size).toInt()] }
  }

  /** Runs [check] on what the user said, then [then] on the worker with whether [index] was said. */
  private fun doubleCheck(check: DoubleCheck, passage: Passage, index: Int, then: (Boolean) -> Unit) {
    val audio = heardAudio()
    val askedAtMs = heardMs
    scope.launch(checker) {
      val said = runCatching { check.said(audio, passage, index) }.getOrDefault(false)
      val ms = check.lastMs
      preferences.recordDoubleCheckMs(ms)
      withContext(worker) {
        trace { "double-check ${passage.words[index].id}: ${if (said) "said" else "not said"} ($ms ms)" }
        report(SessionEvent.Kind.DOUBLE_CHECK, askedAtMs, passage.words[index].id) { "${if (said) "said" else "not said"} $ms ms" }
        then(said)
      }
    }
  }

  /**
   * Worker only. Before a mistake is shown: a second later (the user may be in the middle of the
   * word), the double-check listens again; the mark is only shown if the word wasn't said after
   * all. True if the check took the mark over.
   */
  private fun checkMistake(word: WordId): Boolean {
    val check = check ?: return false
    val follower = follower ?: return false
    val passage = _state.value?.passage ?: return false
    val index = passage.words.indexOfFirst { it.id == word }.takeIf { it >= 0 } ?: return false
    scope.launch(worker) {
      delay(CHECK_MISTAKE_AFTER_MS)
      if (this@MemorizeSessionController.follower !== follower || follower.cursor != index) return@launch
      doubleCheck(check, passage, index) { said ->
        // still the same mistake, still not recited, and the session still listening
        if (this@MemorizeSessionController.follower === follower && follower.cursor == index && !said &&
          scheduler?.isArmed == true
        ) {
          showMistake(index)
        }
      }
    }
    return true
  }

  /**
   * Worker only. Before a hint is spoken, the double-check listens again: if the user did say the
   * word, it counts as recited (as the hint would have) and nothing is said. True if the check took
   * the hint over.
   */
  private fun checkHint(count: Int, extent: HintScheduler.Extent): Boolean {
    val check = check ?: return false
    val follower = follower ?: return false
    val scheduler = scheduler ?: return false
    val passage = _state.value?.passage ?: return false
    val first = follower.cursor
    if (first >= passage.words.size) return false
    // no other hint while this one is being checked
    scheduler.onHintStarted()
    doubleCheck(check, passage, first) { said ->
      if (this@MemorizeSessionController.follower !== follower || this@MemorizeSessionController.scheduler !== scheduler) {
        return@doubleCheck
      }
      when {
        // the user went on meanwhile (no hint needed), or paused, or a call came
        follower.cursor != first || !scheduler.isArmed || inCall ||
          _state.value?.status != MemorizeState.Status.LISTENING -> scheduler.onHintFinished(now())
        said -> {
          follower.markHinted(1)
          recordWord(passage, first, null, null, Recording.WordStatus.RECITED)
          update { it.copy(cursor = follower.cursor, mistake = null, started = follower.started) }
          scheduler.onHintFinished(now())
          applyEffects(scheduler.onWordRecited(now(), atAyahBoundary = passage.atAyahBoundary(follower.cursor)))
          onPassageProgress()
        }
        else -> {
          // giveHint() starts the hint over
          scheduler.onHintFinished(now())
          giveHint(count, extent)
        }
      }
    }
    return true
  }

  /** Main thread, once listening has stopped. */
  private fun finishRecording() {
    val recorder = recorder ?: return
    this.recorder = null
    if (!recorder.keepsDetails) {
      recorder.finish()
      return
    }
    // after the last frame: what the recognizer still had of the last words goes with the recording
    scope.launch(worker) {
      runCatching { stream?.finish() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { recorder.heard(it) }
      recorder.finish()
    }
  }

  /** Worker only. Marks the word at [index] as a mistake (if marks are shown), and counts it. */
  private fun showMistake(index: Int) {
    val state = _state.value ?: return
    if (state.mistake == index) return
    mistakeCounts[index] = (mistakeCounts[index] ?: 0) + 1
    report(SessionEvent.Kind.MARK_SHOWN, heardMs, state.passage.words.getOrNull(index)?.id) {
      if (state.showMistakes) null else "not shown"
    }
    update { if (it.showMistakes) it.copy(mistake = index) else it }
  }

  /** Worker only. Tells a recording made to contribute what the session did at [atMs]. */
  private inline fun report(kind: SessionEvent.Kind, atMs: Long, word: WordId? = null, detail: () -> String? = { null }) {
    val recorder = recorder?.takeIf { it.keepsDetails } ?: return
    recorder.event(SessionEvent(atMs, kind, word, detail()))
  }

  /** Worker only. When the user started or stopped speaking, and when a hint started or ended playing. */
  private fun reportEdges(atMs: Long, speech: Boolean) {
    if (recorder?.keepsDetails != true) return
    if (speech != reportedSpeech) {
      reportedSpeech = speech
      report(if (speech) SessionEvent.Kind.SPEECH else SessionEvent.Kind.SILENCE, atMs)
    }
    val playing = prompting
    if (playing != reportedPrompting) {
      reportedPrompting = playing
      report(if (playing) SessionEvent.Kind.PROMPT_STARTED else SessionEvent.Kind.PROMPT_ENDED, atMs)
    }
  }

  /** What a recording made to contribute keeps about the session: its settings and speech model. */
  private fun sessionInfo(mode: MemorizeState.Mode): Map<String, Any> = mapOf(
    "mode" to mode.name.lowercase(Locale.ROOT),
    "model" to models.spec.id,
    "model_sha256" to models.spec.model.sha256,
    "double_check" to preferences.doubleCheck,
    "settings" to mapOf(
      "hint_delay_s" to preferences.hintDelaySeconds,
      "words_per_hint" to preferences.wordsPerHint,
      "corrector" to preferences.correctorEnabled,
      "hint_on_mistakes" to preferences.hintOnMistakes,
      "hint_on_pauses" to preferences.hintOnPauses,
      "strict_tashkeel" to preferences.strictTashkeel,
      "recite_mark_mistakes" to preferences.reciteMarkMistakes,
      "recite_say_fix" to preferences.reciteSayFix,
      "hint_voice" to preferences.promptQariId,
    ),
  )

  /** Tells the recording (if any) what happened to the word at [index], in the session's audio time. */
  private fun recordWord(passage: Passage, index: Int, startMs: Long?, endMs: Long?, status: Recording.WordStatus) {
    val recorder = recorder ?: return
    val word = passage.words.getOrNull(index) ?: return
    // the passage always holds whole suras: its last word ends one too
    val endsSura = passage.words.getOrNull(index + 1)?.id?.sura != word.id.sura
    recorder.word(
      word.id, startMs, endsSura = endsSura, endMs = endMs, status = status, phonemes = word.wasl,
      mistakes = mistakeCounts[index] ?: 0,
    )
  }

  // ------------------------------------------------------------------------------------ state

  private fun update(transform: (MemorizeState) -> MemorizeState) {
    while (true) {
      val current = _state.value ?: return
      val next = transform(current)
      if (_state.compareAndSet(current, next)) {
        if (next.currentAyah != current.currentAyah) {
          scope.launch { recitationEventPresenter.onRecitationChange(next.currentAyah) }
        }
        return
      }
    }
  }

  private fun publishSession() {
    recitationEventPresenter.onRecitationSessionChange(Session())
  }

  private fun now(): Long = clock()

  /** One id per ayah, for the tracker. */
  private fun ayahId(word: WordId): Int = word.sura * 1000 + word.ayah

  /**
   * Outside salah no ayah may be left out: the tracker holds at the first word not recited. Inside an
   * ayah, a word or two the recognizer missed may pass, unless every word must be heard.
   */
  private fun trackerConfig(): TrackerConfig =
    TrackerConfig(skipAyat = false, maxMissedWords = if (preferences.everyWordHeard) 0 else MISSED_WORDS_IN_AYAH)

  /** Diagnostics, built only when they're on; worker only. */
  private inline fun trace(message: () -> String) {
    traceTo?.let { it("t=%.1fs %s".format(Locale.ROOT, heardMs / 1000.0, message())) }
  }

  /** Exposes the session to the rest of the app (audio bar, page following). */
  private inner class Session : RecitationSession {
    private val snapshot = _state.value
    override fun id(): String = snapshot?.id.orEmpty()
    override fun startAyah(): SuraAyah = snapshot?.passage?.start ?: SuraAyah(1, 1)
    override fun currentAyah(): SuraAyah = _state.value?.currentAyah ?: startAyah()
    override fun startedAt(): Long = snapshot?.startedAt ?: 0L
    override fun recitation(): Recitation = object : Recitation {
      override fun recitedAyahs(): List<RecitedAyah> = emptyList()
    }
  }

  companion object {
    /** A controller on the test's scope, dispatcher and virtual clock. */
    internal fun forTest(
      lexicon: LexiconRepository,
      models: SpeechModelStore,
      preferences: MemorizePreferences,
      recitationEventPresenter: RecitationEventPresenter,
      engineFactory: RecitationEngineFactory,
      promptPlayer: HintVoice,
      audioSources: AudioSourceFactory,
      callState: CallState,
      scope: CoroutineScope,
      worker: CoroutineDispatcher,
      clock: () -> Long,
      lines: LineSource = LineSource { emptyMap() },
      upcoming: UpcomingSuras = UpcomingSuras {},
      recorders: Recorders = Recorders { _, _, _, _ -> null },
      lookaheadWords: Int = LOOKAHEAD_WORDS,
      trace: ((String) -> Unit)? = null,
      doubleChecks: DoubleCheckSource = DoubleCheckSource { null },
      results: SessionResults = SessionResults {},
    ) = MemorizeSessionController(
      lexicon, models, preferences, recitationEventPresenter, engineFactory, promptPlayer, audioSources, callState,
      lines, upcoming, recorders, doubleChecks, results, scope, worker, worker, clock, lookaheadWords, { trace },
    )

    private const val TRACE_TAG = "MemorizeTrace"
    /** What the double-check listens to: the last seconds, and a second more after a mistake. */
    private const val CHECK_SECONDS = 8
    private const val CHECK_MISTAKE_AFTER_MS = 1_000L
    private const val TRACE_STATUS_MS = 10_000L
    private const val BASMALA = "basmala"
    private const val SAMPLE_RATE = 16_000
    /** Words kept loaded ahead of the user: several pages, so the next sura is always there. */
    private const val LOOKAHEAD_WORDS = 300
    /** Words in a row the recognizer may miss inside an ayah, when not every word must be heard. */
    private const val MISSED_WORDS_IN_AYAH = 2
    /** At most this many words of an ayah are given at once when its lines aren't known. */
    private const val UNKNOWN_LINE_WORDS = 8

    private const val TICK_MS = 100L
    private const val PROMPT_TAIL_MS = 300L
    /** Silence after which the user is considered to have paused (completes a phrase like takbir). */
    private const val PAUSE_MS = 800L
    /** Time without anything new recognized after which the user is considered to have paused. */
    private const val RECOGNITION_GAP_MS = 1_500L
    /** How often (in ticks) to check for a phone call. */
    private const val CALL_CHECK_TICKS = 5
    // 30 seconds of audio: if inference ever falls this far behind, drop the oldest frames
    private const val MAX_QUEUED_FRAMES = 300
  }
}
