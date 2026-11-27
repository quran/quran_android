package com.quran.labs.androidquran.memorization.tasmee

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import androidx.core.content.ContextCompat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.kidsclassroom.AssetDownloadProgress
import com.quran.labs.androidquran.kidsclassroom.FastConformerAsrEngine
import com.quran.labs.androidquran.kidsclassroom.KidsAsrAssetDownloader
import com.quran.labs.androidquran.kidsclassroom.KidsClassroomManager
import com.quran.labs.androidquran.kidsclassroom.NeMoMelPreprocessor
import com.quran.labs.androidquran.kidsclassroom.QuranRecitationGrader
import com.quran.labs.androidquran.memorization.QuranMemorizationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class TasmeeStage {
  INACTIVE,
  READY,
  DOWNLOADING_MODELS,
  WARMING_UP,
  LISTENING,
  ERROR
}

data class DetectedTasmeeWord(
  val suraAyah: SuraAyah,
  val glyphPosition: Int
)

data class TasmeeUiState(
  val isEnabled: Boolean = false,
  val stage: TasmeeStage = TasmeeStage.INACTIVE,
  val activePage: Int = 1,
  val activeAyah: SuraAyah? = null,
  val revealedWordsInAyah: Int = 0,
  val totalWordsInAyah: Int = 0,
  val skippedWordsCount: Int = 0,
  val micLevel: Float = 0f,
  val isVadActive: Boolean = false,
  val waveformBars: List<Float> = VadWaveformMath.emptyBars(),
  val lastInferenceMs: Long = 0L,
  val downloadProgress: AssetDownloadProgress = AssetDownloadProgress(),
  val errorMessage: String? = null,
  val latestDetectedWord: DetectedTasmeeWord? = null,
  val detectedWordsVersion: Long = 0L
)

/**
 * Real-time Streaming **Live Recitation Follow-Along ("Tasmee' / تسميع") Mode** supporting
 * **Word by Word (`RevealMode.WORD_BY_WORD`)**, **Single Word (`RevealMode.SINGLE_WORD`)**,
 * **Word Window (`RevealMode.WORD_WINDOW`)**, and **Single Word Tajweed (`RevealMode.SINGLE_WORD_TAJWEED`)**
 * powered by `fastconformer_quran_int8.onnx`:
 *
 * - **Sliding Acoustic Buffer**:
 *   - Hop interval: every **`350 ms`** (`5,600` PCM samples at `16,000 Hz`) on `Dispatchers.Default`.
 *   - Active audio window: audio since the current Ayah started up to **`6.0s` (`96,000` samples)**,
 *     sliding to the last **`4.5s..6.0s` (`72,000..96,000` samples)** on multi-line Ayat, prepended
 *     and appended with mandatory **`3,520` zero-padding samples (`220 ms`)**.
 *   - **Trailing Post-Speech Hop Flush**: guarantees one final decode hop after voice activity ends so
 *     words followed by a natural pause are never dropped by trailing silence checks.
 *   - **Window Reset on Ayah Completion**: trims/resets the rolling buffer the instant the last word
 *     of the current Ayah is unlocked so the next Ayah starts completely fresh.
 * - **Monotonic Reveal Algorithm**:
 *   - Uses [TasmeeMonotonicMatcher] (Signal A Phonetic Rasm + mid-word prefix, Signal B BPE posterior
 *     spike `> 0.55`, and Skip-Ahead Recovery `+1..+2` words with subtle hint dots).
 */
object LiveTasmeeManager {

  private const val SAMPLE_RATE = 16000
  private const val HOP_SAMPLES = 5600 // 350 ms at 16 kHz
  private const val SLIDING_BASE_SAMPLES = 72000 // 4.5 seconds at 16 kHz
  private const val MAX_WINDOW_SAMPLES = 96000 // 6.0 seconds cap at 16 kHz
  private const val READ_CHUNK_SAMPLES = 640 // 40 ms chunks
  private const val PAGE_TURN_LATCH_DELAY_MS = 420L

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val _uiState = MutableStateFlow(TasmeeUiState())
  val uiState: StateFlow<TasmeeUiState> = _uiState.asStateFlow()

  @Volatile
  var onRequestMicPermission: (() -> Unit)? = null

  @Volatile
  var onAdvancePageOrLoopRange: ((Int) -> Unit)? = null

  @Volatile
  var onStopPlaybackAudio: (() -> Unit)? = null

  @Volatile
  var onWordsRevealed: (() -> Unit)? = null

  @Volatile
  var onJumpToPage: ((Int) -> Unit)? = null

  private var quranInfoRef: QuranInfo? = null
  private var appContextRef: Context? = null

  private var audioRecord: AudioRecord? = null
  private var captureJob: Job? = null
  private var hopInferenceJob: Job? = null
  private var downloadJob: Job? = null
  private var pageTurnJob: Job? = null
  private var stateObserverStarted = false

  // Rolling acoustic buffer protected by bufferLock
  private val bufferLock = Any()
  private val rollingAyahPcm = FloatArray(MAX_WINDOW_SAMPLES)
  private var rollingSampleCount = 0
  private var samplesSinceLastHop = 0
  private var bufferEpoch = 1L
  private var hasUnevaluatedSpeech = false
  private var speechArrivalCounter = 0L

  // Monotonic cursor state for the active Ayah slice on the active Mushaf page
  @Volatile
  private var trackedPage: Int = -1

  @Volatile
  private var trackedAyah: SuraAyah? = null

  @Volatile
  private var trackedSlice: PageAyahTasmeeSlice? = null

  @Volatile
  private var revealedWordCountInSlice: Int = 0

  @Volatile
  private var minSignalBFrameCursor: Int = 0

  @Volatile
  private var skippedCountInSession: Int = 0

  fun interface Listener {
    fun onTasmeeStateChanged(state: TasmeeUiState)
  }

  private val listeners = java.util.concurrent.CopyOnWriteArraySet<Listener>()

  @JvmStatic
  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  @JvmStatic
  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  // Detected words per Mushaf page so HighlightingImageView can draw luminous detected-word highlights
  private val detectedWordsLock = Any()
  private val detectedWordsByPage = mutableMapOf<Int, LinkedHashSet<DetectedTasmeeWord>>()
  private val latestDetectedWordByPage = mutableMapOf<Int, DetectedTasmeeWord>()
  private var detectedWordsVersionCounter = 0L

  @JvmStatic
  fun isEnabled(): Boolean = _uiState.value.isEnabled

  @JvmStatic
  fun isActivelyListening(): Boolean {
    val st = _uiState.value
    return st.isEnabled && (
      st.stage == TasmeeStage.LISTENING ||
        st.stage == TasmeeStage.WARMING_UP ||
        st.stage == TasmeeStage.DOWNLOADING_MODELS
      )
  }

