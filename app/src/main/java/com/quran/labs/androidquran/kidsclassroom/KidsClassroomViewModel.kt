package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quran.data.model.SuraAyah
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coordinates the 60 fps Tajweed-driven lip-sync timeline, [TeacherAvatarState], [RewardsDataStore],
 * Slow-Mo (`0.75×`) demonstration mode, live streaming match chimes, and Surah Adventure Map navigation
 * on top of [KidsClassroomManager].
 */
class KidsClassroomViewModel : ViewModel() {

  private val _avatarState = MutableStateFlow(TeacherAvatarState())
  val avatarState: StateFlow<TeacherAvatarState> = _avatarState.asStateFlow()

  private val _audioClueState = MutableStateFlow(AudioTajweedClueSample())
  val audioClueState: StateFlow<AudioTajweedClueSample> = _audioClueState.asStateFlow()

  private val _tajweedAnalysis = MutableStateFlow(
    TajweedAyahAnalysis(emptyList(), emptyList(), androidx.compose.ui.text.AnnotatedString(""))
  )
  val tajweedAnalysis: StateFlow<TajweedAyahAnalysis> = _tajweedAnalysis.asStateFlow()

  private val _showSurahMap = MutableStateFlow(false)
  val showSurahMap: StateFlow<Boolean> = _showSurahMap.asStateFlow()

  private val _focusedSlowMoWordIndex = MutableStateFlow<Int?>(null)
  val focusedSlowMoWordIndex: StateFlow<Int?> = _focusedSlowMoWordIndex.asStateFlow()

  val classroomState: StateFlow<KidsClassroomUiState> = KidsClassroomManager.uiState
  val rewardsState: StateFlow<KidsRewardsState> = RewardsDataStore.state

  private val audioTajweedAnalyzer = AudioWaveformTajweedAnalyzer()
  private var plannedKeyframes: List<TajweedVisemeKeyframe> = emptyList()
  private var plannedTurnKey: String = ""
  private var lastMatchedCountForChime: Int = 0
  private var wordMatchedSparkleUntilMs: Long = 0L
  private var lastLoopTickMs: Long = 0L
  private var lipSyncLoopJob: Job? = null

  fun startSession(context: Context) {
    val appCtx = context.applicationContext
    RewardsDataStore.initialize(appCtx)
    KidsClassroomManager.ensureKidsTabInitialized(appCtx)
    start60FpsLipSyncLoop()
  }

  fun openSurahMap() {
    _showSurahMap.value = true
  }

  fun closeSurahMap() {
    _showSurahMap.value = false
  }

  fun selectSurahFromMap(context: Context, surahNumber: Int) {
    _showSurahMap.value = false
    KidsClassroomManager.selectAyahAndTurn(
      context = context,
      suraAyah = SuraAyah(surahNumber, 1),
      turnIndex = 0,
      autoPlayLoop = false
    )
  }

  fun toggleSlowMo(context: Context) {
    val next = !classroomState.value.isSlowMoEnabled
    KidsClassroomManager.setSlowMoEnabled(context, next)
    _avatarState.value = _avatarState.value.copy(isSlowMo = next)
    _audioClueState.value = _audioClueState.value.copy(isSlowMo = next)
  }

  fun toggleAudioCluesMode(context: Context) {
    val next = !classroomState.value.useAudioCluesMode
    audioTajweedAnalyzer.reset()
    KidsClassroomManager.setUseAudioCluesMode(context, next)
    _audioClueState.value = AudioTajweedClueSample(isSlowMo = classroomState.value.isSlowMoEnabled)
  }

