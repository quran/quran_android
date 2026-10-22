package com.quran.mobile.feature.memorize.session

import android.os.SystemClock
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.audio.AudioSource
import com.quran.mobile.feature.memorize.audio.AudioSourceFactory
import com.quran.mobile.feature.memorize.audio.CallState
import com.quran.mobile.feature.memorize.data.LexiconRepository
import com.quran.mobile.feature.memorize.data.LineSource
import com.quran.mobile.feature.memorize.data.PageLines
import com.quran.mobile.feature.memorize.data.Passage
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import com.quran.mobile.feature.memorize.prompt.HintVoice
import com.quran.mobile.feature.memorize.prompt.UpcomingSuras
import com.quran.mobile.feature.memorize.recording.Recorders
import com.quran.mobile.feature.memorize.recording.Recording
import com.quran.mobile.feature.memorize.recording.SessionRecorder
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
 * A session doesn't end with a sura: the user may go on to the next one (e.g. in taraweeh), so the
 * next suras are loaded (and hidden) before the user gets to them, up to the end of the Quran.
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
  private val lines: LineSource,
  private val upcoming: UpcomingSuras,
  private val recorders: Recorders,
  private val scope: CoroutineScope,
  /** Runs all recognition and tracking, one task at a time. */
  private val worker: CoroutineDispatcher,
  private val clock: () -> Long,
  /** How many words to keep loaded ahead of the user. */
  private val lookaheadWords: Int,
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
  ) : this(
    lexicon, models, preferences, recitationEventPresenter, engineFactory, promptPlayer, audioSources, callState,
    lines, upcoming, recorders,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    worker = Dispatchers.Default.limitedParallelism(1),
    clock = SystemClock::elapsedRealtime,
    lookaheadWords = LOOKAHEAD_WORDS,
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
  // where each loaded word is on the pages (see PageLines), for hints of the rest of a line
  private val wordLines = HashMap<Int, IntArray>()
  // the session whose next suras are being loaded, if any
  private var extendingFor: String? = null
  // the basmala before a sura, once the passage goes beyond its first sura (see prepare)
  private var basmala: List<Pair<String, String>> = emptyList()

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

  /** Starts a session at [startAyah]; with [record], the user's recitation is recorded too. */
  fun start(startAyah: SuraAyah, mode: MemorizeState.Mode = MemorizeState.Mode.MEMORIZE, record: Boolean = false) {
    if (isActive || startJob?.isActive == true) return
    val sessionId = UUID.randomUUID().toString()
    startJob = scope.launch {
      try {
        prepare(sessionId, startAyah, mode, record)
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
  private suspend fun prepare(sessionId: String, startAyah: SuraAyah, mode: MemorizeState.Mode, record: Boolean): Boolean {
    val passage = lexicon.passage(startAyah, minWords = lookaheadWords)
    require(passage.words.isNotEmpty()) { "nothing to recite from $startAyah" }
    val recorder = if (record) recorders.start(startAyah, mode) else null
    this.recorder = recorder
    _state.value = MemorizeState(
      sessionId, passage, System.currentTimeMillis(), MemorizeState.Status.PREPARING, mode, recordingId = recorder?.session,
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
    val basmalaFirst = salah || (startAyah.sura != 1 && startAyah.sura != 9)
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
      extendingFor = null
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
      heardMs = 0L
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
    finishRecording()
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
    // everything the microphone heard, hints included
    recorder?.write(frame.samples)
    heardMs += frame.samples.size * 1000L / SAMPLE_RATE
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
        is TrackerEvent.WordRecited -> recordWord(passage, event.index, event.startMs ?: event.timeMs, event.timeMs, Recording.WordStatus.RECITED)
        is TrackerEvent.WordMissed -> {
          missed += event.index
          recordWord(passage, event.index, null, null, Recording.WordStatus.MISSED)
        }
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
        follower.extend(more.words.map { PhonemeNormalizer.normalize(it.wasl, keepShortVowels) })
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
      when (effect) {
        is HintScheduler.Effect.MarkMistake -> update { state ->
          state.copy(mistake = state.passage.words.indexOfFirst { it.id == effect.word }.takeIf { it >= 0 })
        }
        is HintScheduler.Effect.ClearMistake -> update { it.copy(mistake = null) }
        is HintScheduler.Effect.GiveHint -> giveHint(effect.count, effect.extent)
        HintScheduler.Effect.GiveUp -> gaveUp = true
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
      finishRecording()
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

  private fun finishRecording() {
    val recorder = recorder ?: return
    this.recorder = null
    recorder.finish()
  }

  /** Tells the recording (if any) what happened to the word at [index], in the session's audio time. */
  private fun recordWord(passage: Passage, index: Int, startMs: Long?, endMs: Long?, status: Recording.WordStatus) {
    val recorder = recorder ?: return
    val word = passage.words.getOrNull(index) ?: return
    // the passage always holds whole suras: its last word ends one too
    val endsSura = passage.words.getOrNull(index + 1)?.id?.sura != word.id.sura
    recorder.word(word.id, startMs, endsSura = endsSura, endMs = endMs, status = status, phonemes = word.wasl)
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
      lines: LineSource = LineSource { emptyMap() },
      upcoming: UpcomingSuras = UpcomingSuras {},
      recorders: Recorders = Recorders { _, _ -> null },
      lookaheadWords: Int = LOOKAHEAD_WORDS,
    ) = MemorizeSessionController(
      lexicon, models, preferences, recitationEventPresenter, engineFactory, promptPlayer, audioSources, callState,
      lines, upcoming, recorders, scope, worker, clock, lookaheadWords,
    )

    private const val BASMALA = "basmala"
    private const val SAMPLE_RATE = 16_000
    /** Words kept loaded ahead of the user: several pages, so the next sura is always there. */
    private const val LOOKAHEAD_WORDS = 300
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