  @JvmStatic
  fun currentState(): TasmeeUiState = _uiState.value

  @JvmStatic
  fun getDetectedWordsForPage(page: Int): Set<DetectedTasmeeWord> = synchronized(detectedWordsLock) {
    detectedWordsByPage[page]?.toSet() ?: emptySet()
  }

  @JvmStatic
  fun getLatestDetectedWordForPage(page: Int): DetectedTasmeeWord? = synchronized(detectedWordsLock) {
    latestDetectedWordByPage[page]
  }

  private fun recordDetectedGlyphs(
    page: Int,
    suraAyah: SuraAyah,
    detectedGlyphPositions: List<Int>
  ): Pair<DetectedTasmeeWord?, Long> {
    if (detectedGlyphPositions.isEmpty()) {
      return synchronized(detectedWordsLock) {
        latestDetectedWordByPage[page] to detectedWordsVersionCounter
      }
    }
    return synchronized(detectedWordsLock) {
      val pageSet = detectedWordsByPage.getOrPut(page) { LinkedHashSet() }
      var latest: DetectedTasmeeWord? = null
      for (pos in detectedGlyphPositions) {
        val entry = DetectedTasmeeWord(suraAyah, pos)
        pageSet.add(entry)
        latest = entry
      }
      if (latest != null) {
        latestDetectedWordByPage[page] = latest
      }
      detectedWordsVersionCounter += 1L
      latest to detectedWordsVersionCounter
    }
  }

  private fun clearDetectedWordsForPage(page: Int) {
    synchronized(detectedWordsLock) {
      detectedWordsByPage.remove(page)
      latestDetectedWordByPage.remove(page)
      detectedWordsVersionCounter += 1L
    }
  }

  private fun clearDetectedWordsForAyah(page: Int, suraAyah: SuraAyah) {
    synchronized(detectedWordsLock) {
      val pageSet = detectedWordsByPage[page]
      pageSet?.removeAll { it.suraAyah == suraAyah }
      if (latestDetectedWordByPage[page]?.suraAyah == suraAyah) {
        val fallback = pageSet?.lastOrNull()
        if (fallback != null) {
          latestDetectedWordByPage[page] = fallback
        } else {
          latestDetectedWordByPage.remove(page)
        }
      }
      detectedWordsVersionCounter += 1L
    }
  }

  private fun clearAllDetectedWords() {
    synchronized(detectedWordsLock) {
      detectedWordsByPage.clear()
      latestDetectedWordByPage.clear()
      detectedWordsVersionCounter += 1L
    }
  }