  /**
   * Replays a single tapped word (e.g., a missed word from final grading) at `0.75×` Slow-Mo
   * so Ustadh Noor demonstrates its exact Tajweed mouth movement clearly.
   */
  fun replayWordInSlowMo(context: Context, wordIndexInTurn: Int) {
    val state = classroomState.value
    val turn = state.currentTurn ?: return
    ensureKeyframesPlanned(state, turn)
    val (wordStartMs, wordEndMs) = TajweedVisemePlanner.wordTimeWindowMs(
      keyframes = plannedKeyframes,
      wordIndex = wordIndexInTurn,
      fallbackStartMs = turn.effectiveQariStartMs,
      fallbackEndMs = turn.effectiveQariEndMs
    )
    _focusedSlowMoWordIndex.value = wordIndexInTurn
    KidsClassroomManager.setSlowMoEnabled(context, true)
    KidsClassroomManager.replayTimeWindowInSlowMo(
      context = context,
      startMs = wordStartMs,
      endMs = wordEndMs
    ) {
      _focusedSlowMoWordIndex.value = null
    }
  }

  private var silenceStartMs: Long = 0L

  private fun start60FpsLipSyncLoop() {
    if (lipSyncLoopJob?.isActive == true) return
    lipSyncLoopJob = viewModelScope.launch {
      while (isActive) {
        val state = classroomState.value
        val turn = state.currentTurn
        if (turn != null) {
          ensureKeyframesPlanned(state, turn)
        }

        // Trigger cheerful word-matched nod + gentle chime whenever streaming ASR matches a new word
        if (state.stage == ClassroomStage.CHILD_RECITING || state.stage == ClassroomStage.TASMEE_STREAMING) {
          val liveCount = maxOf(state.streamingMatchedWordCount, state.revealedCount)
          if (liveCount > lastMatchedCountForChime) {
            lastMatchedCountForChime = liveCount
            wordMatchedSparkleUntilMs = System.currentTimeMillis() + 650L
            playSoftMatchChime()
          }
        } else {
          lastMatchedCountForChime = 0
        }

        val nowMs = System.currentTimeMillis()
        val deltaMs = if (lastLoopTickMs == 0L) 16L else (nowMs - lastLoopTickMs).coerceIn(8L, 80L)
        lastLoopTickMs = nowMs
        val isWordSparkling = nowMs < wordMatchedSparkleUntilMs

        val isPlayingAudio = KidsClassroomManager.isPlayingSpeakerAudio()
        val isRecitingStage = state.stage == ClassroomStage.QARI_RECITING ||
          state.stage == ClassroomStage.KIDS_REPEATING ||
          _focusedSlowMoWordIndex.value != null
        val isSlowMoNow = state.isSlowMoEnabled || _focusedSlowMoWordIndex.value != null

        val mood = when {
          state.stage == ClassroomStage.TURN_PASSED -> TeacherMood.CELEBRATING
          state.stage == ClassroomStage.TURN_RETRY -> TeacherMood.ENCOURAGING
          isWordSparkling -> TeacherMood.WORD_MATCHED
          state.stage == ClassroomStage.CHILD_RECITING || state.stage == ClassroomStage.TASMEE_STREAMING ->
            TeacherMood.LISTENING
          isRecitingStage && isPlayingAudio -> TeacherMood.RECITING
          else -> TeacherMood.GREETING
        }

        if (state.useAudioCluesMode) {
          if (isRecitingStage && isPlayingAudio && turn != null) {
            val acousticFrame = KidsClassroomManager.latestAcousticFrame()
            val rawPosMs = KidsClassroomManager.latestPlaybackPositionMs()
            val approxProgress = if (state.stage == ClassroomStage.KIDS_REPEATING) {
              val kidsSpan = (turn.effectiveKidsEndMs - turn.effectiveKidsStartMs).coerceAtLeast(250L)
              ((rawPosMs - turn.effectiveKidsStartMs).toFloat() / kidsSpan.toFloat()).coerceIn(0f, 1f)
            } else {
              val qariSpan = (turn.effectiveQariEndMs - turn.effectiveQariStartMs).coerceAtLeast(250L)
              ((rawPosMs - turn.effectiveQariStartMs).toFloat() / qariSpan.toFloat()).coerceIn(0f, 1f)
            }
            _audioClueState.value = audioTajweedAnalyzer.analyzeStep(
              frame = acousticFrame,
              deltaMs = deltaMs,
              verseProgress = approxProgress,
              analysis = _tajweedAnalysis.value,
              mood = mood,
              isSlowMo = isSlowMoNow,
              starsEarned = state.lastStarsEarned
            )
          } else {
            audioTajweedAnalyzer.reset()
            _audioClueState.value = AudioTajweedClueSample(
              mood = mood,
              isSlowMo = isSlowMoNow,
              starsEarned = state.lastStarsEarned
            )
          }
        } else {
          val sample = if (isRecitingStage && isPlayingAudio && plannedKeyframes.isNotEmpty() && turn != null) {
            val rawPosMs = KidsClassroomManager.latestPlaybackPositionMs()
            val rms = KidsClassroomManager.latestAudioRms()
            // Map Stage 2 kids repeat timeline onto the same Tajweed keyframes so Ustadh Noor mouths along gently
            val mappedPosMs = if (state.stage == ClassroomStage.KIDS_REPEATING) {
              val kidsSpan = (turn.effectiveKidsEndMs - turn.effectiveKidsStartMs).coerceAtLeast(250L)
              val frac = ((rawPosMs - turn.effectiveKidsStartMs).toFloat() / kidsSpan.toFloat()).coerceIn(0f, 1f)
              val qariSpan = (turn.effectiveQariEndMs - turn.effectiveQariStartMs).coerceAtLeast(250L)
              turn.effectiveQariStartMs + (frac * qariSpan).toLong()
            } else {
              rawPosMs
            }

            val silenceDurationMs = if (rms < TajweedVisemePlanner.SILENCE_RMS_THRESHOLD) {
              if (silenceStartMs == 0L) silenceStartMs = nowMs
              (nowMs - silenceStartMs).coerceAtLeast(0L)
            } else {
              silenceStartMs = 0L
              0L
            }

            TajweedVisemePlanner.sampleAt(
              keyframes = plannedKeyframes,
              positionMs = mappedPosMs,
              rmsEnvelope = rms,
              silenceDurationMs = silenceDurationMs
            )
          } else {
            silenceStartMs = 0L
            VisemeSample(viseme = ArabicViseme.REST_CLOSED, mouthOpenness = 0f)
          }

          _avatarState.value = TeacherAvatarState(
            viseme = sample.viseme,
            geometry = sample.geometry,
            mouthOpenness = sample.mouthOpenness,
            isVoiceActive = sample.isVoiceActive,
            mood = mood,
            activeRuleType = sample.ruleType,
            tajweedColor = sample.tajweedColor,
            harakahHint = sample.harakahHint,
            maddProgress = sample.maddProgress,
            isQalqalahBounce = sample.isQalqalahBounce,
            isSlowMo = isSlowMoNow,
            starsEarned = state.lastStarsEarned
          )
        }

        delay(16L) // ~60 fps
      }
    }
  }

  private fun ensureKeyframesPlanned(state: KidsClassroomUiState, turn: TurnSpec) {
    val words = if (turn.uthmaniWords.isNotEmpty()) turn.uthmaniWords else state.ayahWords
    val key = "${state.currentAyah.sura}:${state.currentAyah.ayah}:${turn.turnIndex}:${words.size}"
    if (key == plannedTurnKey && plannedKeyframes.isNotEmpty()) return
    plannedTurnKey = key
    val analysis = TajweedSpanAdapter.analyzeWords(words)
    _tajweedAnalysis.value = analysis
    plannedKeyframes = TajweedVisemePlanner.planKeyframes(
      tokens = analysis.allTokens,
      startMs = turn.effectiveQariStartMs,
      endMs = turn.effectiveQariEndMs
    )
  }

  private fun playSoftMatchChime() {
    try {
      val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 42)
      tg.startTone(ToneGenerator.TONE_PROP_ACK, 85)
      viewModelScope.launch {
        delay(160L)
        runCatching { tg.release() }
      }
    } catch (_: Throwable) {
      // Safe no-op on emulators/JVM without audio hardware
    }
  }

  override fun onCleared() {
    lipSyncLoopJob?.cancel()
    super.onCleared()
  }
}
