package com.quran.labs.androidquran.kidsclassroom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.memorization.QuranMemorizationManager
import com.quran.labs.androidquran.memorization.tasmee.VadWaveformMath
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Central state manager for **Children Learning Mode (Tilawi Kids Quran Classroom)** across
 * all 114 Surahs (`1..114`, `6,236` verses), powering both the **Kids Tab** (`KidsClassroomFragment`)
 * and the **Tajweed Mushaf HUD** (`PagerActivity` + `HighlightingImageView`).
 *
 * Supports two sub-modes via a top Segmented Toggle:
 * - **Mode A (`LISTEN_AND_REPEAT` — "Teacher & Kids Repeat" / `المعلم وترديد الأطفال`)**:
 *   Stage 1 (Sheikh) -> Stage 2 (Audio Kids) -> Stage 3 (Child's Turn + Two-Tier ONNX Grader),
 *   auto-advancing after `1,200 ms` when `passed == true`.
 * - **Mode B (`RECITE_AND_REVEAL` — "Recite & Reveal / Tasmee'" / `التسميع الحي`)**:
 *   `ExoPlayer` off, blank-to-revealed RTL `FlowRow` (`alpha = 0f` -> `1f`), `350 ms` hops over
 *   the latest `4.5s` rolling `AudioRecord` ring buffer, monotonic word reveal via Rasm/prefix,
 *   `+1` lookahead, or trailing-35% BPE peak posterior `>= 0.58f`, auto-advancing to
 *   `next_surah:next_ayah` when `revealedCount == uthmani_words.size`.
 */
object KidsClassroomManager {

  fun interface Listener {
    fun onKidsClassroomStateChanged(state: KidsClassroomUiState)
  }

  private const val PREFS_NAME = "tilawi_kids_classroom_prefs"
  private const val KEY_AUTO_NEXT = "auto_next_enabled"
  private const val KEY_DOCKED_TOP = "is_docked_top"
  private const val KEY_LAST_SURAH = "last_juz30_surah"
  private const val KEY_LAST_AYAH = "last_juz30_ayah"
  private const val KEY_PRACTICE_MODE = "practice_mode_name"
  private const val KEY_USE_AUDIO_CLUES_MODE = "use_audio_clues_mode"
  private const val KEY_AYAH_REPEAT_TARGET = "ayah_repeat_target"
  private const val KEY_SURAH_REPEAT_TARGET = "surah_repeat_target"
  private const val KEY_DIFFICULTY_LEVEL = "difficulty_level"
  private const val KEY_REPEAT_FROM_MISTAKE_ONLY = "repeat_from_first_mistake_only"
  private const val KEY_TEACHER_QARI_ID = "selected_teacher_qari_id"
  internal const val MAX_AYAH_REPEAT_TARGET = 50
  internal const val MAX_SURAH_REPEAT_TARGET = 20
  private const val AUTO_NEXT_DELAY_MS = 1200L
  private const val AUTO_RETRY_DELAY_MS = 1550L
  private const val TASMEE_AYAH_ADVANCE_BEAT_MS = 280L
  internal const val KIDS_MIN_SPEECH_PEAK_RMS = 0.022f

  private sealed interface PostCelebrationAction {
    data object None : PostCelebrationAction
    data class RepeatCurrentAyah(val suraAyah: SuraAyah, val nextAyahRepeatCompleted: Int) : PostCelebrationAction
    data class RepeatCurrentSurah(val firstAyahOfSurah: SuraAyah, val nextSurahRepeatCompleted: Int) : PostCelebrationAction
    data class AdvanceNextAyah(val nextAyah: SuraAyah, val keepSurahRepeatCompleted: Int) : PostCelebrationAction
  }

  private val coroutineErrorHandler = CoroutineExceptionHandler { _, throwable ->
    Timber.e(throwable, "Unhandled coroutine error in KidsClassroomManager")
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + coroutineErrorHandler)
  private val listeners = CopyOnWriteArraySet<Listener>()

  private val _uiState = MutableStateFlow(KidsClassroomUiState())
  val uiState: StateFlow<KidsClassroomUiState> = _uiState.asStateFlow()

  private var audioController: KidsClassroomAudioController? = null
  private val streamingAsrEngine = StreamingQuranAsrEngine()
  private val offlineBatchAsrEngine = OfflineBatchQuranAsrEngine()
  private var autoNextJob: Job? = null
  private var earlyCompleteJob: Job? = null
  private var downloadJob: Job? = null
  private val ayahTurnScores = mutableListOf<Double>()
  private val surahAyahScores = LinkedHashMap<Int, Double>()
  private var activeRetryTailTurn: TurnSpec? = null
  private var celebrationEventSeq: Long = 0L
  private var lastCelebrationThemeIdx: Int = -1
  private var pendingPostCelebrationAction: PostCelebrationAction = PostCelebrationAction.None
  @Volatile
  private var checkedLegacyHashThisSession: Boolean = false

  @Volatile
  var onNavigateToAyahPage: ((SuraAyah) -> Unit)? = null

  @Volatile
  var onRequestMicPermission: (() -> Unit)? = null

  internal fun pickRandomCelebrationTheme(): KidsCelebrationTheme {
    val themes = KidsCelebrationTheme.entries
    if (themes.size <= 1) return themes.first()
    var idx = kotlin.random.Random.nextInt(themes.size)
    if (idx == lastCelebrationThemeIdx) {
      idx = (idx + 1 + kotlin.random.Random.nextInt(themes.size - 1)) % themes.size
    }
    lastCelebrationThemeIdx = idx
    return themes[idx]
  }

  fun setAyahRepeatTarget(context: Context, count: Int) {
    val clamped = count.coerceIn(1, MAX_AYAH_REPEAT_TARGET)
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putInt(KEY_AYAH_REPEAT_TARGET, clamped)
      .apply()
    updateState { prev ->
      prev.copy(
        ayahRepeatTarget = clamped,
        ayahRepeatCompleted = prev.ayahRepeatCompleted.coerceAtMost((clamped - 1).coerceAtLeast(0))
      )
    }
  }

  fun setSurahRepeatTarget(context: Context, count: Int) {
    val clamped = count.coerceIn(1, MAX_SURAH_REPEAT_TARGET)
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putInt(KEY_SURAH_REPEAT_TARGET, clamped)
      .apply()
    updateState { prev ->
      prev.copy(
        surahRepeatTarget = clamped,
        surahRepeatCompleted = prev.surahRepeatCompleted.coerceAtMost((clamped - 1).coerceAtLeast(0))
      )
    }
  }

  fun setDifficultyLevel(context: Context, level: KidsDifficultyLevel) {
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_DIFFICULTY_LEVEL, level.name)
      .apply()
    updateState { it.copy(difficultyLevel = level) }
  }

  fun setRepeatFromFirstMistakeOnly(context: Context, enabled: Boolean) {
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_REPEAT_FROM_MISTAKE_ONLY, enabled)
      .apply()
    if (!enabled) {
      activeRetryTailTurn = null
    }
    updateState {
      it.copy(
        repeatFromFirstMistakeOnly = enabled,
        activeMistakeTailStartWordIndex = if (enabled) it.activeMistakeTailStartWordIndex else 0
      )
    }
  }

  /**
   * Switches the active AI Teacher reciter ([KidsTeacherQari.HUSARY_MUALLIM] default or
   * [KidsTeacherQari.MINSHAWI_TEACHER]), persists the choice, reloads the active dataset,
   * and downloads the full 114-Surah JSON for that reciter in the background if not yet cached.
   */
  fun setSelectedTeacherQari(context: Context, qari: KidsTeacherQari) {
    val appCtx = context.applicationContext
    appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_TEACHER_QARI_ID, qari.qariId)
      .apply()

    autoNextJob?.cancel()
    earlyCompleteJob?.cancel()
    audioController?.stopPlaybackAndMicGate()
    activeRetryTailTurn = null
    pendingPostCelebrationAction = PostCelebrationAction.None

    MinshawiJuz30Repository.setActiveTeacherQari(appCtx, qari)
    val curAyah = _uiState.value.currentAyah
    val verseSpec = MinshawiJuz30Repository.getVerse(appCtx, curAyah)
      ?: MinshawiJuz30Repository.getVerse(appCtx, 78, 1)
    val firstTurn = verseSpec?.turns?.firstOrNull()

    updateState { prev ->
      prev.copy(
        selectedTeacherQari = qari,
        stage = if (prev.stage == ClassroomStage.DOWNLOADING_MODELS) prev.stage else ClassroomStage.IDLE,
        isAutoTransitionPending = false,
        isRetryAttempt = false,
        currentAyah = verseSpec?.suraAyah ?: prev.currentAyah,
        surahNameAr = verseSpec?.surahNameAr ?: prev.surahNameAr,
        currentTurnIndex = 0,
        totalTurns = verseSpec?.totalTurns ?: 1,
        currentTurn = firstTurn,
        ayahWords = verseSpec?.allUthmaniWords ?: prev.ayahWords,
        isFullQuranLoaded = MinshawiJuz30Repository.isFullDatasetLoaded(),
        childMaxTimeoutSec = firstTurn?.computeChildMaxTimeoutSec() ?: 12.0f,
        activePlaybackWordPosition = -1,
        activeMistakeTailStartWordIndex = 0,
        wordGradesByMushafPos = buildInitialTurnWordGrades(firstTurn),
        statusMessageOverride = null
      )
    }

    if (!AsrModelManager.isTeacherFullJsonReady(appCtx, qari)) {
      scope.launch {
        val ok = AsrModelManager.ensureTeacherFullJsonDownloaded(appCtx, qari) { progress ->
          scope.launch {
            updateState { st -> st.copy(downloadProgress = progress) }
          }
        }
        if (ok && MinshawiJuz30Repository.getActiveTeacherQari() == qari) {
          val refreshed = MinshawiJuz30Repository.getVerse(appCtx, _uiState.value.currentAyah)
          val refreshedTurn = refreshed?.turns?.firstOrNull()
          updateState { st ->
            st.copy(
              surahNameAr = refreshed?.surahNameAr ?: st.surahNameAr,
              totalTurns = refreshed?.totalTurns ?: st.totalTurns,
              currentTurn = refreshedTurn ?: st.currentTurn,
              ayahWords = refreshed?.allUthmaniWords ?: st.ayahWords,
              isFullQuranLoaded = MinshawiJuz30Repository.isFullDatasetLoaded()
            )
          }
        }
      }
    }
  }

  internal fun activeRetryTailTurnForTest(): TurnSpec? = activeRetryTailTurn

  /**
   * Builds a Sheikh-only tail [TurnSpec] starting from the word **immediately before**
   * [firstMistakeIndexInTurn] (when [includePreviousWord] is `true`) through the end of [baseTurn].
   * For example, in a 5-word Ayah where the learner's first mistake is on word 3 (index `2`),
   * this returns a 4-word tail turn covering words 2, 3, 4, and 5 so the learner hears natural
   * Quranic transition into the missed word at `0.75×` slow-mo with isolated word clarity.
   */
  internal fun buildMistakeTailTurn(
    baseTurn: TurnSpec,
    firstMistakeIndexInTurn: Int,
    vocab: Array<String>? = null,
    includePreviousWord: Boolean = true
  ): TurnSpec {
    val totalWords = baseTurn.uthmaniWords.size
    if (totalWords <= 1) return baseTurn.copy(contiguousPlayback = false)
    val rawIdx = if (includePreviousWord) firstMistakeIndexInTurn - 1 else firstMistakeIndexInTurn
    val startIdx = rawIdx.coerceIn(0, totalWords - 1)
    if (startIdx == 0) return baseTurn.copy(contiguousPlayback = false)

    val weights = baseTurn.uthmaniWords.map { phoneticWeightForWord(it) }
    val totalWeight = weights.sum().coerceAtLeast(1.0)
    val prefixWeight = weights.take(startIdx).sum()
    val startFraction = (prefixWeight / totalWeight).coerceIn(0.0, 0.92)

    val qariSpanSec = (baseTurn.qariEndSec - baseTurn.qariStartSec).coerceAtLeast(0.4)
    val rawTailStartSec = baseTurn.qariStartSec + qariSpanSec * startFraction
    // Small 90 ms lead-in ensures the onset consonant of the lead-in word is never clipped
    val leadInTailStartSec = (rawTailStartSec - 0.09).coerceIn(
      baseTurn.qariStartSec,
      (baseTurn.qariEndSec - 0.35).coerceAtLeast(baseTurn.qariStartSec)
    )

    val tailUthmani = baseTurn.uthmaniWords.subList(startIdx, totalWords)
    val tailNormalized = if (baseTurn.normalizedWords.size == totalWords) {
      baseTurn.normalizedWords.subList(startIdx, totalWords)
    } else {
      tailUthmani
    }
    val tailQariOnnx = if (baseTurn.qariOnnxWords.size == totalWords) {
      baseTurn.qariOnnxWords.subList(startIdx, totalWords)
    } else {
      tailNormalized
    }
    val tailTokenIds = sliceTokenIdsFromWordIndex(
      tokenIds = baseTurn.tokenIds,
      totalWords = totalWords,
      startWordIndex = startIdx,
      vocab = vocab
    )

    return baseTurn.copy(
      wordStartIdx = baseTurn.wordStartIdx + startIdx,
      wordEndIdx = baseTurn.wordStartIdx + totalWords,
      qariStartSec = leadInTailStartSec,
      qariEndSec = baseTurn.qariEndSec,
      contiguousPlayback = false,
      uthmaniWords = tailUthmani,
      normalizedWords = tailNormalized,
      qariOnnxWords = tailQariOnnx,
      tokenIds = tailTokenIds
    )
  }

  private fun sliceTokenIdsFromWordIndex(
    tokenIds: List<Int>,
    totalWords: Int,
    startWordIndex: Int,
    vocab: Array<String>?
  ): List<Int> {
    if (tokenIds.isEmpty() || startWordIndex <= 0) return tokenIds
    if (vocab != null && vocab.isNotEmpty()) {
      val groups = mutableListOf<MutableList<Int>>()
      for (tid in tokenIds) {
        val piece = if (tid in vocab.indices) vocab[tid] else ""
        if (piece.startsWith("\u2581") || groups.isEmpty()) {
          groups.add(mutableListOf(tid))
        } else {
          groups.last().add(tid)
        }
      }
      if (groups.size == totalWords && startWordIndex < groups.size) {
        return groups.subList(startWordIndex, groups.size).flatten()
      }
    }
    val approxIdx = ((startWordIndex.toDouble() / totalWords.coerceAtLeast(1)) * tokenIds.size)
      .toInt()
      .coerceIn(0, (tokenIds.size - 1).coerceAtLeast(0))
    return tokenIds.subList(approxIdx, tokenIds.size)
  }

  internal fun phoneticWeightForWord(word: String): Double {
    var weight = 1.4
    for (ch in word) {
      when {
        ch in '\u0621'..'\u064A' -> {
          weight += if (ch == 'ا' || ch == 'و' || ch == 'ي' || ch == 'ى') 1.35 else 1.0
        }
        ch == '\u0651' -> weight += 0.9 // Shaddah holds longer
        ch == '\u0653' || ch == '\u0670' -> weight += 1.6 // Maddah / dagger alif
      }
    }
    return weight
  }

  fun latestPlaybackPositionMs(): Long = audioController?.latestPlaybackPositionMs() ?: 0L

  fun latestAudioRms(): Float = audioController?.latestAudioRms() ?: 0f

  fun latestAcousticFrame(): AudioAcousticFrame =
    audioController?.latestAcousticFrame() ?: AudioAcousticFrame()

  fun isPlayingSpeakerAudio(): Boolean = audioController?.isPlayingSpeakerAudio() == true

  fun setUseAudioCluesMode(context: Context, enabled: Boolean) {
    val appCtx = context.applicationContext
    appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_USE_AUDIO_CLUES_MODE, enabled)
      .apply()
    updateState { it.copy(useAudioCluesMode = enabled) }
  }

  fun setSlowMoEnabled(context: Context, enabled: Boolean) {
    val speed = if (enabled) 0.75f else 1.0f
    getOrCreateAudioController(context).setPlaybackSpeed(speed)
    updateState { it.copy(isSlowMoEnabled = enabled) }
  }

  fun replayTimeWindowInSlowMo(
    context: Context,
    startMs: Long,
    endMs: Long,
    onCompleted: () -> Unit = {}
  ) {
    val appCtx = context.applicationContext
    val verse = MinshawiJuz30Repository.getVerse(appCtx, _uiState.value.currentAyah) ?: return
    getOrCreateAudioController(appCtx).playFocusedSegment(
      audioUrl = verse.audioUrl,
      startMs = startMs,
      endMs = endMs,
      speed = 0.75f,
      onCompleted = onCompleted,
      onPlaybackError = { onCompleted() }
    )
  }

  /**
   * Replays a specific difficult word in Sheikh Al-Minshawi's recitation at `0.75×` Slow-Mo
   * (used by the Per-Surah Revision Analysis sheet).
   */
  fun replayWordInSlowMo(
    context: Context,
    suraAyah: SuraAyah,
    mushafWordPosition: Int,
    onCompleted: () -> Unit = {}
  ) {
    val appCtx = context.applicationContext
    MinshawiJuz30Repository.ensureLoaded(appCtx)
    val verse = MinshawiJuz30Repository.getVerse(appCtx, suraAyah) ?: run {
      onCompleted()
      return
    }
    val zeroBasedWord = (mushafWordPosition - 1).coerceAtLeast(0)
    val turn = verse.turns.firstOrNull { t ->
      zeroBasedWord >= t.wordStartIdx && zeroBasedWord < t.wordStartIdx + t.uthmaniWords.size
    } ?: verse.turns.firstOrNull() ?: run {
      onCompleted()
      return
    }

    val idxInTurn = (zeroBasedWord - turn.wordStartIdx).coerceIn(0, (turn.uthmaniWords.size - 1).coerceAtLeast(0))
    val weights = turn.uthmaniWords.map { phoneticWeightForWord(it) }
    val totalWeight = weights.sum().coerceAtLeast(1.0)
    val beforeWeight = weights.take(idxInTurn).sum()
    val wordWeight = weights.getOrElse(idxInTurn) { 1.0 }
    val qariStartMs = turn.effectiveQariStartMs
    val qariEndMs = turn.effectiveQariEndMs
    val spanMs = (qariEndMs - qariStartMs).coerceAtLeast(400L)

    val estStart = qariStartMs + (spanMs * (beforeWeight / totalWeight)).toLong()
    val estEnd = qariStartMs + (spanMs * ((beforeWeight + wordWeight) / totalWeight)).toLong()
    val clipStart = (estStart - 130L).coerceAtLeast(qariStartMs)
    val clipEnd = maxOf(estEnd + 160L, clipStart + 750L).coerceAtMost(qariEndMs + 150L)

    getOrCreateAudioController(appCtx).playFocusedSegment(
      audioUrl = verse.audioUrl,
      startMs = clipStart,
      endMs = clipEnd,
      speed = 0.75f,
      onCompleted = onCompleted,
      onPlaybackError = { onCompleted() }
    )
  }

  @JvmStatic
  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  @JvmStatic
  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  @JvmStatic
  fun isEnabled(): Boolean = _uiState.value.isEnabled

  @JvmStatic
  fun currentState(): KidsClassroomUiState = _uiState.value

  internal fun isMicrophoneRecordingForTest(): Boolean {
    return audioController?.isMicrophoneRecording() == true
  }

  private fun updateState(transform: (KidsClassroomUiState) -> KidsClassroomUiState) {
    val next = transform(_uiState.value)
    _uiState.value = next
    for (listener in listeners) {
      listener.onKidsClassroomStateChanged(next)
    }
  }

  private fun getOrCreateAudioController(context: Context): KidsClassroomAudioController {
    audioController?.let { return it }
    return KidsClassroomAudioController(context.applicationContext).also {
      audioController = it
    }
  }

  /**
   * Initializes state for the standalone **Kids Tab** (`KidsClassroomFragment`) without
   * enabling the Mushaf reader overlay until the user opens the Mushaf reader.
   */
  fun ensureKidsTabInitialized(context: Context) {
    val appCtx = context.applicationContext
    val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val selectedTeacherQari = KidsTeacherQari.fromIdOrDefault(prefs.getString(KEY_TEACHER_QARI_ID, null))
    MinshawiJuz30Repository.setActiveTeacherQari(appCtx, selectedTeacherQari)

    val autoNext = prefs.getBoolean(KEY_AUTO_NEXT, true)
    val useAudioClues = prefs.getBoolean(KEY_USE_AUDIO_CLUES_MODE, false)
    val ayahRepeatTarget = prefs.getInt(KEY_AYAH_REPEAT_TARGET, 1).coerceIn(1, MAX_AYAH_REPEAT_TARGET)
    val surahRepeatTarget = prefs.getInt(KEY_SURAH_REPEAT_TARGET, 1).coerceIn(1, MAX_SURAH_REPEAT_TARGET)
    val difficultyLevel = KidsDifficultyLevel.fromNameOrDefault(prefs.getString(KEY_DIFFICULTY_LEVEL, null))
    val repeatFromMistakeOnly = prefs.getBoolean(KEY_REPEAT_FROM_MISTAKE_ONLY, false)

    val savedSurah = prefs.getInt(KEY_LAST_SURAH, 78).coerceIn(1, 114)
    val savedAyah = prefs.getInt(KEY_LAST_AYAH, 1).coerceAtLeast(1)
    val current = _uiState.value
    val targetAyah = if (current.ayahWords.isNotEmpty()) {
      current.currentAyah
    } else {
      SuraAyah(savedSurah, savedAyah)
    }

    val verseSpec = MinshawiJuz30Repository.getVerse(appCtx, targetAyah)
      ?: MinshawiJuz30Repository.getVerse(appCtx, 78, 1)

    val firstTurn = verseSpec?.turns?.firstOrNull()
    val allReady = KidsAssetDownloader.areAllAssetsReady(appCtx)
    val coreReady = KidsAssetDownloader.areCoreEngineAssetsReady(appCtx)
    val streamingReady = AsrModelManager.isStreamingAsrReady(appCtx)
    val fullLoaded = MinshawiJuz30Repository.isFullDatasetLoaded()
    RewardsDataStore.initialize(appCtx)
    KidsSurahAnalysisStore.ensureLoaded(appCtx)

    updateState { prev ->
      prev.copy(
        practiceMode = KidsPracticeMode.LISTEN_AND_REPEAT,
        stage = if (coreReady) {
          if (prev.stage == ClassroomStage.DOWNLOADING_MODELS) ClassroomStage.IDLE else prev.stage
        } else {
          ClassroomStage.DOWNLOADING_MODELS
        },
        currentAyah = verseSpec?.suraAyah ?: targetAyah,
        surahNameAr = verseSpec?.surahNameAr ?: MinshawiJuz30Repository.getSurahNameAr(targetAyah.sura),
        currentTurnIndex = prev.currentTurnIndex.coerceIn(0, ((verseSpec?.turns?.size ?: 1) - 1).coerceAtLeast(0)),
        totalTurns = verseSpec?.totalTurns ?: 1,
        currentTurn = verseSpec?.turns?.getOrNull(prev.currentTurnIndex) ?: firstTurn,
        ayahWords = verseSpec?.allUthmaniWords ?: emptyList(),
        isFullQuranLoaded = fullLoaded,
        isStreamingAsrReady = streamingReady,
        childMaxTimeoutSec = (verseSpec?.turns?.getOrNull(prev.currentTurnIndex) ?: firstTurn)
          ?.computeChildMaxTimeoutSec() ?: 12.0f,
        autoNextEnabled = autoNext,
        ayahRepeatTarget = ayahRepeatTarget,
        surahRepeatTarget = surahRepeatTarget,
        difficultyLevel = difficultyLevel,
        repeatFromFirstMistakeOnly = repeatFromMistakeOnly,
        useAudioCluesMode = useAudioClues,
        selectedTeacherQari = selectedTeacherQari,
        downloadProgress = prev.downloadProgress.copy(isReady = allReady || coreReady),
        wordGradesByMushafPos = if (prev.wordGradesByMushafPos.isNotEmpty()) {
          prev.wordGradesByMushafPos
        } else {
          buildInitialTurnWordGrades(firstTurn)
        }
      )
    }

    if (!checkedLegacyHashThisSession) {
      checkedLegacyHashThisSession = true
      scope.launch {
        val invalidatedLegacy = AsrModelManager.checkAndInvalidateLegacyModelsOnStartup(appCtx)
        if (invalidatedLegacy || !AsrModelManager.isFullBundleReady(appCtx)) {
          startModelDownload(appCtx)
        } else {
          FastConformerAsrEngine.ensureInitialized(appCtx)
          streamingAsrEngine.load(appCtx)
          updateState { it.copy(isStreamingAsrReady = streamingAsrEngine.isLoaded) }
        }
      }
    } else if (!allReady) {
      startModelDownload(appCtx)
    } else {
      scope.launch {
        FastConformerAsrEngine.ensureInitialized(appCtx)
        if (AsrModelManager.isStreamingAsrReady(appCtx) && !streamingAsrEngine.isLoaded) {
          streamingAsrEngine.load(appCtx)
          updateState { it.copy(isStreamingAsrReady = streamingAsrEngine.isLoaded) }
        }
      }
    }
  }

  /**
   * Activates Children Learning Mode on the Tajweed Mushaf page across Surahs `1..114`.
   */
  fun enableClassroomMode(
    context: Context,
    currentPage: Int,
    selectedAyah: SuraAyah?,
    quranInfo: QuranInfo,
    enableWordHiding: Boolean = false
  ): SuraAyah {
    val appCtx = context.applicationContext
    val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val selectedTeacherQari = KidsTeacherQari.fromIdOrDefault(prefs.getString(KEY_TEACHER_QARI_ID, null))
    MinshawiJuz30Repository.setActiveTeacherQari(appCtx, selectedTeacherQari)

    val autoNext = prefs.getBoolean(KEY_AUTO_NEXT, true)
    val useAudioClues = prefs.getBoolean(KEY_USE_AUDIO_CLUES_MODE, false)
    val ayahRepeatTarget = prefs.getInt(KEY_AYAH_REPEAT_TARGET, 1).coerceIn(1, MAX_AYAH_REPEAT_TARGET)
    val surahRepeatTarget = prefs.getInt(KEY_SURAH_REPEAT_TARGET, 1).coerceIn(1, MAX_SURAH_REPEAT_TARGET)
    val difficultyLevel = KidsDifficultyLevel.fromNameOrDefault(prefs.getString(KEY_DIFFICULTY_LEVEL, null))
    val repeatFromMistakeOnly = prefs.getBoolean(KEY_REPEAT_FROM_MISTAKE_ONLY, false)
    RewardsDataStore.initialize(appCtx)
    KidsSurahAnalysisStore.ensureLoaded(appCtx)

    if (com.quran.labs.androidquran.memorization.tasmee.LiveTasmeeManager.isEnabled()) {
      com.quran.labs.androidquran.memorization.tasmee.LiveTasmeeManager.stopTasmee()
    }

    if (enableWordHiding && !QuranMemorizationManager.isHiddenModeEnabled()) {
      if (QuranMemorizationManager.getRevealMode().isSingleActiveWordMode) {
        QuranMemorizationManager.setRevealMode(
          com.quran.labs.androidquran.memorization.RevealMode.WORD_BY_WORD,
          appCtx
        )
      }
      QuranMemorizationManager.setHiddenModeEnabled(true, currentPage, quranInfo, appCtx)
    } else if (QuranMemorizationManager.isHiddenModeEnabled() &&
      QuranMemorizationManager.getRevealMode().isSingleActiveWordMode
    ) {
      // Ensure revealed turn words stay visible together on the Mushaf page during Kids Memorization
      QuranMemorizationManager.setRevealMode(
        com.quran.labs.androidquran.memorization.RevealMode.WORD_BY_WORD,
        appCtx
      )
    }

    val initialAyah = resolveInitialAyah(
      context = appCtx,
      currentPage = currentPage,
      selectedAyah = selectedAyah,
      quranInfo = quranInfo
    )

    val verseSpec = MinshawiJuz30Repository.getVerse(appCtx, initialAyah)
      ?: MinshawiJuz30Repository.getVerse(appCtx, 78, 1)

    val firstTurn = verseSpec?.turns?.firstOrNull()
    val coreReady = KidsAsrAssetDownloader.areCoreEngineAssetsReady(appCtx)
    val fullLoaded = MinshawiJuz30Repository.isFullDatasetLoaded()

    ayahTurnScores.clear()
    surahAyahScores.clear()
    activeRetryTailTurn = null
    pendingPostCelebrationAction = PostCelebrationAction.None

    updateState {
      KidsClassroomUiState(
        isEnabled = true,
        practiceMode = KidsPracticeMode.LISTEN_AND_REPEAT,
        stage = if (coreReady) ClassroomStage.IDLE else ClassroomStage.DOWNLOADING_MODELS,
        currentAyah = verseSpec?.suraAyah ?: SuraAyah(78, 1),
        surahNameAr = verseSpec?.surahNameAr ?: MinshawiJuz30Repository.getSurahNameAr(initialAyah.sura),
        currentTurnIndex = 0,
        totalTurns = verseSpec?.totalTurns ?: 1,
        currentTurn = firstTurn,
        ayahWords = verseSpec?.allUthmaniWords ?: emptyList(),
        revealedCount = 0,
        isFullQuranLoaded = fullLoaded,
        childMaxTimeoutSec = firstTurn?.computeChildMaxTimeoutSec() ?: 12.0f,
        activePlaybackWordPosition = -1,
        autoNextEnabled = autoNext,
        ayahRepeatTarget = ayahRepeatTarget,
        ayahRepeatCompleted = 0,
        surahRepeatTarget = surahRepeatTarget,
        surahRepeatCompleted = 0,
        difficultyLevel = difficultyLevel,
        repeatFromFirstMistakeOnly = repeatFromMistakeOnly,
        activeMistakeTailStartWordIndex = 0,
        pendingCelebration = null,
        useAudioCluesMode = useAudioClues,
        selectedTeacherQari = selectedTeacherQari,
        isDockedTop = false,
        isMinimized = false,
        downloadProgress = AssetDownloadProgress(isReady = coreReady),
        lastEvaluation = null,
        wordGradesByMushafPos = buildInitialTurnWordGrades(firstTurn),
        statusMessageOverride = null
      )
    }

    if (coreReady) {
      scope.launch {
        FastConformerAsrEngine.ensureInitialized(appCtx)
        if (AsrModelManager.isStreamingAsrReady(appCtx) && !streamingAsrEngine.isLoaded) {
          kotlinx.coroutines.withContext(Dispatchers.Default) {
            streamingAsrEngine.load(appCtx)
          }
          updateState { it.copy(isStreamingAsrReady = streamingAsrEngine.isLoaded) }
        }
      }
      if (!KidsAssetDownloader.isTeacherFullJsonReady(appCtx, selectedTeacherQari)) {
        startModelDownload(appCtx)
      }
    } else {
      startModelDownload(appCtx)
    }

    return verseSpec?.suraAyah ?: SuraAyah(78, 1)
  }

  fun disableClassroomMode(context: Context? = null) {
    autoNextJob?.cancel()
    earlyCompleteJob?.cancel()
    audioController?.stopPlaybackAndMicGate()
    ayahTurnScores.clear()
    surahAyahScores.clear()
    activeRetryTailTurn = null
    pendingPostCelebrationAction = PostCelebrationAction.None
    updateState {
      it.copy(
        isEnabled = false,
        stage = ClassroomStage.IDLE,
        isAutoTransitionPending = false,
        isRetryAttempt = false,
        activePlaybackWordPosition = -1,
        activeMistakeTailStartWordIndex = 0,
        pendingCelebration = null,
        micLevelRms = 0f,
        voicedSpeechMs = 0L,
        isVadActive = false,
        waveformBars = VadWaveformMath.emptyBars(),
        statusMessageOverride = null
      )
    }
  }

  fun stopActiveLoop() {
    autoNextJob?.cancel()
    earlyCompleteJob?.cancel()
    audioController?.stopPlaybackAndMicGate()
    activeRetryTailTurn = null
    pendingPostCelebrationAction = PostCelebrationAction.None
    updateState {
      it.copy(
        stage = ClassroomStage.IDLE,
        isAutoTransitionPending = false,
        isRetryAttempt = false,
        activePlaybackWordPosition = -1,
        activeMistakeTailStartWordIndex = 0,
        pendingCelebration = null,
        micLevelRms = 0f,
        voicedSpeechMs = 0L,
        isVadActive = false,
        waveformBars = VadWaveformMath.emptyBars(),
        statusMessageOverride = null
      )
    }
  }

  /**
   * Switches between **Mode A** (`LISTEN_AND_REPEAT`: "Teacher & Kids Repeat" / `المعلم وترديد الأطفال`)
   * and **Mode B** (`RECITE_AND_REVEAL`: "Recite & Reveal / Tasmee'" / `التسميع الحي`).
   */
  fun setPracticeMode(
    context: Context,
    mode: KidsPracticeMode,
    autoStartStreamingInModeB: Boolean = false
  ) {
    val appCtx = context.applicationContext
    autoNextJob?.cancel()
    audioController?.stopPlaybackAndMicGate()
    activeRetryTailTurn = null

    appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_PRACTICE_MODE, mode.name)
      .apply()

    val verse = MinshawiJuz30Repository.getVerse(appCtx, _uiState.value.currentAyah)
    val turn = verse?.turns?.firstOrNull()

    updateState {
      it.copy(
        practiceMode = mode,
        stage = if (KidsAssetDownloader.areCoreEngineAssetsReady(appCtx)) {
          ClassroomStage.IDLE
        } else {
          ClassroomStage.DOWNLOADING_MODELS
        },
        isAutoTransitionPending = false,
        isRetryAttempt = false,
        activeMistakeTailStartWordIndex = 0,
        currentTurnIndex = 0,
        totalTurns = verse?.totalTurns ?: 1,
        currentTurn = turn,
        ayahWords = verse?.allUthmaniWords ?: it.ayahWords,
        revealedCount = 0,
        childMaxTimeoutSec = turn?.computeChildMaxTimeoutSec() ?: 12.0f,
        activePlaybackWordPosition = -1,
        micLevelRms = 0f,
        voicedSpeechMs = 0L,
        isVadActive = false,
        waveformBars = VadWaveformMath.emptyBars(),
        lastEvaluation = null,
        wordGradesByMushafPos = buildInitialTurnWordGrades(turn),
        statusMessageOverride = null
      )
    }

    if (mode == KidsPracticeMode.RECITE_AND_REVEAL && autoStartStreamingInModeB) {
      startReciteAndRevealStreaming(appCtx)
    }
  }

  @JvmStatic
  fun onMushafPageTapped(): Boolean {
    val state = _uiState.value
    if (!state.isEnabled) return false
    if (state.stage == ClassroomStage.TASMEE_STREAMING) {
      revealHintWordInTasmee()
      return true
    }
    if (state.stage != ClassroomStage.CHILD_RECITING) {
      return false
    }
    val controller = audioController ?: return false
    if (!controller.canStopChildRecordingByPageTap()) {
      return true
    }
    finishChildRecitationEarly()
    return true
  }

  fun toggleClassroomMode(
    context: Context,
    currentPage: Int,
    selectedAyah: SuraAyah?,
    quranInfo: QuranInfo,
    enableWordHiding: Boolean = false
  ): SuraAyah? {
    return if (isEnabled()) {
      disableClassroomMode(context)
      null
    } else {
      enableClassroomMode(context, currentPage, selectedAyah, quranInfo, enableWordHiding)
    }
  }

  private fun resolveInitialAyah(
    context: Context,
    currentPage: Int,
    selectedAyah: SuraAyah?,
    quranInfo: QuranInfo
  ): SuraAyah {
    if (selectedAyah != null && MinshawiJuz30Repository.getVerse(context, selectedAyah) != null) {
      return selectedAyah
    }
    if (currentPage in 1..604) {
      val suraOnPage = quranInfo.getSuraOnPage(currentPage)
      val firstAyahOnPage = quranInfo.getFirstAyahOnPage(currentPage).coerceAtLeast(1)
      val candidate = SuraAyah(suraOnPage, firstAyahOnPage)
      if (MinshawiJuz30Repository.getVerse(context, candidate) != null) {
        return candidate
      }
    }
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedSurah = prefs.getInt(KEY_LAST_SURAH, 78)
    val savedAyah = prefs.getInt(KEY_LAST_AYAH, 1)
    val saved = SuraAyah(savedSurah, savedAyah)
    return if (MinshawiJuz30Repository.getVerse(context, saved) != null) {
      saved
    } else {
      SuraAyah(78, 1)
    }
  }

  /**
   * Downloads missing offline AI teacher assets (`vocab.json`, `minshawi_teacher_full.json`,
   * `fastconformer_quran_int8.onnx`) from Hugging Face.
   */
  fun startModelDownload(context: Context) {
    if (downloadJob?.isActive == true) return
    val appCtx = context.applicationContext
    downloadJob = scope.launch {
      val coreAlreadyReady = KidsAssetDownloader.areCoreEngineAssetsReady(appCtx)
      updateState {
        it.copy(
          stage = if (coreAlreadyReady) it.stage else ClassroomStage.DOWNLOADING_MODELS,
          downloadProgress = it.downloadProgress.copy(isDownloading = true, errorMessage = null)
        )
      }
      val ok = KidsAssetDownloader.ensureAllAssetsDownloaded(appCtx) { progress ->
        scope.launch {
          updateState { state ->
            val readyForUse = progress.isReady || KidsAssetDownloader.areCoreEngineAssetsReady(appCtx)
            state.copy(
              stage = if (readyForUse && state.stage == ClassroomStage.DOWNLOADING_MODELS) {
                ClassroomStage.IDLE
              } else if (!readyForUse) {
                ClassroomStage.DOWNLOADING_MODELS
              } else {
                state.stage
              },
              isFullQuranLoaded = MinshawiJuz30Repository.isFullDatasetLoaded(),
              downloadProgress = progress
            )
          }
        }
      }
      if (ok) {
        val activeQari = MinshawiJuz30Repository.getActiveTeacherQari()
        if (!AsrModelManager.isTeacherFullJsonReady(appCtx, activeQari)) {
          AsrModelManager.ensureTeacherFullJsonDownloaded(appCtx, activeQari)
        }
        MinshawiJuz30Repository.reloadIfFullDatasetAvailable(appCtx)
        FastConformerAsrEngine.ensureInitialized(appCtx)
        val curAyah = _uiState.value.currentAyah
        val refreshedVerse = MinshawiJuz30Repository.getVerse(appCtx, curAyah)
        val refreshedTurn = refreshedVerse?.turns?.firstOrNull()
        updateState {
          it.copy(
            stage = if (it.stage == ClassroomStage.DOWNLOADING_MODELS) ClassroomStage.IDLE else it.stage,
            surahNameAr = refreshedVerse?.surahNameAr ?: it.surahNameAr,
            totalTurns = refreshedVerse?.totalTurns ?: it.totalTurns,
            currentTurn = refreshedTurn ?: it.currentTurn,
            ayahWords = refreshedVerse?.allUthmaniWords ?: it.ayahWords,
            isFullQuranLoaded = MinshawiJuz30Repository.isFullDatasetLoaded(),
            downloadProgress = it.downloadProgress.copy(
              isReady = true,
              isDownloading = false,
              errorMessage = null
            )
          )
        }
      }
    }
  }

  /**
   * Selects a specific Surah (`1..114`) & Ayah (and turn), updating both Kids Tab and Mushaf HUD.
   */
  fun selectAyahAndTurn(
    context: Context,
    suraAyah: SuraAyah,
    turnIndex: Int = 0,
    autoPlayLoop: Boolean = false,
    autoStartMode: KidsPracticeMode? = if (autoPlayLoop) KidsPracticeMode.LISTEN_AND_REPEAT else null,
    preserveRepetitionProgress: Boolean = false
  ) {
    val appCtx = context.applicationContext
    MinshawiJuz30Repository.ensureLoaded(appCtx)
    val verseSpec = MinshawiJuz30Repository.getVerse(appCtx, suraAyah)
    if (verseSpec == null) {
      // Surah 1..77 requested before minshawi_teacher_full.json finished downloading
      appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putInt(KEY_LAST_SURAH, suraAyah.sura)
        .putInt(KEY_LAST_AYAH, suraAyah.ayah)
        .apply()
      updateState {
        it.copy(
          currentAyah = suraAyah,
          surahNameAr = MinshawiJuz30Repository.getSurahNameAr(suraAyah.sura),
          stage = ClassroomStage.DOWNLOADING_MODELS
        )
      }
      startModelDownload(appCtx)
      return
    }

    val clampedTurnIdx = turnIndex.coerceIn(0, (verseSpec.turns.size - 1).coerceAtLeast(0))
    val turn = verseSpec.turns.getOrNull(clampedTurnIdx)

    autoNextJob?.cancel()
    val keepStreaming = autoStartMode == KidsPracticeMode.RECITE_AND_REVEAL &&
      _uiState.value.stage == ClassroomStage.TASMEE_STREAMING
    if (!keepStreaming) {
      audioController?.stopPlaybackAndMicGate()
    } else {
      audioController?.resetStreamingRingBuffer()
    }

    val prevAyah = _uiState.value.currentAyah
    if (suraAyah != prevAyah || clampedTurnIdx == 0) {
      ayahTurnScores.clear()
    }
    if (suraAyah.sura != prevAyah.sura) {
      surahAyahScores.clear()
    }
    activeRetryTailTurn = null
    pendingPostCelebrationAction = PostCelebrationAction.None

    appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
      .putInt(KEY_LAST_SURAH, verseSpec.surah)
      .putInt(KEY_LAST_AYAH, verseSpec.ayah)
      .apply()

    val assetsReady = KidsAsrAssetDownloader.areCoreEngineAssetsReady(appCtx)
    val targetMode = autoStartMode ?: _uiState.value.practiceMode
    updateState { prev ->
      val nextAyahRepDone = when {
        preserveRepetitionProgress -> prev.ayahRepeatCompleted
        suraAyah != prev.currentAyah || clampedTurnIdx == 0 -> 0
        else -> prev.ayahRepeatCompleted
      }
      val nextSurahRepDone = when {
        preserveRepetitionProgress -> prev.surahRepeatCompleted
        suraAyah.sura != prev.currentAyah.sura -> 0
        else -> prev.surahRepeatCompleted
      }
      prev.copy(
        practiceMode = targetMode,
        stage = when {
          !assetsReady -> ClassroomStage.DOWNLOADING_MODELS
          keepStreaming -> ClassroomStage.TASMEE_STREAMING
          else -> ClassroomStage.IDLE
        },
        isAutoTransitionPending = false,
        isRetryAttempt = false,
        currentAyah = verseSpec.suraAyah,
        surahNameAr = verseSpec.surahNameAr,
        currentTurnIndex = clampedTurnIdx,
        totalTurns = verseSpec.totalTurns,
        currentTurn = turn,
        ayahWords = verseSpec.allUthmaniWords,
        revealedCount = 0,
        ayahRepeatCompleted = nextAyahRepDone,
        surahRepeatCompleted = nextSurahRepDone,
        activeMistakeTailStartWordIndex = 0,
        pendingCelebration = null,
        isFullQuranLoaded = MinshawiJuz30Repository.isFullDatasetLoaded(),
        childMaxTimeoutSec = turn?.computeChildMaxTimeoutSec() ?: 12.0f,
        activePlaybackWordPosition = -1,
        micLevelRms = if (keepStreaming) prev.micLevelRms else 0f,
        voicedSpeechMs = 0L,
        isVadActive = if (keepStreaming) prev.isVadActive else false,
        waveformBars = if (keepStreaming) prev.waveformBars else VadWaveformMath.emptyBars(),
        lastEvaluation = null,
        wordGradesByMushafPos = buildInitialTurnWordGrades(turn),
        statusMessageOverride = null
      )
    }

    if (_uiState.value.isEnabled) {
      onNavigateToAyahPage?.invoke(verseSpec.suraAyah)
    }

    if (assetsReady && autoStartMode != null && !keepStreaming) {
      when (autoStartMode) {
        KidsPracticeMode.LISTEN_AND_REPEAT -> startThreeStageLoopForCurrentTurn(appCtx)
        KidsPracticeMode.RECITE_AND_REVEAL -> startReciteAndRevealStreaming(appCtx)
        KidsPracticeMode.RECITE_ONLY -> startReciteOnlyForCurrentTurn(appCtx)
      }
    }
  }

  /**
   * Starts **Mode A** ("Teacher & Kids Repeat" — Graded 3-Stage Loop) for the current turn:
   * Stage 1 (`Sheikh`) -> Stage 2 (`Audio Kids`) -> Stage 3 (`Child's Turn` + ONNX Grader).
   */
  fun startThreeStageLoopForCurrentTurn(
    context: Context,
    isRetryAttempt: Boolean = false
  ) {
    val appCtx = context.applicationContext
    if (!KidsAsrAssetDownloader.areCoreEngineAssetsReady(appCtx)) {
      startModelDownload(appCtx)
      return
    }

    val state = _uiState.value
    val verse = MinshawiJuz30Repository.getVerse(appCtx, state.currentAyah) ?: run {
      startModelDownload(appCtx)
      return
    }
    val turn = verse.turns.getOrNull(state.currentTurnIndex) ?: return
    val nextVerse = MinshawiJuz30Repository.getNextVerse(appCtx, state.currentAyah)

    autoNextJob?.cancel()
    if (!isRetryAttempt) {
      activeRetryTailTurn = null
    }

    if (QuranMemorizationManager.isHiddenModeEnabled()) {
      val turnPositions = (1..turn.uthmaniWords.size).map { turn.wordStartIdx + it }
      QuranMemorizationManager.hideWordRangeForAyah(state.currentAyah, turnPositions)
    }

    updateState {
      it.copy(
        practiceMode = KidsPracticeMode.LISTEN_AND_REPEAT,
        stage = ClassroomStage.QARI_RECITING,
        isAutoTransitionPending = false,
        isRetryAttempt = isRetryAttempt,
        activeMistakeTailStartWordIndex = 0,
        childMaxTimeoutSec = turn.computeChildMaxTimeoutSec(),
        activePlaybackWordPosition = turn.wordStartIdx + 1,
        micLevelRms = 0f,
        voicedSpeechMs = 0L,
        isVadActive = false,
        waveformBars = VadWaveformMath.emptyBars(),
        lastEvaluation = null,
        wordGradesByMushafPos = buildInitialTurnWordGrades(turn),
        statusMessageOverride = null
      )
    }

    val controller = getOrCreateAudioController(appCtx)
    controller.playTurnQariAndKids(
      verse = verse,
      turn = turn,
      nextVerseToPrefetch = nextVerse,
      hasChildrenRepeat = MinshawiJuz30Repository.hasChildrenRepeat(),
      onStageChanged = { newStage ->
        updateState { it.copy(stage = newStage) }
      },
      onActiveWordChanged = { mushafWordPos ->
        updateState { prev ->
          if (prev.activePlaybackWordPosition == mushafWordPos) prev
          else prev.copy(activePlaybackWordPosition = mushafWordPos)
        }
      },
      onStage2Completed = {
        beginStage3ChildRecitation(appCtx, mode = KidsPracticeMode.LISTEN_AND_REPEAT)
      },
      onPlaybackError = { errMsg ->
        updateState {
          it.copy(
            stage = ClassroomStage.IDLE,
            isAutoTransitionPending = false,
            activePlaybackWordPosition = -1,
            statusMessageOverride = errMsg
          )
        }
      }
    )
  }

  /**
   * Plays **Sheikh Al-Minshawi ONLY (no kids chorus)** from the first mistaken word to the end of
   * the turn ([tailTurn]), then immediately transitions to Stage 3 grading only those remaining words.
   */
  fun startQariOnlyMistakeTailRetry(
    context: Context,
    tailTurn: TurnSpec
  ) {
    val appCtx = context.applicationContext
    if (!KidsAsrAssetDownloader.areCoreEngineAssetsReady(appCtx)) {
      startModelDownload(appCtx)
      return
    }
    val state = _uiState.value
    val verse = MinshawiJuz30Repository.getVerse(appCtx, state.currentAyah) ?: return
    val nextVerse = MinshawiJuz30Repository.getNextVerse(appCtx, state.currentAyah)

    autoNextJob?.cancel()
    activeRetryTailTurn = tailTurn

    // Keep already-correct prefix words visible on the Mushaf; hide only the mistaken tail
    if (QuranMemorizationManager.isHiddenModeEnabled()) {
      val tailPositions = (1..tailTurn.uthmaniWords.size).map { tailTurn.wordStartIdx + it }
      QuranMemorizationManager.hideWordRangeForAyah(state.currentAyah, tailPositions)
    }

    updateState {
      it.copy(
        practiceMode = KidsPracticeMode.LISTEN_AND_REPEAT,
        stage = ClassroomStage.QARI_RECITING,
        isAutoTransitionPending = false,
        isRetryAttempt = true,
        activeMistakeTailStartWordIndex = tailTurn.wordStartIdx,
        childMaxTimeoutSec = tailTurn.computeChildMaxTimeoutSec(),
        activePlaybackWordPosition = tailTurn.wordStartIdx + 1,
        micLevelRms = 0f,
        voicedSpeechMs = 0L,
        isVadActive = false,
        waveformBars = VadWaveformMath.emptyBars(),
        lastEvaluation = null,
        wordGradesByMushafPos = buildInitialTurnWordGrades(tailTurn),
        statusMessageOverride = null
      )
    }

    val controller = getOrCreateAudioController(appCtx)
    controller.playQariOnlyTail(
      verse = verse,
      tailTurn = tailTurn,
      nextVerseToPrefetch = nextVerse,
      playbackSpeed = KidsClassroomAudioController.MISTAKE_TAIL_SLOW_MO_SPEED,
      onStageChanged = { newStage ->
        updateState { it.copy(stage = newStage) }
      },
      onActiveWordChanged = { mushafWordPos ->
        updateState { prev ->
          if (prev.activePlaybackWordPosition == mushafWordPos) prev
          else prev.copy(activePlaybackWordPosition = mushafWordPos)
        }
      },
      onQariCompleted = {
        beginStage3ChildRecitation(
          context = appCtx,
          mode = KidsPracticeMode.LISTEN_AND_REPEAT,
          isRetryAttempt = true
        )
      },
      onPlaybackError = { errMsg ->
        updateState {
          it.copy(
            stage = ClassroomStage.IDLE,
            isAutoTransitionPending = false,
            activePlaybackWordPosition = -1,
            statusMessageOverride = errMsg
          )
        }
      }
    )
  }

  /**
   * Starts **Mode B** ("Recite & Reveal / Tasmee'" — `التسميع الحي`):
   * - `ExoPlayer` is off.
   * - Every `350 ms`, slices the latest `4.5s` from the rolling `AudioRecord` ring buffer and runs
   *   ONNX + [KidsTasmeeRevealMatcher] on `Dispatchers.Default`.
   * - Monotonically advances `revealedCount`; when `revealedCount == ayahWords.size`, resets the
   *   rolling audio buffer and advances to `next_surah:next_ayah`.
   */
  fun startReciteAndRevealStreaming(context: Context) {
    val appCtx = context.applicationContext
    if (!KidsAsrAssetDownloader.areCoreEngineAssetsReady(appCtx)) {
      startModelDownload(appCtx)
      return
    }
    if (!hasRecordAudioPermission(appCtx)) {
      updateState {
        it.copy(
          practiceMode = KidsPracticeMode.RECITE_AND_REVEAL,
          stage = ClassroomStage.IDLE
        )
      }
      onRequestMicPermission?.invoke()
      return
    }

    val verse = MinshawiJuz30Repository.getVerse(appCtx, _uiState.value.currentAyah) ?: run {
      startModelDownload(appCtx)
      return
    }

    if (com.quran.labs.androidquran.memorization.tasmee.LiveTasmeeManager.isEnabled()) {
      com.quran.labs.androidquran.memorization.tasmee.LiveTasmeeManager.stopTasmee()
    }

    autoNextJob?.cancel()
    val ayahWords = verse.allUthmaniWords
    val keepExistingRevealed = if (_uiState.value.revealedCount in 1 until ayahWords.size) {
      _uiState.value.revealedCount
    } else {
      0
    }

    updateState {
      it.copy(
        practiceMode = KidsPracticeMode.RECITE_AND_REVEAL,
        stage = ClassroomStage.TASMEE_STREAMING,
        isAutoTransitionPending = false,
        isRetryAttempt = false,
        ayahWords = ayahWords,
        revealedCount = keepExistingRevealed,
        activePlaybackWordPosition = -1,
        micLevelRms = 0f,
        isVadActive = false,
        waveformBars = VadWaveformMath.emptyBars(),
        lastEvaluation = null,
        statusMessageOverride = null
      )
    }

    val controller = getOrCreateAudioController(appCtx)
    controller.startStreamingTasmee(
      onMicLevelChanged = { rms, isVadActive, bars ->
        scope.launch {
          updateState { prev ->
            if (prev.stage != ClassroomStage.TASMEE_STREAMING) prev
            else prev.copy(
              micLevelRms = rms,
              isVadActive = isVadActive,
              waveformBars = bars
            )
          }
        }
      },
      onWindowSlice = { windowPcm, hadSilenceGap, forceTrailingDecode ->
        processModeBStreamingHop(appCtx, windowPcm, hadSilenceGap, forceTrailingDecode)
      }
    )
  }

  private suspend fun processModeBStreamingHop(
    appCtx: Context,
    windowPcm: FloatArray,
    hadSilenceGap: Boolean,
    forceTrailingDecode: Boolean = false
  ) {
    val stateSnap = _uiState.value
    if (stateSnap.stage != ClassroomStage.TASMEE_STREAMING ||
      stateSnap.practiceMode != KidsPracticeMode.RECITE_AND_REVEAL ||
      stateSnap.isAutoTransitionPending
    ) {
      return
    }

    val targetAyah = stateSnap.currentAyah
    val verse = MinshawiJuz30Repository.getVerse(appCtx, targetAyah) ?: return

    val prep = NeMoMelPreprocessor.processRollingTasmeeWindow(
      windowPcm = windowPcm,
      recentHopSamples = if (forceTrailingDecode) windowPcm.size else 8800,
      maxActiveSamples = KidsClassroomAudioController.TASMEE_RING_SAMPLES,
      minRecentRms = if (forceTrailingDecode) 0f else NeMoMelPreprocessor.MIN_PEAK_RMS
    )
    val mel = prep as? NeMoMelPreprocessor.PreprocessResult.MelFeatures ?: return

    val asrOut = FastConformerAsrEngine.runInference(
      context = appCtx,
      melFeatures = mel.features,
      numFrames = mel.numFrames
    ) ?: return

    // Re-check state after ONNX inference in case user switched Ayah or stopped
    val latest = _uiState.value
    if (latest.stage != ClassroomStage.TASMEE_STREAMING ||
      latest.currentAyah != targetAyah ||
      latest.isAutoTransitionPending
    ) {
      return
    }

    val revealSpec = KidsTasmeeRevealMatcher.AyahRevealSpec.fromVerse(verse, asrOut.vocab)
    if (revealSpec.wordCount <= 0) return

    val hypWords = QuranRecitationGrader.greedyCtcDecode(
      logprobs = asrOut.logprobs,
      vocab = asrOut.vocab,
      blankId = asrOut.blankId
    )

    val nextRevealed = KidsTasmeeRevealMatcher.computeNextRevealedCount(
      spec = revealSpec,
      currentRevealedCount = latest.revealedCount,
      hypWords = hypWords,
      logprobs = asrOut.logprobs,
      hadSilenceGapSinceLastReveal = hadSilenceGap
    )

    if (nextRevealed > latest.revealedCount) {
      audioController?.clearTasmeeSilenceGapFlag()
      val completedAyah = nextRevealed >= revealSpec.wordCount
      if (completedAyah) {
        // Reset rolling audio buffer immediately so old words never leak into next_surah:next_ayah
        audioController?.resetStreamingRingBuffer()
      }
      scope.launch {
        if (QuranMemorizationManager.isHiddenModeEnabled()) {
          QuranMemorizationManager.revealWordRangeForAyah(
            suraAyah = targetAyah,
            wordPositions = (1..nextRevealed).toList(),
            context = appCtx
          )
        }
        updateState { cur ->
          if (cur.currentAyah != targetAyah || cur.stage != ClassroomStage.TASMEE_STREAMING) {
            cur
          } else {
            val monotonic = maxOf(cur.revealedCount, nextRevealed).coerceAtMost(revealSpec.wordCount)
            cur.copy(
              revealedCount = monotonic,
              isAutoTransitionPending = monotonic >= revealSpec.wordCount
            )
          }
        }
        if (completedAyah) {
          advanceCompletedTasmeeAyah(appCtx, targetAyah)
        }
      }
    }
  }

  private fun advanceCompletedTasmeeAyah(appCtx: Context, completedAyah: SuraAyah) {
    autoNextJob?.cancel()
    autoNextJob = scope.launch {
      delay(TASMEE_AYAH_ADVANCE_BEAT_MS)
      val cur = _uiState.value
      if (cur.stage != ClassroomStage.TASMEE_STREAMING || cur.currentAyah != completedAyah) {
        return@launch
      }
      audioController?.resetStreamingRingBuffer()
      val nextVerse = MinshawiJuz30Repository.getNextVerse(appCtx, completedAyah)
      if (nextVerse != null && cur.autoNextEnabled) {
        selectAyahAndTurn(
          context = appCtx,
          suraAyah = nextVerse.suraAyah,
          turnIndex = 0,
          autoStartMode = KidsPracticeMode.RECITE_AND_REVEAL
        )
      } else {
        updateState { it.copy(isAutoTransitionPending = false) }
      }
    }
  }

  /**
   * Reveals 1 word manually as a hint in Mode B ("Recite & Reveal / Tasmee'").
   */
  fun revealHintWordInTasmee(context: Context? = null) {
    val cur = _uiState.value
    val total = cur.ayahWords.size
    if (total <= 0) return
    val nextCount = (cur.revealedCount + 1).coerceAtMost(total)
    if (QuranMemorizationManager.isHiddenModeEnabled()) {
      QuranMemorizationManager.revealWordRangeForAyah(
        suraAyah = cur.currentAyah,
        wordPositions = (1..nextCount).toList(),
        context = context?.applicationContext
      )
    }
    updateState { it.copy(revealedCount = nextCount) }
    if (nextCount >= total && context != null && cur.stage == ClassroomStage.TASMEE_STREAMING) {
      audioController?.resetStreamingRingBuffer()
      advanceCompletedTasmeeAyah(context.applicationContext, cur.currentAyah)
    }
  }

  /**
   * Resets `revealedCount = 0` and clears the rolling ring buffer for the current Ayah in Mode B.
   */
  fun resetCurrentAyahTasmee() {
    autoNextJob?.cancel()
    audioController?.resetStreamingRingBuffer()
    val cur = _uiState.value
    if (QuranMemorizationManager.isHiddenModeEnabled() && cur.ayahWords.isNotEmpty()) {
      QuranMemorizationManager.hideWordRangeForAyah(
        suraAyah = cur.currentAyah,
        wordPositions = (1..cur.ayahWords.size).toList()
      )
    }
    updateState {
      it.copy(
        revealedCount = 0,
        isAutoTransitionPending = false
      )
    }
  }

  fun startReciteOnlyForCurrentTurn(context: Context) {
    beginStage3ChildRecitation(context, mode = KidsPracticeMode.RECITE_ONLY, isRetryAttempt = false)
  }

  /**
   * Starts Stage 3 (`Child's Turn`) with dynamic max timeout
   * `maxOf(12.0f, (turn.qari_end_sec - turn.qari_start_sec) * 1.6f + 4.0f)`.
   */
  fun beginStage3ChildRecitation(
    context: Context,
    mode: KidsPracticeMode = _uiState.value.practiceMode,
    isRetryAttempt: Boolean = _uiState.value.isRetryAttempt
  ) {
    val appCtx = context.applicationContext
    if (!KidsAsrAssetDownloader.areCoreEngineAssetsReady(appCtx)) {
      startModelDownload(appCtx)
      return
    }

    if (!hasRecordAudioPermission(appCtx)) {
      updateState {
        it.copy(
          practiceMode = mode,
          stage = ClassroomStage.IDLE,
          isAutoTransitionPending = false,
          isRetryAttempt = false,
          activePlaybackWordPosition = -1
        )
      }
      onRequestMicPermission?.invoke()
      return
    }

    val turn = activeRetryTailTurn ?: _uiState.value.currentTurn ?: return
    val dynamicTimeoutSec = turn.computeChildMaxTimeoutSec()
    autoNextJob?.cancel()
    earlyCompleteJob?.cancel()

    if (AsrModelManager.isStreamingAsrReady(appCtx) && !streamingAsrEngine.isLoaded) {
      streamingAsrEngine.load(appCtx)
    }
    if (streamingAsrEngine.isLoaded) {
      streamingAsrEngine.resetStreamState()
    }

    if (QuranMemorizationManager.isHiddenModeEnabled()) {
      val turnPositions = (1..turn.uthmaniWords.size).map { turn.wordStartIdx + it }
      QuranMemorizationManager.hideWordRangeForAyah(_uiState.value.currentAyah, turnPositions)
    }

    updateState {
      it.copy(
        practiceMode = mode,
        stage = ClassroomStage.CHILD_RECITING,
        isAutoTransitionPending = false,
        isRetryAttempt = isRetryAttempt,
        childMaxTimeoutSec = dynamicTimeoutSec,
        activePlaybackWordPosition = -1,
        micLevelRms = 0f,
        voicedSpeechMs = 0L,
        isVadActive = false,
        waveformBars = VadWaveformMath.emptyBars(),
        lastEvaluation = null,
        lastStarsEarned = 0,
        streamingMatchedWordCount = 0,
        streamingPartialTranscript = "",
        isStreamingAsrReady = streamingAsrEngine.isLoaded,
        wordGradesByMushafPos = buildInitialTurnWordGrades(turn),
        statusMessageOverride = null
      )
    }

    val targetNormWords = turn.normalizedWords.ifEmpty { turn.uthmaniWords }
    val targetQariWords = turn.qariOnnxWords.ifEmpty { targetNormWords }
    val streamChunkCallback: ((ShortArray) -> Unit)? = if (streamingAsrEngine.isLoaded) {
      { pcmChunk ->
        val chunkRes = streamingAsrEngine.processAudioChunk(
          pcmChunk = pcmChunk,
          targetNormalizedWords = targetNormWords,
          targetQariWords = targetQariWords
        )
        scope.launch {
          updateState { prev ->
            if (prev.stage != ClassroomStage.CHILD_RECITING) prev
            else prev.copy(
              streamingMatchedWordCount = maxOf(prev.streamingMatchedWordCount, chunkRes.matchedWordCount),
              streamingPartialTranscript = chunkRes.partialTranscript
            )
          }
          // When all words in the turn match live, finalize 450 ms later for Stage-4 offline grading
          if (chunkRes.allWordsMatched && earlyCompleteJob?.isActive != true) {
            earlyCompleteJob = scope.launch {
              delay(450L)
              if (_uiState.value.stage == ClassroomStage.CHILD_RECITING) {
                audioController?.stopChildTurnRecordingNow()
              }
            }
          }
        }
      }
    } else {
      null
    }

    getOrCreateAudioController(appCtx).startChildTurnRecording(
      maxTimeoutSec = dynamicTimeoutSec,
      streamingChunkSamples = streamingAsrEngine.chunkSamples(),
      onStreamingPcmChunk = streamChunkCallback,
      onMicLevelChanged = { rms, voicedMs, isVadActive, bars ->
        scope.launch {
          updateState { prev ->
            if (prev.stage != ClassroomStage.CHILD_RECITING) prev
            else prev.copy(
              micLevelRms = rms,
              voicedSpeechMs = voicedMs,
              isVadActive = isVadActive,
              waveformBars = bars
            )
          }
        }
      },
      onRecordingCompleted = { pcmFloat32 ->
        earlyCompleteJob?.cancel()
        gradeCapturedChildAudio(appCtx, pcmFloat32)
      }
    )
  }

  fun finishChildRecitationEarly() {
    audioController?.stopChildTurnRecordingNow()
  }

  private fun gradeCapturedChildAudio(context: Context, pcmFloat32: FloatArray) {
    val state = _uiState.value
    val suraAyah = state.currentAyah
    val turn = activeRetryTailTurn ?: state.currentTurn ?: return
    val difficulty = state.difficultyLevel
    val hadSustainedVoice = state.voicedSpeechMs >= KidsClassroomAudioController.MIN_VOICED_SPEECH_MS

    updateState {
      it.copy(
        stage = ClassroomStage.SCORING,
        micLevelRms = 0f,
        isVadActive = false,
        waveformBars = VadWaveformMath.emptyBars()
      )
    }

    scope.launch {
      val minRms = if (hadSustainedVoice) {
        NeMoMelPreprocessor.MIN_PEAK_RMS // 0.004f per spec
      } else {
        KIDS_MIN_SPEECH_PEAK_RMS
      }
      val outcome = offlineBatchAsrEngine.gradeFullUtteranceFloat(
        context = context,
        pcmFloat32 = pcmFloat32,
        suraAyah = suraAyah,
        turn = turn,
        minPeakRms = minRms,
        passThresholdPercent = difficulty.passThresholdPercent.toDouble(),
        minWordAccuracy = difficulty.minWordAccuracy
      )
      applyTurnEvaluationResult(context, outcome.evaluation)
    }
  }

  internal fun applyTurnEvaluationResult(context: Context, evalResult: TurnEvaluationResult) {
    val appCtx = context.applicationContext
    val stateBefore = _uiState.value
    val difficulty = stateBefore.difficultyLevel
    val normalizedWordAcc = if (evalResult.wordAccuracy > 1.0) {
      evalResult.wordAccuracy / 100.0
    } else {
      evalResult.wordAccuracy
    }
    val effectivePassed = !evalResult.noSpeechDetected &&
      evalResult.scorePercent >= difficulty.passThresholdPercent.toDouble() &&
      normalizedWordAcc >= difficulty.minWordAccuracy
    val effectiveEval = if (evalResult.passed == effectivePassed) {
      evalResult
    } else {
      evalResult.copy(passed = effectivePassed)
    }

    val gradesMap = effectiveEval.wordGrades.associateBy { it.mushafWordPosition }
    val autoNext = stateBefore.autoNextEnabled
    val nextStage = if (effectivePassed) ClassroomStage.TURN_PASSED else ClassroomStage.TURN_RETRY
    val turnStars = RewardsDataStore.computeStarsFromGrade(effectiveEval)

    val surahNameAr = stateBefore.surahNameAr.ifBlank {
      MinshawiJuz30Repository.getSurahNameAr(effectiveEval.suraAyah.sura)
    }
    // Persist per-Surah word & Ayah revision analytics for every voiced recitation turn
    KidsSurahAnalysisStore.recordEvaluation(
      context = appCtx,
      evalResult = effectiveEval,
      effectivePassed = effectivePassed,
      surahNameAr = surahNameAr
    )

    // Reveal the evaluated turn's words on the Mushaf page so the learner sees Tajweed + grade badges
    if (QuranMemorizationManager.isHiddenModeEnabled() && effectiveEval.wordGrades.isNotEmpty()) {
      QuranMemorizationManager.revealWordRangeForAyah(
        suraAyah = effectiveEval.suraAyah,
        wordPositions = effectiveEval.wordGrades.map { it.mushafWordPosition },
        context = appCtx
      )
    }

    val isFullAyahCompleted = effectivePassed &&
      effectiveEval.turnIndex + 1 >= effectiveEval.totalTurns.coerceAtLeast(1)

    if (effectivePassed) {
      activeRetryTailTurn = null
      ayahTurnScores.add(effectiveEval.scorePercent.coerceIn(0.0, 100.0))
    }

    if (isFullAyahCompleted) {
      val avgAyahScore = if (ayahTurnScores.isNotEmpty()) {
        ayahTurnScores.average().coerceIn(0.0, 100.0)
      } else {
        effectiveEval.scorePercent.coerceIn(0.0, 100.0)
      }
      val ayahScoreInt = avgAyahScore.toInt().coerceIn(0, 100)
      val ayahStars = maxOf(
        turnStars,
        when {
          ayahScoreInt >= 90 -> 3
          ayahScoreInt >= 78 -> 2
          else -> 1
        }
      ).coerceIn(1, 3)

      RewardsDataStore.recordAyahStars(
        context = appCtx,
        surah = effectiveEval.suraAyah.sura,
        ayah = effectiveEval.suraAyah.ayah,
        starsEarned = ayahStars
      )
      surahAyahScores[effectiveEval.suraAyah.ayah] = avgAyahScore
      ayahTurnScores.clear()

      val ayahTarget = stateBefore.ayahRepeatTarget.coerceAtLeast(1)
      val surahTarget = stateBefore.surahRepeatTarget.coerceAtLeast(1)
      val nextAyahRepDone = stateBefore.ayahRepeatCompleted + 1
      val nextVerse = MinshawiJuz30Repository.getNextVerse(appCtx, effectiveEval.suraAyah)
      val isLastAyahInSurah = nextVerse == null || nextVerse.surah != effectiveEval.suraAyah.sura

      // Full-screen celebration triggers ONLY after completing the FULL Surah (final Ayah + all its repetitions)
      if (isLastAyahInSurah && nextAyahRepDone >= ayahTarget) {
        val totalStarsNow = RewardsDataStore.state.value.totalStars
        val surahAvgScore = if (surahAyahScores.isNotEmpty()) {
          surahAyahScores.values.average().coerceIn(0.0, 100.0)
        } else {
          avgAyahScore
        }
        val surahScoreInt = surahAvgScore.toInt().coerceIn(0, 100)
        val surahStars = when {
          surahScoreInt >= 90 -> 3
          surahScoreInt >= 78 -> 2
          else -> 1
        }
        surahAyahScores.clear()

        val surahBadgeAward = RewardsDataStore.recordSurahCompletion(
          context = appCtx,
          surah = effectiveEval.suraAyah.sura,
          surahNameAr = surahNameAr,
          accuracyPercent = surahScoreInt
        )

        val nextSurahRepDone = stateBefore.surahRepeatCompleted + 1
        val nextAction = if (nextSurahRepDone < surahTarget) {
          PostCelebrationAction.RepeatCurrentSurah(
            firstAyahOfSurah = SuraAyah(effectiveEval.suraAyah.sura, 1),
            nextSurahRepeatCompleted = nextSurahRepDone
          )
        } else if (nextVerse != null) {
          PostCelebrationAction.AdvanceNextAyah(
            nextAyah = nextVerse.suraAyah,
            keepSurahRepeatCompleted = 0
          )
        } else {
          PostCelebrationAction.None
        }

        val surahAnalysis = KidsSurahAnalysisStore.getSurahSummary(appCtx, effectiveEval.suraAyah.sura)
        val totalAyatInSurah = MinshawiJuz30Repository.getAyahCountForSurah(effectiveEval.suraAyah.sura)
          .coerceAtLeast(effectiveEval.suraAyah.ayah)

        pendingPostCelebrationAction = nextAction
        celebrationEventSeq += 1L
        val celebrationEvent = AyahCelebrationEvent(
          eventId = celebrationEventSeq,
          suraAyah = effectiveEval.suraAyah,
          surahNameAr = surahNameAr,
          scorePercent = surahScoreInt,
          starsEarned = surahStars,
          totalStars = totalStarsNow,
          theme = pickRandomCelebrationTheme(),
          ayahRepetitionCompleted = ayahTarget,
          ayahRepetitionTarget = ayahTarget,
          surahRepetitionCompleted = nextSurahRepDone.coerceAtMost(surahTarget),
          surahRepetitionTarget = surahTarget,
          isSurahCompleted = true,
          missedWordsInSurahCount = surahAnalysis.uniqueDifficultWordCount,
          totalAyatInSurah = totalAyatInSurah,
          awardedSurahBadge = surahBadgeAward.badge,
          isNewSurahBadge = surahBadgeAward.isNewlyUnlocked,
          isUpgradedSurahBadge = surahBadgeAward.isTierUpgraded
        )

        autoNextJob?.cancel()
        updateState {
          it.copy(
            stage = ClassroomStage.TURN_PASSED,
            isAutoTransitionPending = nextAction != PostCelebrationAction.None && autoNext,
            isRetryAttempt = false,
            activePlaybackWordPosition = -1,
            activeMistakeTailStartWordIndex = 0,
            lastEvaluation = effectiveEval,
            lastStarsEarned = surahStars,
            ayahRepeatCompleted = ayahTarget,
            surahRepeatCompleted = nextSurahRepDone.coerceAtMost(surahTarget),
            pendingCelebration = celebrationEvent,
            wordGradesByMushafPos = gradesMap,
            statusMessageOverride = null
          )
        }
        return
      }

      // Intermediate Ayah or Ayah repetition: NO full-screen celebration — smoothly repeat or advance
      val shouldRepeatSameAyah = nextAyahRepDone < ayahTarget
      val willAutoContinue = autoNext && (shouldRepeatSameAyah || nextVerse != null)

      updateState {
        it.copy(
          stage = ClassroomStage.TURN_PASSED,
          isAutoTransitionPending = willAutoContinue,
          isRetryAttempt = false,
          activePlaybackWordPosition = -1,
          activeMistakeTailStartWordIndex = 0,
          lastEvaluation = effectiveEval,
          lastStarsEarned = ayahStars,
          ayahRepeatCompleted = if (shouldRepeatSameAyah) nextAyahRepDone else 0,
          pendingCelebration = null,
          wordGradesByMushafPos = gradesMap,
          statusMessageOverride = null
        )
      }

      autoNextJob?.cancel()
      if (willAutoContinue) {
        autoNextJob = scope.launch {
          delay(AUTO_NEXT_DELAY_MS)
          val latest = _uiState.value
          if (latest.stage != ClassroomStage.TURN_PASSED || !latest.autoNextEnabled) {
            return@launch
          }
          if (shouldRepeatSameAyah) {
            if (QuranMemorizationManager.isHiddenModeEnabled() && latest.ayahWords.isNotEmpty()) {
              QuranMemorizationManager.hideWordRangeForAyah(
                suraAyah = effectiveEval.suraAyah,
                wordPositions = (1..latest.ayahWords.size).toList()
              )
            }
            selectAyahAndTurn(
              context = appCtx,
              suraAyah = effectiveEval.suraAyah,
              turnIndex = 0,
              autoStartMode = latest.practiceMode,
              preserveRepetitionProgress = true
            )
          } else if (nextVerse != null) {
            selectAyahAndTurn(
              context = appCtx,
              suraAyah = nextVerse.suraAyah,
              turnIndex = 0,
              autoStartMode = latest.practiceMode,
              preserveRepetitionProgress = true
            )
          }
        }
      }
      return
    }

    if (turnStars > 0) {
      RewardsDataStore.recordAyahStars(
        context = appCtx,
        surah = effectiveEval.suraAyah.sura,
        ayah = effectiveEval.suraAyah.ayah,
        starsEarned = turnStars
      )
    }

    // Check if Repeat-From-First-Mistake (Sheikh Only) should slice the remaining words
    var computedTailTurn: TurnSpec? = null
    if (!effectivePassed && stateBefore.repeatFromFirstMistakeOnly && !effectiveEval.noSpeechDetected) {
      val firstMistakeIdx = effectiveEval.wordGrades.indexOfFirst { g ->
        g.status == WordGradeStatus.WORD_ERROR ||
          g.status == WordGradeStatus.MISSING ||
          g.status == WordGradeStatus.TASHKEEL_ERROR
      }
      val baseTurn = activeRetryTailTurn ?: stateBefore.currentTurn
      if (baseTurn != null && firstMistakeIdx >= 0 && firstMistakeIdx < baseTurn.uthmaniWords.size) {
        computedTailTurn = buildMistakeTailTurn(
          baseTurn = baseTurn,
          firstMistakeIndexInTurn = firstMistakeIdx,
          vocab = offlineBatchAsrEngine.vocabArray()
        )
      }
    }
    activeRetryTailTurn = if (!effectivePassed) computedTailTurn else null

    val hasNextPortionOrAyah = run {
      val st = _uiState.value
      val verse = MinshawiJuz30Repository.getVerse(appCtx, st.currentAyah)
      val moreTurns = verse != null && (st.currentTurnIndex + 1 < verse.turns.size)
      moreTurns || MinshawiJuz30Repository.getNextVerse(appCtx, st.currentAyah) != null
    }

    val willAutoAdvance = effectivePassed && autoNext && hasNextPortionOrAyah
    val willAutoRepeatOnFail = !effectivePassed

    updateState {
      it.copy(
        stage = nextStage,
        isAutoTransitionPending = willAutoAdvance || willAutoRepeatOnFail,
        isRetryAttempt = !effectivePassed,
        activePlaybackWordPosition = -1,
        activeMistakeTailStartWordIndex = computedTailTurn?.wordStartIdx ?: 0,
        lastEvaluation = effectiveEval,
        lastStarsEarned = turnStars,
        wordGradesByMushafPos = gradesMap,
        statusMessageOverride = null
      )
    }

    autoNextJob?.cancel()
    if (willAutoAdvance) {
      autoNextJob = scope.launch {
        delay(AUTO_NEXT_DELAY_MS)
        val latest = _uiState.value
        if (latest.stage == ClassroomStage.TURN_PASSED && latest.autoNextEnabled) {
          advanceToNextTurnOrAyah(
            context = appCtx,
            autoStartMode = latest.practiceMode
          )
        }
      }
    } else if (willAutoRepeatOnFail) {
      val scheduledTail = computedTailTurn
      autoNextJob = scope.launch {
        delay(AUTO_RETRY_DELAY_MS)
        val latest = _uiState.value
        if (latest.stage != ClassroomStage.TURN_RETRY) {
          return@launch
        }
        when (latest.practiceMode) {
          KidsPracticeMode.LISTEN_AND_REPEAT -> {
            if (scheduledTail != null && latest.repeatFromFirstMistakeOnly) {
              startQariOnlyMistakeTailRetry(appCtx, scheduledTail)
            } else {
              startThreeStageLoopForCurrentTurn(appCtx, isRetryAttempt = true)
            }
          }
          KidsPracticeMode.RECITE_ONLY -> {
            beginStage3ChildRecitation(
              context = appCtx,
              mode = KidsPracticeMode.RECITE_ONLY,
              isRetryAttempt = true
            )
          }
          KidsPracticeMode.RECITE_AND_REVEAL -> {
            startReciteAndRevealStreaming(appCtx)
          }
        }
      }
    }
  }

  /**
   * Dismisses the full-screen Ayah celebration overlay and executes the scheduled repetition
   * (repeating the same Ayah, looping the Surah, or advancing to the next Ayah).
   */
  fun dismissCelebrationAndContinue(context: Context, autoStart: Boolean = _uiState.value.autoNextEnabled) {
    val appCtx = context.applicationContext
    val cur = _uiState.value
    val action = pendingPostCelebrationAction
    pendingPostCelebrationAction = PostCelebrationAction.None
    val startMode = if (autoStart) cur.practiceMode else null

    when (action) {
      is PostCelebrationAction.RepeatCurrentAyah -> {
        if (QuranMemorizationManager.isHiddenModeEnabled() && cur.ayahWords.isNotEmpty()) {
          QuranMemorizationManager.hideWordRangeForAyah(
            suraAyah = action.suraAyah,
            wordPositions = (1..cur.ayahWords.size).toList()
          )
        }
        updateState {
          it.copy(
            ayahRepeatCompleted = action.nextAyahRepeatCompleted,
            pendingCelebration = null,
            isAutoTransitionPending = false
          )
        }
        selectAyahAndTurn(
          context = appCtx,
          suraAyah = action.suraAyah,
          turnIndex = 0,
          autoStartMode = startMode,
          preserveRepetitionProgress = true
        )
      }
      is PostCelebrationAction.RepeatCurrentSurah -> {
        updateState {
          it.copy(
            ayahRepeatCompleted = 0,
            surahRepeatCompleted = action.nextSurahRepeatCompleted,
            pendingCelebration = null,
            isAutoTransitionPending = false
          )
        }
        selectAyahAndTurn(
          context = appCtx,
          suraAyah = action.firstAyahOfSurah,
          turnIndex = 0,
          autoStartMode = startMode,
          preserveRepetitionProgress = true
        )
      }
      is PostCelebrationAction.AdvanceNextAyah -> {
        updateState {
          it.copy(
            ayahRepeatCompleted = 0,
            surahRepeatCompleted = action.keepSurahRepeatCompleted,
            pendingCelebration = null,
            isAutoTransitionPending = false
          )
        }
        selectAyahAndTurn(
          context = appCtx,
          suraAyah = action.nextAyah,
          turnIndex = 0,
          autoStartMode = startMode,
          preserveRepetitionProgress = true
        )
      }
      PostCelebrationAction.None -> {
        updateState {
          it.copy(
            pendingCelebration = null,
            isAutoTransitionPending = false,
            stage = ClassroomStage.IDLE
          )
        }
      }
    }
  }

  /**
   * Dismisses the full-screen celebration without automatically starting the next loop.
   */
  fun dismissCelebrationOnly() {
    pendingPostCelebrationAction = PostCelebrationAction.None
    updateState {
      it.copy(
        pendingCelebration = null,
        isAutoTransitionPending = false,
        stage = ClassroomStage.IDLE
      )
    }
  }

  /**
   * Navigates directly to the next full Ayah (`turnIndex = 0`), resetting per-Ayah repetition progress.
   */
  fun advanceToNextAyah(context: Context, autoPlay: Boolean = false) {
    val appCtx = context.applicationContext
    val state = _uiState.value
    val nextVerse = MinshawiJuz30Repository.getNextVerse(appCtx, state.currentAyah) ?: return
    val autoStartMode = if (autoPlay) state.practiceMode else null
    selectAyahAndTurn(
      context = appCtx,
      suraAyah = nextVerse.suraAyah,
      turnIndex = 0,
      autoStartMode = autoStartMode,
      preserveRepetitionProgress = false
    )
  }

  /**
   * Navigates directly to the previous full Ayah (`turnIndex = 0`), resetting per-Ayah repetition progress.
   */
  fun goToPreviousAyah(context: Context, autoPlay: Boolean = false) {
    val appCtx = context.applicationContext
    val state = _uiState.value
    val prevVerse = MinshawiJuz30Repository.getPreviousVerse(appCtx, state.currentAyah) ?: return
    val autoStartMode = if (autoPlay) state.practiceMode else null
    selectAyahAndTurn(
      context = appCtx,
      suraAyah = prevVerse.suraAyah,
      turnIndex = 0,
      autoStartMode = autoStartMode,
      preserveRepetitionProgress = false
    )
  }

  fun advanceToNextTurnOrAyah(
    context: Context,
    autoPlay: Boolean = false,
    autoStartMode: KidsPracticeMode? = if (autoPlay) _uiState.value.practiceMode else null
  ) {
    val appCtx = context.applicationContext
    val state = _uiState.value
    val verse = MinshawiJuz30Repository.getVerse(appCtx, state.currentAyah) ?: return

    // In Mode B (Recite & Reveal), navigation moves whole Ayah by Ayah
    if (state.practiceMode != KidsPracticeMode.RECITE_AND_REVEAL &&
      state.currentTurnIndex + 1 < verse.turns.size
    ) {
      selectAyahAndTurn(
        context = appCtx,
        suraAyah = state.currentAyah,
        turnIndex = state.currentTurnIndex + 1,
        autoStartMode = autoStartMode,
        preserveRepetitionProgress = true
      )
    } else {
      val nextVerse = MinshawiJuz30Repository.getNextVerse(appCtx, state.currentAyah)
      if (nextVerse != null) {
        selectAyahAndTurn(
          context = appCtx,
          suraAyah = nextVerse.suraAyah,
          turnIndex = 0,
          autoStartMode = autoStartMode
        )
      } else {
        updateState { it.copy(isAutoTransitionPending = false) }
      }
    }
  }

  fun goToPreviousTurnOrAyah(context: Context, autoPlay: Boolean = false) {
    val appCtx = context.applicationContext
    val state = _uiState.value
    val autoStartMode = if (autoPlay) state.practiceMode else null
    if (state.practiceMode != KidsPracticeMode.RECITE_AND_REVEAL && state.currentTurnIndex > 0) {
      selectAyahAndTurn(
        context = appCtx,
        suraAyah = state.currentAyah,
        turnIndex = state.currentTurnIndex - 1,
        autoStartMode = autoStartMode,
        preserveRepetitionProgress = true
      )
    } else {
      val prevVerse = MinshawiJuz30Repository.getPreviousVerse(appCtx, state.currentAyah)
      if (prevVerse != null) {
        val targetTurn = if (state.practiceMode == KidsPracticeMode.RECITE_AND_REVEAL) {
          0
        } else {
          (prevVerse.turns.size - 1).coerceAtLeast(0)
        }
        selectAyahAndTurn(
          context = appCtx,
          suraAyah = prevVerse.suraAyah,
          turnIndex = targetTurn,
          autoStartMode = autoStartMode
        )
      }
    }
  }

  fun setAutoNextEnabled(context: Context, enabled: Boolean) {
    if (!enabled && _uiState.value.stage == ClassroomStage.TURN_PASSED) {
      autoNextJob?.cancel()
    }
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_AUTO_NEXT, enabled)
      .apply()
    updateState {
      it.copy(
        autoNextEnabled = enabled,
        isAutoTransitionPending = if (!enabled && it.stage == ClassroomStage.TURN_PASSED) false else it.isAutoTransitionPending
      )
    }
  }

  fun toggleDockPosition(context: Context) {
    val nextTop = !_uiState.value.isDockedTop
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_DOCKED_TOP, nextTop)
      .apply()
    updateState { it.copy(isDockedTop = nextTop) }
  }

  fun setMinimized(minimized: Boolean) {
    updateState { it.copy(isMinimized = minimized) }
  }

  fun onRecordAudioPermissionResult(context: Context, granted: Boolean) {
    if (granted) {
      when (_uiState.value.practiceMode) {
        KidsPracticeMode.RECITE_AND_REVEAL -> startReciteAndRevealStreaming(context)
        else -> if (isEnabled() || _uiState.value.ayahWords.isNotEmpty()) {
          beginStage3ChildRecitation(context, mode = _uiState.value.practiceMode)
        }
      }
    }
  }

  private fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
  }

  private fun buildInitialTurnWordGrades(turn: TurnSpec?): Map<Int, WordGrade> {
    if (turn == null) return emptyMap()
    val map = LinkedHashMap<Int, WordGrade>(turn.uthmaniWords.size)
    for (i in turn.uthmaniWords.indices) {
      val mushafPos = turn.wordStartIdx + i + 1
      val uthmani = turn.uthmaniWords[i]
      val norm = turn.normalizedWords.getOrElse(i) { uthmani }
      map[mushafPos] = WordGrade(
        wordIndexInTurn = i,
        mushafWordPosition = mushafPos,
        uthmaniWord = uthmani,
        normalizedWord = norm,
        status = WordGradeStatus.PENDING,
        tokenConfidence = 0f
      )
    }
    return map
  }
}