  private fun ensureMemorizationObserverStarted() {
    if (stateObserverStarted) return
    stateObserverStarted = true
    scope.launch {
      QuranMemorizationManager.uiState.collect { memState ->
        if (!_uiState.value.isEnabled) return@collect

        // If the user explicitly exits hidden memorization mode, stop Live Tasmee' cleanly
        if (!memState.isHiddenModeEnabled) {
          stopTasmee()
          return@collect
        }

        // Sync if user swiped to another page, glyphs just registered, or tapped Reset
        val ctx = appContextRef ?: return@collect
        if (memState.currentPage != trackedPage) {
          syncCursorToPageAndFirstUnrevealedAyah(ctx, memState.currentPage, resetAudio = true)
        } else {
          val activeAyah = trackedAyah
          val slice = trackedSlice
          if (activeAyah == null || slice == null) {
            syncCursorToPageAndFirstUnrevealedAyah(ctx, memState.currentPage, resetAudio = false)
          } else {
            val currentPageGlyphs = QuranMemorizationManager.getWordPositionsForAyah(
              memState.currentPage,
              activeAyah
            )
            val sliceGlyphCount = slice.glyphPositionsByWordIdx.sumOf { it.size }
            val vocabLoadedNow = FastConformerAsrEngine.peekVocab() != null
            val sliceMissingTokenIds = vocabLoadedNow &&
              slice.wordTokenIds.all { it.isEmpty() } &&
              slice.wordCount > 0
            if (currentPageGlyphs.isNotEmpty() &&
              (currentPageGlyphs.size != sliceGlyphCount || sliceMissingTokenIds)
            ) {
              syncCursorToPageAndFirstUnrevealedAyah(ctx, memState.currentPage, resetAudio = false)
              return@collect
            }

            val revealedGlyphs = QuranMemorizationManager.getRevealedWordPositionsForAyah(
              memState.currentPage,
              activeAyah
            )
            // Detect global Reset button press
            if (memState.pageProgress.revealedWords == 0 && revealedWordCountInSlice > 0) {
              clearDetectedWordsForPage(memState.currentPage)
              syncCursorToPageAndFirstUnrevealedAyah(ctx, memState.currentPage, resetAudio = true)
            } else {
              // Sync monotonically forward if user tapped the screen to reveal a forgotten word
              val manualCount = countRevealedSliceWordsFromGlyphs(slice, revealedGlyphs)
              if (manualCount > revealedWordCountInSlice) {
                revealedWordCountInSlice = manualCount
                if (manualCount >= slice.wordCount) {
                  handleActiveAyahSliceCompleted(ctx, memState.currentPage)
                } else {
                  updateUiState {
                    it.copy(
                      revealedWordsInAyah = revealedWordCountInSlice,
                      totalWordsInAyah = slice.wordCount
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  fun toggleTasmee(
    context: Context,
    currentPage: Int,
    quranInfo: QuranInfo
  ) {
    if (_uiState.value.isEnabled) {
      stopTasmee()
    } else {
      prepareTasmeeReady(context, currentPage, quranInfo)
    }
  }

  /**
   * Opens Live Recitation ("Recite & Reveal / Tasmee'") in an armed [TasmeeStage.READY] state
   * without opening the microphone yet — the user presses the mic symbol to start the session.
   */
  fun prepareTasmeeReady(
    context: Context,
    currentPage: Int,
    quranInfo: QuranInfo
  ) {
    val appCtx = context.applicationContext
    appContextRef = appCtx
    quranInfoRef = quranInfo
    ensureMemorizationObserverStarted()

    // Ensure mutual exclusion with Children Learning Mode & Qari MP3 playback
    if (KidsClassroomManager.isEnabled()) {
      KidsClassroomManager.disableClassroomMode(appCtx)
    }
    onStopPlaybackAudio?.invoke()

    // Stop any previous capture/inference before entering armed READY state
    downloadJob?.cancel()
    downloadJob = null
    pageTurnJob?.cancel()
    pageTurnJob = null
    hopInferenceJob?.cancel()
    hopInferenceJob = null
    stopAudioCaptureInternal()
    resetRollingAudioBuffer()

    // Activate Memorization Hidden Mode supporting Word-by-Word (default), Single Word, or Word Window
    QuranMemorizationManager.startTasmeeSession(currentPage, quranInfo, appCtx)
    val effectivePage = QuranMemorizationManager.uiState.value.currentPage

    updateUiState { prev ->
      TasmeeUiState(
        isEnabled = true,
        stage = TasmeeStage.READY,
        activePage = effectivePage,
        latestDetectedWord = getLatestDetectedWordForPage(effectivePage),
        detectedWordsVersion = prev.detectedWordsVersion
      )
    }
    syncCursorToPageAndFirstUnrevealedAyah(appCtx, effectivePage, resetAudio = true)
  }

  /**
   * Handles a tap on the centered Live Recitation microphone symbol:
   * - In [TasmeeStage.READY] / [TasmeeStage.INACTIVE]: begins active mic listening.
   * - In [TasmeeStage.LISTENING] / [TasmeeStage.WARMING_UP] / [TasmeeStage.DOWNLOADING_MODELS]:
   *   pauses cleanly back to [TasmeeStage.READY] while preserving detected-word highlights.
   * - In [TasmeeStage.ERROR]: retries downloading/starting the ASR engine.
   */
  fun onMicButtonTapped(
    context: Context,
    currentPage: Int,
    quranInfo: QuranInfo
  ) {
    when (_uiState.value.stage) {
      TasmeeStage.READY, TasmeeStage.INACTIVE -> startTasmee(context, currentPage, quranInfo)
      TasmeeStage.ERROR -> retryDownloadIfNeeded(context)
      TasmeeStage.LISTENING, TasmeeStage.WARMING_UP, TasmeeStage.DOWNLOADING_MODELS -> {
        pauseListeningToReady()
      }
    }
  }

  /**
   * Pauses active microphone capture and inference back to [TasmeeStage.READY] while keeping the
   * Live Recitation bar visible and preserving highlighted detected words on the Mushaf page.
   */
  fun pauseListeningToReady() {
    downloadJob?.cancel()
    downloadJob = null
    pageTurnJob?.cancel()
    pageTurnJob = null
    hopInferenceJob?.cancel()
    hopInferenceJob = null
    stopAudioCaptureInternal()
    resetRollingAudioBuffer()

    if (!_uiState.value.isEnabled) return
    updateUiState {
      it.copy(
        stage = TasmeeStage.READY,
        micLevel = 0f,
        isVadActive = false,
        waveformBars = VadWaveformMath.emptyBars(),
        errorMessage = null
      )
    }
  }

  fun startTasmee(
    context: Context,
    currentPage: Int,
    quranInfo: QuranInfo
  ) {
    val appCtx = context.applicationContext
    appContextRef = appCtx
    quranInfoRef = quranInfo
    ensureMemorizationObserverStarted()

    // Ensure mutual exclusion with Children Learning Mode & Qari MP3 playback
    if (KidsClassroomManager.isEnabled()) {
      KidsClassroomManager.disableClassroomMode(appCtx)
    }
    onStopPlaybackAudio?.invoke()

    // Activate Memorization Hidden Mode supporting Word-by-Word (default), Single Word, or Word Window!
    QuranMemorizationManager.startTasmeeSession(currentPage, quranInfo, appCtx)
    val effectivePage = QuranMemorizationManager.uiState.value.currentPage

    if (!hasRecordAudioPermission(appCtx)) {
      updateUiState { prev ->
        TasmeeUiState(
          isEnabled = true,
          stage = TasmeeStage.READY,
          activePage = effectivePage,
          activeAyah = prev.activeAyah,
          revealedWordsInAyah = prev.revealedWordsInAyah,
          totalWordsInAyah = prev.totalWordsInAyah,
          latestDetectedWord = getLatestDetectedWordForPage(effectivePage),
          detectedWordsVersion = prev.detectedWordsVersion
        )
      }
      onRequestMicPermission?.invoke()
      return
    }

    skippedCountInSession = 0
    val allAssetsReady = KidsAsrAssetDownloader.areAllAssetsReady(appCtx)
    val coreReadyForJuz30 = effectivePage in 582..604 &&
      KidsAsrAssetDownloader.areCoreEngineAssetsReady(appCtx)
    val canStartImmediately = allAssetsReady || coreReadyForJuz30

    updateUiState { prev ->
      TasmeeUiState(
        isEnabled = true,
        stage = if (canStartImmediately) TasmeeStage.WARMING_UP else TasmeeStage.DOWNLOADING_MODELS,
        activePage = effectivePage,
        activeAyah = prev.activeAyah,
        revealedWordsInAyah = prev.revealedWordsInAyah,
        totalWordsInAyah = prev.totalWordsInAyah,
        downloadProgress = AssetDownloadProgress(isReady = canStartImmediately),
        latestDetectedWord = getLatestDetectedWordForPage(effectivePage),
        detectedWordsVersion = prev.detectedWordsVersion
      )
    }

    if (canStartImmediately) {
      prepareEngineAndBeginStreaming(appCtx, effectivePage)
      if (!allAssetsReady) {
        // Quietly complete full 114-Surah teacher JSON download in background for non-Juz-30 pages
        downloadJob?.cancel()
        downloadJob = scope.launch {
          KidsAsrAssetDownloader.ensureAllAssetsDownloaded(appCtx) {}
          QuranAsrReferenceRepository.ensureLoaded(appCtx)
        }
      }
    } else {
      startModelDownloadThenStream(appCtx, effectivePage)
    }
  }

  fun onRecordAudioPermissionResult(
    context: Context,
    granted: Boolean,
    currentPage: Int,
    quranInfo: QuranInfo
  ) {
    if (granted) {
      startTasmee(context, currentPage, quranInfo)
    } else if (_uiState.value.isEnabled) {
      pauseListeningToReady()
    } else {
      stopTasmee()
    }
  }

  fun stopTasmee() {
    downloadJob?.cancel()
    downloadJob = null
    pageTurnJob?.cancel()
    pageTurnJob = null
    hopInferenceJob?.cancel()
    hopInferenceJob = null
    stopAudioCaptureInternal()
    resetRollingAudioBuffer()
    clearAllDetectedWords()
    trackedPage = -1
    trackedAyah = null
    trackedSlice = null
    revealedWordCountInSlice = 0
    minSignalBFrameCursor = 0

    updateUiState {
      TasmeeUiState(
        isEnabled = false,
        stage = TasmeeStage.INACTIVE,
        detectedWordsVersion = detectedWordsVersionCounter
      )
    }
  }

  private fun startModelDownloadThenStream(appCtx: Context, initialPage: Int) {
    downloadJob?.cancel()
    downloadJob = scope.launch {
      val ok = KidsAsrAssetDownloader.ensureAllAssetsDownloaded(appCtx) { progress ->
        scope.launch {
          if (_uiState.value.isEnabled) {
            updateUiState {
              it.copy(
                stage = if (progress.errorMessage != null) TasmeeStage.ERROR else TasmeeStage.DOWNLOADING_MODELS,
                downloadProgress = progress,
                errorMessage = progress.errorMessage
              )
            }
          }
        }
      }
      if (!ok || !_uiState.value.isEnabled) {
        return@launch
      }
      val latestPage = QuranMemorizationManager.uiState.value.currentPage.takeIf { it > 0 } ?: initialPage
      prepareEngineAndBeginStreaming(appCtx, latestPage)
    }
  }

  fun retryDownloadIfNeeded(context: Context) {
    if (!_uiState.value.isEnabled) return
    val appCtx = context.applicationContext
    val page = QuranMemorizationManager.uiState.value.currentPage.coerceAtLeast(1)
    startModelDownloadThenStream(appCtx, page)
  }

  /**
   * Skips directly to the next Ayah on the active Mushaf page (revealing remaining words of the
   * current Ayah) or advances to the next page when already on the page's final Ayah.
   */
  fun skipToNextAyah(context: Context) {
    if (!_uiState.value.isEnabled) return
    val appCtx = context.applicationContext
    val page = trackedPage.takeIf { it > 0 }
      ?: QuranMemorizationManager.uiState.value.currentPage.coerceIn(1, 604)
    pageTurnJob?.cancel()
    resetRollingAudioBuffer()
    minSignalBFrameCursor = 0

    QuranMemorizationManager.ensurePageInitialized(page, quranInfoRef, appCtx)
    val candidates = QuranMemorizationManager.getCandidateAyahsOnPage(page)
    val active = trackedAyah ?: candidates.firstOrNull { !QuranMemorizationManager.isAyahFullyRevealed(page, it) }

    if (active != null) {
      clearDetectedWordsForAyah(page, active)
      val glyphs = QuranMemorizationManager.getWordPositionsForAyah(page, active)
      if (glyphs.isNotEmpty()) {
        QuranMemorizationManager.revealWordRangeForAyah(
          suraAyah = active,
          wordPositions = glyphs,
          context = appCtx
        )
      }
      updateUiState {
        it.copy(
          latestDetectedWord = getLatestDetectedWordForPage(page),
          detectedWordsVersion = detectedWordsVersionCounter
        )
      }
      val nextUnrevealed = candidates.firstOrNull { !QuranMemorizationManager.isAyahFullyRevealed(page, it) }
      onWordsRevealed?.invoke()
      if (nextUnrevealed != null) {
        syncCursorToPageAndFirstUnrevealedAyah(appCtx, page, resetAudio = true)
        return
      }
    }

    if (onAdvancePageOrLoopRange != null) {
      onAdvancePageOrLoopRange?.invoke(page)
    } else if (page < 604) {
      onJumpToPage?.invoke(page + 1)
    }
  }

  /**
   * Steps back to the previous Ayah on the active Mushaf page (re-hiding it for recitation),
   * or restarts the first Ayah on the page, or jumps to the previous page.
   */
  fun goToPreviousAyah(context: Context) {
    if (!_uiState.value.isEnabled) return
    val appCtx = context.applicationContext
    val page = trackedPage.takeIf { it > 0 }
      ?: QuranMemorizationManager.uiState.value.currentPage.coerceIn(1, 604)
    pageTurnJob?.cancel()
    resetRollingAudioBuffer()
    minSignalBFrameCursor = 0

    QuranMemorizationManager.ensurePageInitialized(page, quranInfoRef, appCtx)
    val candidates = QuranMemorizationManager.getCandidateAyahsOnPage(page)
    val active = trackedAyah
    val activeIdx = if (active != null) candidates.indexOf(active) else candidates.size

    if (activeIdx > 0 && candidates.isNotEmpty()) {
      if (active != null) {
        val activeGlyphs = QuranMemorizationManager.getWordPositionsForAyah(page, active)
        if (activeGlyphs.isNotEmpty()) {
          QuranMemorizationManager.hideWordRangeForAyah(active, activeGlyphs)
        }
        clearDetectedWordsForAyah(page, active)
      }
      val prevAyah = candidates[(activeIdx - 1).coerceIn(0, candidates.lastIndex)]
      val prevGlyphs = QuranMemorizationManager.getWordPositionsForAyah(page, prevAyah)
      if (prevGlyphs.isNotEmpty()) {
        QuranMemorizationManager.hideWordRangeForAyah(prevAyah, prevGlyphs)
      }
      clearDetectedWordsForAyah(page, prevAyah)
      syncCursorToPageAndFirstUnrevealedAyah(appCtx, page, resetAudio = true)
      onWordsRevealed?.invoke()
      return
    }

    if (active != null && revealedWordCountInSlice > 0) {
      val activeGlyphs = QuranMemorizationManager.getWordPositionsForAyah(page, active)
      if (activeGlyphs.isNotEmpty()) {
        QuranMemorizationManager.hideWordRangeForAyah(active, activeGlyphs)
      }
      clearDetectedWordsForAyah(page, active)
      syncCursorToPageAndFirstUnrevealedAyah(appCtx, page, resetAudio = true)
      onWordsRevealed?.invoke()
      return
    }

    if (page > 1) {
      onJumpToPage?.invoke(page - 1)
    }
  }

  private fun prepareEngineAndBeginStreaming(appCtx: Context, page: Int) {
    scope.launch {
      updateUiState {
        it.copy(
          stage = TasmeeStage.WARMING_UP,
          errorMessage = null
        )
      }
      val engineReady = FastConformerAsrEngine.ensureInitialized(appCtx)
      val quranLoaded = QuranAsrReferenceRepository.ensureLoaded(appCtx)
      if (!_uiState.value.isEnabled) return@launch

      if (!engineReady || (!quranLoaded && page !in 582..604)) {
        updateUiState {
          it.copy(
            stage = TasmeeStage.ERROR,
            errorMessage = "ASR model initialization failed"
          )
        }
        return@launch
      }

      val currentPageNow = QuranMemorizationManager.uiState.value.currentPage.takeIf { it > 0 } ?: page
      syncCursorToPageAndFirstUnrevealedAyah(appCtx, currentPageNow, resetAudio = true)
      startAudioCaptureLoop(appCtx)
    }
  }

  private fun syncCursorToPageAndFirstUnrevealedAyah(
    context: Context,
    page: Int,
    resetAudio: Boolean
  ) {
    QuranMemorizationManager.ensurePageInitialized(page, quranInfoRef, context)
    val candidateAyahs = QuranMemorizationManager.getCandidateAyahsOnPage(page)
    val nextAyah = candidateAyahs.firstOrNull { ayah ->
      !QuranMemorizationManager.isAyahFullyRevealed(page, ayah)
    }

    trackedPage = page
    if (resetAudio) {
      resetRollingAudioBuffer()
    }

    if (nextAyah == null) {
      trackedAyah = null
      trackedSlice = null
      revealedWordCountInSlice = 0
      minSignalBFrameCursor = 0
      updateUiState {
        it.copy(
          activePage = page,
          activeAyah = candidateAyahs.lastOrNull(),
          revealedWordsInAyah = 0,
          totalWordsInAyah = 0
        )
      }
      return
    }

    val vocab = FastConformerAsrEngine.peekVocab()
    val ayahRef = QuranAsrReferenceRepository.getAyahReference(context, nextAyah, vocab)
    val pageGlyphs = QuranMemorizationManager.getWordPositionsForAyah(page, nextAyah)
    val totalAyahGlyphs = QuranMemorizationManager.getWordCountForAyah(nextAyah)

    val slice = if (ayahRef != null) {
      QuranAsrReferenceRepository.buildPageAyahSlice(
        page = page,
        ayahRef = ayahRef,
        pageGlyphPositions = pageGlyphs,
        totalAyahGlyphCount = totalAyahGlyphs
      )
    } else {
      null
    }

    val alreadyRevealedGlyphs = QuranMemorizationManager.getRevealedWordPositionsForAyah(page, nextAyah)
    val initialCount = if (slice != null) {
      countRevealedSliceWordsFromGlyphs(slice, alreadyRevealedGlyphs)
    } else {
      0
    }

    trackedAyah = nextAyah
    trackedSlice = slice
    revealedWordCountInSlice = initialCount
    minSignalBFrameCursor = 0

    updateUiState {
      it.copy(
        activePage = page,
        activeAyah = nextAyah,
        revealedWordsInAyah = initialCount,
        totalWordsInAyah = slice?.wordCount ?: pageGlyphs.size
      )
    }
  }

  private fun countRevealedSliceWordsFromGlyphs(
    slice: PageAyahTasmeeSlice,
    revealedGlyphs: Set<Int>
  ): Int {
    if (revealedGlyphs.isEmpty()) return 0
    var count = 0
    for (wIdx in 0 until slice.wordCount) {
      val glyphs = slice.glyphPositionsByWordIdx[wIdx]
      if (glyphs.isNotEmpty() && glyphs.all { it in revealedGlyphs }) {
        count = wIdx + 1
      } else {
        break
      }
    }
    return count
  }

  @SuppressLint("MissingPermission")
  private fun startAudioCaptureLoop(appCtx: Context) {
    stopAudioCaptureInternal()
    if (!hasRecordAudioPermission(appCtx)) {
      stopTasmee()
      onRequestMicPermission?.invoke()
      return
    }

    val minBufBytes = AudioRecord.getMinBufferSize(
      SAMPLE_RATE,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(SAMPLE_RATE * 2)
    val recordBufBytes = max(minBufBytes * 2, SAMPLE_RATE * 2)

    val record = createBestAudioRecord(recordBufBytes)
    if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
      record?.release()
      updateUiState {
        it.copy(
          stage = TasmeeStage.ERROR,
          errorMessage = "Microphone unavailable"
        )
      }
      return
    }

    try {
      record.startRecording()
    } catch (e: Exception) {
      Timber.e(e, "Failed to start AudioRecord for Live Tasmee'")
      record.release()
      updateUiState {
        it.copy(
          stage = TasmeeStage.ERROR,
          errorMessage = e.localizedMessage ?: "Failed to start microphone"
        )
      }
      return
    }

    audioRecord = record
    updateUiState {
      it.copy(
        stage = TasmeeStage.LISTENING,
        errorMessage = null
      )
    }

    captureJob = scope.launch(Dispatchers.IO) {
      try {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
      } catch (_: Throwable) {
      }

      val shortChunk = ShortArray(READ_CHUNK_SAMPLES)
      val floatChunk = FloatArray(READ_CHUNK_SAMPLES)
      val waveformHistory = FloatArray(VadWaveformMath.DEFAULT_BAR_COUNT)
      val invScale = 1.0f / 32768.0f
      var lastMicUiEmitMs = 0L
      var ambientNoiseRms = 0.006f
      var lastVoiceTimestampMs = 0L

      while (isActive && _uiState.value.isEnabled) {
        val read = try {
          record.read(shortChunk, 0, shortChunk.size)
        } catch (e: Exception) {
          Timber.w(e, "AudioRecord read interrupted in Live Tasmee'")
          break
        }
        if (read <= 0) {
          delay(10)
          continue
        }

        var sumSq = 0.0
        for (i in 0 until read) {
          val v = (shortChunk[i].toInt() * invScale).coerceIn(-1.0f, 1.0f)
          floatChunk[i] = v
          sumSq += (v * v).toDouble()
        }
        val chunkRms = sqrt(sumSq / read.toDouble()).toFloat()

        val nowMs = System.currentTimeMillis()
        val dynamicThreshold = max(
          VadWaveformMath.LIVE_TASMEE_VAD_RMS_THRESHOLD,
          (ambientNoiseRms * 2.3f).coerceAtMost(0.055f)
        )
        val isChunkVoiced = chunkRms >= dynamicThreshold
        if (isChunkVoiced) {
          lastVoiceTimestampMs = nowMs
        } else if (chunkRms < VadWaveformMath.LIVE_TASMEE_VAD_RMS_THRESHOLD) {
          ambientNoiseRms = (0.95f * ambientNoiseRms + 0.05f * chunkRms).coerceIn(0.003f, 0.030f)
        }
        val isVadActiveNow = lastVoiceTimestampMs > 0L &&
          (nowMs - lastVoiceTimestampMs) <= VadWaveformMath.VAD_HANGOVER_MS
        val barsSnapshot = VadWaveformMath.updateBarsFromFloatChunk(
          history = waveformHistory,
          samples = floatChunk,
          sampleCount = read,
          isVadActive = isVadActiveNow
        )

        if (nowMs - lastMicUiEmitMs >= 50L) {
          lastMicUiEmitMs = nowMs
          val visualLevel = ((chunkRms - 0.004f).coerceAtLeast(0f) * 9.0f).coerceIn(0f, 1f)
          scope.launch {
            if (_uiState.value.isEnabled) {
              updateUiState {
                it.copy(
                  micLevel = visualLevel,
                  isVadActive = isVadActiveNow,
                  waveformBars = barsSnapshot
                )
              }
            }
          }
        }

        var shouldTriggerHop = false
        var hopSnapshot: FloatArray? = null
        var snapshotEpoch = 0L
        var forceTrailingDecode = false
        var speechCounterAtHop = 0L

        synchronized(bufferLock) {
          appendSamplesLocked(floatChunk, read)
          if (isChunkVoiced || chunkRms >= NeMoMelPreprocessor.MIN_PEAK_RMS * 2.5f) {
            hasUnevaluatedSpeech = true
            speechArrivalCounter += 1L
          }
          samplesSinceLastHop += read
          if (samplesSinceLastHop >= HOP_SAMPLES) {
            // Trigger every 350 ms (5,600 samples at 16 kHz) when previous pass is idle
            if (hopInferenceJob?.isActive != true && rollingSampleCount >= NeMoMelPreprocessor.MIN_TRIMMED_SAMPLES) {
              samplesSinceLastHop = 0
              hopSnapshot = rollingAyahPcm.copyOfRange(0, rollingSampleCount)
              snapshotEpoch = bufferEpoch
              forceTrailingDecode = hasUnevaluatedSpeech
              speechCounterAtHop = speechArrivalCounter
              shouldTriggerHop = true
            } else if (samplesSinceLastHop >= HOP_SAMPLES * 2) {
              samplesSinceLastHop = HOP_SAMPLES
            }
          }
        }

        if (shouldTriggerHop && hopSnapshot != null) {
          launchHopInferencePass(
            appCtx = appCtx,
            windowPcm = hopSnapshot,
            snapshotEpoch = snapshotEpoch,
            forceTrailingDecode = forceTrailingDecode,
            speechCounterAtHop = speechCounterAtHop
          )
        }
      }
    }
  }

  private fun appendSamplesLocked(samples: FloatArray, count: Int) {
    if (count <= 0) return
    if (count >= MAX_WINDOW_SAMPLES) {
      System.arraycopy(
        samples,
        count - MAX_WINDOW_SAMPLES,
        rollingAyahPcm,
        0,
        MAX_WINDOW_SAMPLES
      )
      rollingSampleCount = MAX_WINDOW_SAMPLES
      return
    }

    if (rollingSampleCount + count <= MAX_WINDOW_SAMPLES) {
      System.arraycopy(samples, 0, rollingAyahPcm, rollingSampleCount, count)
      rollingSampleCount += count
    } else {
      // Once the current Ayah exceeds the 6.0s cap, slide back to the last 4.5s (72,000 samples)
      // so the active window stays smoothly within [4.5s .. 6.0s] with minimal copying!
      val targetKeepPrior = (SLIDING_BASE_SAMPLES - count).coerceAtLeast(0)
      val dropCount = (rollingSampleCount - targetKeepPrior).coerceAtLeast(0)
      val keptCount = rollingSampleCount - dropCount
      if (keptCount > 0) {
        System.arraycopy(rollingAyahPcm, dropCount, rollingAyahPcm, 0, keptCount)
      }
      System.arraycopy(samples, 0, rollingAyahPcm, keptCount, count)
      rollingSampleCount = keptCount + count
    }
  }

  private fun resetRollingAudioBuffer() {
    synchronized(bufferLock) {
      rollingSampleCount = 0
      samplesSinceLastHop = 0
      hasUnevaluatedSpeech = false
      speechArrivalCounter = 0L
      bufferEpoch += 1L
    }
  }

  private fun launchHopInferencePass(
    appCtx: Context,
    windowPcm: FloatArray,
    snapshotEpoch: Long,
    forceTrailingDecode: Boolean,
    speechCounterAtHop: Long
  ) {
    hopInferenceJob = scope.launch(Dispatchers.Default) {
      val activeSlice = trackedSlice ?: run {
        // Attempt lazy slice resolution if page glyphs just registered
        val curPage = QuranMemorizationManager.uiState.value.currentPage
        withContext(Dispatchers.Main.immediate) {
          syncCursorToPageAndFirstUnrevealedAyah(appCtx, curPage, resetAudio = false)
        }
        trackedSlice
      } ?: return@launch

      val currentCountSnapshot = revealedWordCountInSlice
      if (currentCountSnapshot >= activeSlice.wordCount) return@launch

      val startNs = System.nanoTime()
      val melResult = NeMoMelPreprocessor.processRollingTasmeeWindow(
        windowPcm = windowPcm,
        recentHopSamples = if (forceTrailingDecode) windowPcm.size else 8800,
        maxActiveSamples = MAX_WINDOW_SAMPLES,
        minRecentRms = if (forceTrailingDecode) 0f else 0.0045f
      )
      if (melResult !is NeMoMelPreprocessor.PreprocessResult.MelFeatures) {
        if (forceTrailingDecode) {
          synchronized(bufferLock) {
            if (bufferEpoch == snapshotEpoch && speechArrivalCounter == speechCounterAtHop) {
              hasUnevaluatedSpeech = false
            }
          }
        }
        return@launch
      }

      val asrOut = FastConformerAsrEngine.runInference(
        context = appCtx,
        melFeatures = melResult.features,
        numFrames = melResult.numFrames
      ) ?: return@launch

      synchronized(bufferLock) {
        if (bufferEpoch == snapshotEpoch && speechArrivalCounter == speechCounterAtHop) {
          hasUnevaluatedSpeech = false
        }
      }

      val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)

      // Discard stale result if the Ayah/page reset while inference was running
      val isStillSameEpoch = synchronized(bufferLock) { bufferEpoch == snapshotEpoch }
      if (!isStillSameEpoch || !_uiState.value.isEnabled) return@launch
      val latestSlice = trackedSlice ?: return@launch
      if (latestSlice.page != activeSlice.page || latestSlice.suraAyah != activeSlice.suraAyah) {
        return@launch
      }

      val baseRevealed = max(currentCountSnapshot, revealedWordCountInSlice)
      val hopMatch = TasmeeMonotonicMatcher.evaluateHop(
        slice = latestSlice,
        currentRevealedCount = baseRevealed,
        logprobs = asrOut.logprobs,
        vocab = asrOut.vocab,
        minSignalBFrame = minSignalBFrameCursor,
        blankId = asrOut.blankId
      )

      // If the reciter spoke Basmalah before Ayah 1 of a Surah (sura != 1), trim the finished
      // Basmalah audio once "الرحيم" is completed so Ayah 1 starts on a clean acoustic window.
      if (!hopMatch.didAdvance &&
        baseRevealed == 0 &&
        latestSlice.suraAyah.sura != 1 &&
        latestSlice.suraAyah.ayah == 1 &&
        hopMatch.decodedHypWords.lastOrNull()?.let { QuranRecitationGrader.phoneticRasm(it) } == "لرحيم"
      ) {
        synchronized(bufferLock) {
          if (bufferEpoch == snapshotEpoch && rollingSampleCount > 4800) {
            val keepTail = 3200 // 200 ms tail
            System.arraycopy(
              rollingAyahPcm,
              rollingSampleCount - keepTail,
              rollingAyahPcm,
              0,
              keepTail
            )
            rollingSampleCount = keepTail
          }
        }
        return@launch
      }

      if (!hopMatch.didAdvance) {
        // Cross-Ayah follow-along catch-up: if only 1..3 words remain in the current Ayah and
        // the reciter has already moved into the opening of the next Ayah on this Mushaf page,
        // complete the current Ayah and reveal the heard opening word(s) of the next Ayah!
        val remainingInAyah = latestSlice.wordCount - baseRevealed
        if (remainingInAyah in 1..3 && hopMatch.decodedHypWords.isNotEmpty()) {
          val nextSlice = resolveNextUnrevealedAyahSliceOnPage(
            context = appCtx,
            page = latestSlice.page,
            afterAyah = latestSlice.suraAyah,
            vocab = asrOut.vocab
          )
          if (nextSlice != null) {
            val nextUnlocked = TasmeeMonotonicMatcher.evaluateCrossAyahContinuation(
              currentSlice = latestSlice,
              currentRevealedCount = baseRevealed,
              nextSlice = nextSlice,
              decodedHypWords = hopMatch.decodedHypWords
            )
            if (nextUnlocked > 0) {
              withContext(Dispatchers.Main.immediate) {
                if (!_uiState.value.isEnabled) return@withContext
                val currentSameEpoch = synchronized(bufferLock) { bufferEpoch == snapshotEpoch }
                if (!currentSameEpoch) return@withContext
                applyCrossAyahContinuationOnMain(
                  appCtx = appCtx,
                  completedSlice = latestSlice,
                  completedFromWordIdx = baseRevealed,
                  nextSlice = nextSlice,
                  nextUnlockedWords = nextUnlocked,
                  inferenceMs = elapsedMs
                )
              }
              return@launch
            }
          }
        }

        withContext(Dispatchers.Main.immediate) {
          if (_uiState.value.isEnabled) {
            updateUiState { it.copy(lastInferenceMs = elapsedMs) }
          }
        }
        return@launch
      }

      withContext(Dispatchers.Main.immediate) {
        if (!_uiState.value.isEnabled) return@withContext
        val currentSameEpoch = synchronized(bufferLock) { bufferEpoch == snapshotEpoch }
        if (!currentSameEpoch) return@withContext

        applyMonotonicMatchOnMain(
          appCtx = appCtx,
          slice = latestSlice,
          hopMatch = hopMatch,
          inferenceMs = elapsedMs
        )
      }
    }
  }

  private fun resolveNextUnrevealedAyahSliceOnPage(
    context: Context,
    page: Int,
    afterAyah: SuraAyah,
    vocab: Array<String>?
  ): PageAyahTasmeeSlice? {
    val candidates = QuranMemorizationManager.getCandidateAyahsOnPage(page)
    val idx = candidates.indexOf(afterAyah)
    if (idx < 0 || idx + 1 >= candidates.size) return null
    val nextAyah = candidates.subList(idx + 1, candidates.size).firstOrNull { ayah ->
      !QuranMemorizationManager.isAyahFullyRevealed(page, ayah)
    } ?: return null
    val ayahRef = QuranAsrReferenceRepository.getAyahReference(context, nextAyah, vocab) ?: return null
    val pageGlyphs = QuranMemorizationManager.getWordPositionsForAyah(page, nextAyah)
    val totalAyahGlyphs = QuranMemorizationManager.getWordCountForAyah(nextAyah)
    return QuranAsrReferenceRepository.buildPageAyahSlice(
      page = page,
      ayahRef = ayahRef,
      pageGlyphPositions = pageGlyphs,
      totalAyahGlyphCount = totalAyahGlyphs
    )
  }

  private fun applyCrossAyahContinuationOnMain(
    appCtx: Context,
    completedSlice: PageAyahTasmeeSlice,
    completedFromWordIdx: Int,
    nextSlice: PageAyahTasmeeSlice,
    nextUnlockedWords: Int,
    inferenceMs: Long
  ) {
    // 1. Reveal remaining words of completedSlice and clear its highlight once fully revealed
    val remainingGlyphs = ArrayList<Int>(4)
    for (wIdx in completedFromWordIdx.coerceAtLeast(0) until completedSlice.wordCount) {
      remainingGlyphs.addAll(completedSlice.glyphPositionsByWordIdx[wIdx])
    }
    clearDetectedWordsForAyah(completedSlice.page, completedSlice.suraAyah)
    if (remainingGlyphs.isNotEmpty()) {
      QuranMemorizationManager.advanceTasmeeMonotonicCursor(
        page = completedSlice.page,
        suraAyah = completedSlice.suraAyah,
        unlockedGlyphPositions = remainingGlyphs,
        skippedGlyphPositions = emptySet(),
        quranInfo = quranInfoRef,
        context = appCtx
      )
    }

    // 2. Keep only the latest ~2.2s tail of audio so the opening words of nextSlice stay aligned
    //    while older audio from completedSlice is discarded
    synchronized(bufferLock) {
      val keepTailSamples = min(rollingSampleCount, 35200) // 2.2s at 16 kHz
      if (keepTailSamples > 0 && rollingSampleCount > keepTailSamples) {
        System.arraycopy(
          rollingAyahPcm,
          rollingSampleCount - keepTailSamples,
          rollingAyahPcm,
          0,
          keepTailSamples
        )
      }
      rollingSampleCount = keepTailSamples
      samplesSinceLastHop = 0
      hasUnevaluatedSpeech = false
      bufferEpoch += 1L
    }

    // 3. Advance cursor into nextSlice and unlock its heard opening word(s)
    val clampedNext = nextUnlockedWords.coerceIn(1, nextSlice.wordCount)
    trackedPage = nextSlice.page
    trackedAyah = nextSlice.suraAyah
    trackedSlice = nextSlice
    revealedWordCountInSlice = clampedNext
    minSignalBFrameCursor = 0

    val nextGlyphs = ArrayList<Int>(4)
    for (wIdx in 0 until clampedNext) {
      nextGlyphs.addAll(nextSlice.glyphPositionsByWordIdx[wIdx])
    }
    var latestDetected: DetectedTasmeeWord? = null
    var version = detectedWordsVersionCounter
    if (nextGlyphs.isNotEmpty()) {
      val recorded = recordDetectedGlyphs(nextSlice.page, nextSlice.suraAyah, nextGlyphs)
      latestDetected = recorded.first
      version = recorded.second
      QuranMemorizationManager.advanceTasmeeMonotonicCursor(
        page = nextSlice.page,
        suraAyah = nextSlice.suraAyah,
        unlockedGlyphPositions = nextGlyphs,
        skippedGlyphPositions = emptySet(),
        quranInfo = quranInfoRef,
        context = appCtx
      )
    }
    onWordsRevealed?.invoke()

    if (clampedNext >= nextSlice.wordCount) {
      handleActiveAyahSliceCompleted(appCtx, nextSlice.page)
    } else {
      updateUiState {
        it.copy(
          activePage = nextSlice.page,
          activeAyah = nextSlice.suraAyah,
          revealedWordsInAyah = clampedNext,
          totalWordsInAyah = nextSlice.wordCount,
          lastInferenceMs = inferenceMs,
          latestDetectedWord = latestDetected,
          detectedWordsVersion = version
        )
      }
    }
  }

  private fun applyMonotonicMatchOnMain(
    appCtx: Context,
    slice: PageAyahTasmeeSlice,
    hopMatch: TasmeeMonotonicMatcher.HopMatchResult,
    inferenceMs: Long
  ) {
    if (hopMatch.newRevealedCount <= revealedWordCountInSlice) return

    val prevCount = revealedWordCountInSlice
    val newCount = hopMatch.newRevealedCount.coerceAtMost(slice.wordCount)
    revealedWordCountInSlice = newCount
    minSignalBFrameCursor = hopMatch.updatedMinSignalBFrame

    val unlockedGlyphs = ArrayList<Int>(4)
    for (wIdx in prevCount until newCount) {
      unlockedGlyphs.addAll(slice.glyphPositionsByWordIdx[wIdx])
    }

    val skippedGlyphs = LinkedHashSet<Int>()
    for (skipWIdx in hopMatch.skippedWordIndices) {
      if (skipWIdx in 0 until slice.wordCount) {
        skippedGlyphs.addAll(slice.glyphPositionsByWordIdx[skipWIdx])
      }
    }
    if (hopMatch.skippedWordIndices.isNotEmpty()) {
      skippedCountInSession += hopMatch.skippedWordIndices.size
    }

    val isAyahNowComplete = newCount >= slice.wordCount
    val detectedGlyphs = unlockedGlyphs.filter { it !in skippedGlyphs }
    val (latestDetected, version) = if (isAyahNowComplete) {
      clearDetectedWordsForAyah(slice.page, slice.suraAyah)
      getLatestDetectedWordForPage(slice.page) to detectedWordsVersionCounter
    } else {
      recordDetectedGlyphs(slice.page, slice.suraAyah, detectedGlyphs)
    }

    QuranMemorizationManager.advanceTasmeeMonotonicCursor(
      page = slice.page,
      suraAyah = slice.suraAyah,
      unlockedGlyphPositions = unlockedGlyphs,
      skippedGlyphPositions = skippedGlyphs,
      quranInfo = quranInfoRef,
      context = appCtx
    )
    onWordsRevealed?.invoke()

    if (isAyahNowComplete) {
      updateUiState {
        it.copy(
          latestDetectedWord = latestDetected,
          detectedWordsVersion = version
        )
      }
      handleActiveAyahSliceCompleted(appCtx, slice.page)
    } else {
      updateUiState {
        it.copy(
          activePage = slice.page,
          activeAyah = slice.suraAyah,
          revealedWordsInAyah = newCount,
          totalWordsInAyah = slice.wordCount,
          skippedWordsCount = skippedCountInSession,
          lastInferenceMs = inferenceMs,
          latestDetectedWord = latestDetected ?: it.latestDetectedWord,
          detectedWordsVersion = version
        )
      }
    }
  }

  /**
   * Window Reset on Ayah Completion:
   * Immediately trims/resets the rolling acoustic buffer so the next Ayah starts completely fresh,
   * and turns the Mushaf page (or loops the active range) if the final Ayah on the page finished.
   */
  private fun handleActiveAyahSliceCompleted(appCtx: Context, completedPage: Int) {
    // 0. Remove detected-word highlights for the now fully revealed Ayah so calligraphy stays clean
    trackedAyah?.let { completedAyah ->
      clearDetectedWordsForAyah(completedPage, completedAyah)
    }

    // 1. Immediate acoustic window reset so completed Ayah audio never bleeds into the next Ayah
    resetRollingAudioBuffer()
    minSignalBFrameCursor = 0

    // 2. Check if there is another unrevealed Ayah on this Mushaf page
    val candidateAyahs = QuranMemorizationManager.getCandidateAyahsOnPage(completedPage)
    val nextUnrevealedOnPage = candidateAyahs.firstOrNull { ayah ->
      !QuranMemorizationManager.isAyahFullyRevealed(completedPage, ayah)
    }

    if (nextUnrevealedOnPage != null) {
      updateUiState {
        it.copy(
          latestDetectedWord = getLatestDetectedWordForPage(completedPage),
          detectedWordsVersion = detectedWordsVersionCounter
        )
      }
      syncCursorToPageAndFirstUnrevealedAyah(appCtx, completedPage, resetAudio = false)
      return
    }

    // 3. Final Ayah on the page (or end of MemorizationRange) completed -> latch last word briefly,
    //    then automatically flip to the next Mushaf page (or loop range)!
    updateUiState {
      it.copy(
        revealedWordsInAyah = it.totalWordsInAyah,
        skippedWordsCount = skippedCountInSession,
        latestDetectedWord = getLatestDetectedWordForPage(completedPage),
        detectedWordsVersion = detectedWordsVersionCounter
      )
    }

    pageTurnJob?.cancel()
    pageTurnJob = scope.launch {
      delay(PAGE_TURN_LATCH_DELAY_MS)
      if (!_uiState.value.isEnabled) return@launch
      resetRollingAudioBuffer()
      onAdvancePageOrLoopRange?.invoke(completedPage)
    }
  }

  private fun stopAudioCaptureInternal() {
    captureJob?.cancel()
    captureJob = null
    val record = audioRecord
    audioRecord = null
    if (record != null) {
      try {
        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          record.stop()
        }
      } catch (_: Exception) {
      }
      try {
        record.release()
      } catch (_: Exception) {
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun createBestAudioRecord(bufferSizeInBytes: Int): AudioRecord? {
    val sources = intArrayOf(
      MediaRecorder.AudioSource.VOICE_RECOGNITION,
      MediaRecorder.AudioSource.MIC
    )
    for (src in sources) {
      try {
        val candidate = AudioRecord(
          src,
          SAMPLE_RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSizeInBytes
        )
        if (candidate.state == AudioRecord.STATE_INITIALIZED) {
          return candidate
        }
        candidate.release()
      } catch (_: Exception) {
      }
    }
    return null
  }

  fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
  }

  private fun updateUiState(transform: (TasmeeUiState) -> TasmeeUiState) {
    val updated = transform(_uiState.value)
    _uiState.value = updated
    for (listener in listeners) {
      listener.onTasmeeStateChanged(updated)
    }
  }
}
