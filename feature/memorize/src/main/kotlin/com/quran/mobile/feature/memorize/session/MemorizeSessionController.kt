package com.quran.mobile.feature.memorize.session

import android.os.SystemClock
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.audio.AudioSource
import com.quran.mobile.feature.memorize.audio.AudioSourceFactory
import com.quran.mobile.feature.memorize.audio.CallState
import com.quran.mobile.feature.memorize.data.LexiconRepository
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import com.quran.mobile.feature.memorize.prompt.HintVoice
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import com.quran.mobile.feature.memorize.tracker.PhonemeNormalizer
import com.quran.mobile.feature.memorize.tracker.RecitationTracker
import com.quran.mobile.feature.memorize.tracker.TimedPhoneme
import com.quran.mobile.feature.memorize.tracker.TrackerEvent
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
 * All recognition and tracking happens on one serialized worker so audio, timer ticks and hints
 * never race. Nothing is recorded or logged: audio frames are dropped as soon as they are decoded.
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
  private val scope: CoroutineScope,
  /** Runs all recognition and tracking, one task at a time. */
  private val worker: CoroutineDispatcher,
  private val clock: () -> Long,
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
  ) : this(
    lexicon, models, preferences, recitationEventPresenter, engineFactory, promptPlayer, audioSources, callState,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    worker = Dispatchers.Default.limitedParallelism(1),
    clock = SystemClock::elapsedRealtime,
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

  // the following are only touched on the main thread
  private var startJob: Job? = null
  private var mic: AudioSource? = null
  private var frames: Channel<Frame>? = null
  private var jobs: List<Job> = emptyList()
  // completed once the session's engine and follower can take frames
  private var ready = CompletableDeferred<Unit>()
  // while a hint is being played the microphone hears the reciter, not the user
  @Volatile private var prompting = false
  // bumped by every hint, by stopping and by a call: a hint only plays (and only ends the wait for
  // it) while its number is still the current one
  private val hintGeneration = AtomicInteger()

  private class Frame(val samples: FloatArray, val speech: Boolean)

  val isActive: Boolean get() = _state.value?.isActive == true

  fun start(startAyah: SuraAyah, mode: MemorizeState.Mode = MemorizeState.Mode.MEMORIZE) {
    if (isActive || startJob?.isActive == true) return
    val sessionId = UUID.randomUUID().toString()
    startJob = scope.launch {
      try {
        prepare(sessionId, startAyah, mode)
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
  private suspend fun prepare(sessionId: String, startAyah: SuraAyah, mode: MemorizeState.Mode): Boolean {
    val passage = lexicon.passage(startAyah)
    require(passage.words.isNotEmpty()) { "nothing to recite from $startAyah" }
    _state.value = MemorizeState(sessionId, passage, System.currentTimeMillis(), MemorizeState.Status.PREPARING, mode)
    publishSession()
    // Listen right away: loading the model takes seconds on slow phones, and what the user says
    // meanwhile waits in the frame queue until the session is ready to take it.
    val ready = CompletableDeferred<Unit>().also { this.ready = it }
    startListening()

    val salah = mode == MemorizeState.Mode.SALAH
    val kinds = if (salah) SalahFlow.PRAYER_PHRASES else setOf("isti'adha", "basmala")
    val phraseList = lexicon.phrases(kinds)
      .filter { (kind, _) -> kind != "basmala" || (startAyah.sura != 1 && startAyah.sura != 9) || salah }
    val fatihaWords = if (salah) lexicon.passage(SuraAyah(1, 2)).words else emptyList()

    return withContext(worker) {
      // stop() cleans up on this worker too: if it already ran, don't load anything it won't free
      if (_state.value?.id != sessionId) return@withContext false
      keepShortVowels = preferences.strictTashkeel
      val phrases = phraseList.map { (kind, phonemes) -> kind to PhonemeNormalizer.normalize(phonemes, keepShortVowels) }
      val words = passage.words.map { PhonemeNormalizer.normalize(it.wasl, keepShortVowels) }
      val fatiha = fatihaWords.map { PhonemeNormalizer.normalize(it.wasl, keepShortVowels) }
      val engine = engine ?: engineFactory.create(models).also { engine = it }
      stream = engine.newStream()
      follower = if (salah) {
        SalahFollower(SalahFlow(words, fatiha, phrases))
      } else {
        MemorizeFollower(RecitationTracker(words, phrases = phrases))
      }
      scheduler = HintScheduler(preferences.hintConfig(salah = salah))
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
      giveHint(preferences.wordsPerHint)
    }
  }

  fun stop() {
    startJob?.cancel()
    startJob = null
    hintGeneration.incrementAndGet()
    promptPlayer.stop()
    prompting = false
    stopListening()
    scope.launch(worker) {
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
          scheduler?.onTick(now())?.let { applyEffects(it) }
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
    if (prompting) {
      // keep the stream's timeline continuous, but don't let the hint be recognized as the user
      stream.accept(FloatArray(frame.samples.size), speech = false)
      return
    }
    val now = now()
    scheduler.onSpeechActivity(now, frame.speech)
    val tokens = stream.accept(frame.samples, speech = frame.speech)
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
    val events = follower?.onPause().orEmpty()
    if (events.isNotEmpty()) onTrackerEvents(events)
  }

  /** No hints while the phone rings or is in a call; they start over once it ends. */
  private fun checkCall() {
    val calling = callState.isInCall()
    if (calling == inCall) return
    inCall = calling
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
      when (event) {
        is TrackerEvent.WordRecited -> Unit
        is TrackerEvent.WordMissed -> missed += event.index
        is TrackerEvent.Repetition -> scheduler.onRepetition(now())
        is TrackerEvent.Mistake -> effects += scheduler.onMistake(now(), passage.words[event.index].id)
        is TrackerEvent.NonTarget -> Unit
      }
    }
    val progressed = events.any { it is TrackerEvent.WordRecited || it is TrackerEvent.WordMissed }
    if (progressed) {
      effects += scheduler.onWordRecited(now(), atAyahBoundary = passage.atAyahBoundary(follower.cursor))
    }
    val cursor = follower.cursor
    update { it.copy(cursor = cursor, missed = it.missed + missed) }
    applyEffects(effects)
    disarmIfNotAllowed()
    if (follower.isDone) finish()
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
      when (effect) {
        is HintScheduler.Effect.MarkMistake -> update { state ->
          state.copy(mistake = state.passage.words.indexOfFirst { it.id == effect.word }.takeIf { it >= 0 })
        }
        is HintScheduler.Effect.ClearMistake -> update { it.copy(mistake = null) }
        is HintScheduler.Effect.GiveHint -> giveHint(effect.count)
        HintScheduler.Effect.GiveUp -> gaveUp = true
      }
    }
  }

  private fun giveHint(count: Int) {
    val follower = follower ?: return
    val scheduler = scheduler ?: return
    val passage = _state.value?.passage ?: return
    val first = follower.cursor
    follower.markHinted(count)
    val hinted = (first until follower.cursor).toList()
    if (hinted.isEmpty()) return
    scheduler.onHintStarted()
    update { it.copy(cursor = follower.cursor, hinted = it.hinted + hinted, mistake = null) }
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

  /** On the main thread, once hint number [generation] has been played (or had nothing to play). */
  private fun hintFinished(generation: Int) {
    scope.launch {
      // let the room go quiet before listening to the user again
      if (prompting) delay(PROMPT_TAIL_MS)
      if (generation != hintGeneration.get()) return@launch
      prompting = false
      withContext(worker) {
        scheduler?.onHintFinished(now())
        if (follower?.isDone == true) finish()
      }
    }
  }

  private fun finish() {
    scope.launch {
      stopListening()
      update { it.copy(status = MemorizeState.Status.FINISHED, mistake = null) }
      recitationEventPresenter.onListeningStateChange(false)
      // the passage is done; the user may keep reading, but the model's memory isn't needed
      withContext(worker) { releaseEngine() }
    }
  }

  /** Worker only. The model takes a lot of memory; it is loaded again for the next session. */
  private fun releaseEngine() {
    runCatching { stream?.close() }
    runCatching { engine?.close() }
    engine = null
    stream = null
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
    ) = MemorizeSessionController(
      lexicon, models, preferences, recitationEventPresenter, engineFactory, promptPlayer, audioSources, callState,
      scope, worker, clock,
    )

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
