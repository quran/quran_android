package com.quran.labs.androidquran.kidsclassroom

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.quran.labs.androidquran.memorization.tasmee.VadWaveformMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Process-wide 150 MB Media3 [SimpleCache] singleton so multiple controller lifecycles
 * share cached Minshawi Teacher audio cleanly without directory lock collisions.
 */
@OptIn(UnstableApi::class)
internal object KidsExoSimpleCacheHolder {
  private const val SIMPLE_CACHE_DIR = "tilawi_kids_exo_cache_150mb"
  private const val MAX_SIMPLE_CACHE_BYTES = 150L * 1024L * 1024L // 150 MB

  @Volatile
  private var simpleCache: SimpleCache? = null

  fun getOrNull(appContext: Context): SimpleCache? {
    simpleCache?.let { return it }
    synchronized(this) {
      simpleCache?.let { return it }
      return try {
        val cacheDir = File(appContext.cacheDir, SIMPLE_CACHE_DIR).apply { mkdirs() }
        SimpleCache(
          cacheDir,
          LeastRecentlyUsedCacheEvictor(MAX_SIMPLE_CACHE_BYTES),
          StandaloneDatabaseProvider(appContext)
        ).also { simpleCache = it }
      } catch (t: Throwable) {
        Timber.w(t, "Falling back to uncached DataSource; SimpleCache unavailable")
        null
      }
    }
  }
}

/**
 * Manages the audio pipelines for both Children Learning Mode sub-modes across Surahs `1..114`:
 *
 * ### Mode A: "Teacher & Kids Repeat" (`المعلم وترديد الأطفال` — Graded 3-Stage Loop)
 * 1. **Stage 1 (Sheikh)**: Play `audio_url` via `ExoPlayer` (`SimpleCache` `150 MB`, prefetch
 *    `next_surah:next_ayah`). Seek to `qari_start_sec`, poll every `20 ms` until `qari_end_sec`.
 * 2. **Stage 2 (Audio Kids)**: If `!turn.contiguous_playback`, seek to `kids_start_sec`. Play until
 *    `kids_end_sec`, then pause immediately.
 * 3. **Stage 3 (Child's Turn)**: Un-gate persistent warm `16,000 Hz` `PCM_16BIT` `AudioRecord`
 *    (`VOICE_RECOGNITION`).
 *    - Dynamic max timeout: `maxOf(12.0f, (turn.qari_end_sec - turn.qari_start_sec) * 1.6f + 4.0f)`.
 *    - Silence VAD endpoint: Once cumulative voiced speech `>= 550 ms`, trigger single-shot ONNX
 *      evaluation after `1,150 ms` of silence.
 *
 * ### Mode B: "Recite & Reveal / Tasmee'" (`التسميع الحي` — Streaming Blank-to-Revealed)
 * - `ExoPlayer` is off.
 * - Every `350 ms`, slice the latest `4.5s` (`72,000` samples) from the rolling `AudioRecord`
 *   ring buffer and dispatch to ONNX on `Dispatchers.Default`.
 */
@OptIn(UnstableApi::class)
class KidsClassroomAudioController(
  private val appContext: Context
) {

  companion object {
    const val PLAYHEAD_POLL_MS = 20L
    const val MISTAKE_TAIL_SLOW_MO_SPEED = 0.75f
    private const val SPEAKER_DRAIN_PAUSE_MS = 220L

    // AudioRecord & Stage 3 endpointing parameters
    const val SAMPLE_RATE = 16000
    private const val MAX_RECORD_SECONDS = 60
    private const val MAX_RECORD_SAMPLES = SAMPLE_RATE * MAX_RECORD_SECONDS
    const val MIN_VOICED_SPEECH_MS = 550L
    const val POST_SPEECH_SILENCE_STOP_MS = 1150L
    const val DEFAULT_CHILD_MAX_TIMEOUT_SEC = 12.0f
    private const val MIN_TAP_TO_STOP_ELAPSED_MS = 250L

    // Mode B ("Recite & Reveal / Tasmee'") rolling ring buffer parameters
    const val TASMEE_HOP_MS = 350L
    const val TASMEE_WINDOW_SECONDS = 4.5f
    const val TASMEE_RING_SAMPLES = 72000 // 4.5s * 16,000 Hz

    // Elevated silence threshold (~ -31 dBFS) + 2.6x adaptive ambient margin + 3-frame hysteresis
    const val VOICE_RMS_THRESHOLD = 0.028f
    const val AMBIENT_NOISE_MULTIPLIER = 2.6f
    const val MAX_ADAPTIVE_RMS_THRESHOLD = 0.075f
    const val MIN_CONSECUTIVE_VOICED_FRAMES = 3 // 60ms at 20ms/chunk

    internal fun computeEffectiveVoiceThreshold(ambientNoiseRms: Float): Float {
      return max(
        VOICE_RMS_THRESHOLD,
        (ambientNoiseRms * AMBIENT_NOISE_MULTIPLIER).coerceAtMost(MAX_ADAPTIVE_RMS_THRESHOLD)
      )
    }
  }

  private val coroutineErrorHandler = CoroutineExceptionHandler { _, throwable ->
    Timber.e(throwable, "Unhandled coroutine error in KidsClassroomAudioController")
  }

  private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + coroutineErrorHandler)
  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineErrorHandler)
  private val computeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + coroutineErrorHandler)

  private val upstreamDataSourceFactory: DataSource.Factory by lazy {
    val httpFactory = DefaultHttpDataSource.Factory()
      .setConnectTimeoutMs(20_000)
      .setReadTimeoutMs(30_000)
      .setAllowCrossProtocolRedirects(true)
    DefaultDataSource.Factory(appContext, httpFactory)
  }

  private val cacheDataSourceFactory: CacheDataSource.Factory? by lazy {
    val cache = KidsExoSimpleCacheHolder.getOrNull(appContext)
    cache?.let {
      CacheDataSource.Factory()
        .setCache(it)
        .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
  }

  private val mediaDataSourceFactory: DataSource.Factory by lazy {
    cacheDataSourceFactory ?: upstreamDataSourceFactory
  }

  val rmsAudioProcessor: RmsEnvelopeAudioProcessor = RmsEnvelopeAudioProcessor()

  private var exoPlayer: ExoPlayer? = null
  private var loadedMediaKey: String? = null
  private var playbackLoopJob: Job? = null
  private var prefetchJob: Job? = null

  @Volatile
  private var isExoSpeakerPlaying: Boolean = false

  @Volatile
  private var latestPlayheadMs: Long = 0L

  @Volatile
  private var playbackSpeedFactor: Float = 1.0f

  @Volatile
  private var activePlaybackErrorCallback: ((String) -> Unit)? = null

  private val playerListener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
      isExoSpeakerPlaying = isPlaying
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
      if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        isExoSpeakerPlaying = false
      }
    }

    override fun onPlayerError(error: PlaybackException) {
      isExoSpeakerPlaying = false
      playbackLoopJob?.cancel()
      val errCallback = activePlaybackErrorCallback
      activePlaybackErrorCallback = null
      try {
        exoPlayer?.pause()
      } catch (_: Throwable) {
      }
      errCallback?.invoke(error.localizedMessage ?: "Audio stream error")
    }
  }

  // Persistent warm 16,000 Hz PCM_16BIT AudioRecord state
  @Volatile
  private var warmAudioRecord: AudioRecord? = null

  @Volatile
  private var warmMicJob: Job? = null

  // Mode A single-shot Stage 3 gate state
  @Volatile
  private var isMicGateOpen: Boolean = false

  private val pcmCaptureBuffer = ShortArray(MAX_RECORD_SAMPLES)

  @Volatile
  private var capturedSampleCount: Int = 0

  @Volatile
  private var voicedDurationMs: Long = 0L

  @Volatile
  private var trailingSilenceMs: Long = 0L

  @Volatile
  private var consecutiveVoicedFrames: Int = 0

  @Volatile
  private var gateOpenedAtMs: Long = 0L

  @Volatile
  private var activeMaxTimeoutMs: Long = (DEFAULT_CHILD_MAX_TIMEOUT_SEC * 1000f).toLong()

  @Volatile
  private var ambientNoiseRms: Float = 0.008f

  @Volatile
  private var onChildRecordingFinishedCallback: ((FloatArray) -> Unit)? = null

  @Volatile
  private var onMicLevelChangedCallback: ((Float, Long, Boolean, List<Float>) -> Unit)? = null

  @Volatile
  private var onStreamingPcmChunkCallback: ((ShortArray) -> Unit)? = null

  @Volatile
  private var streamingChunkSizeSamples: Int = 8960 // 560 ms at 16 kHz

  @Volatile
  private var lastStreamedSampleCursor: Int = 0

  // Mode B ("Recite & Reveal / Tasmee'") 4.5s rolling ring buffer & 350ms hop job
  private val tasmeeRingLock = Any()
  private val tasmeeRingBuffer = ShortArray(TASMEE_RING_SAMPLES)
  private var tasmeeRingWritePos: Int = 0
  private var tasmeeRingFilledSamples: Int = 0
  private var tasmeeQuietMsSinceVoice: Long = 0L
  private var tasmeeSawSilenceGapFlag: Boolean = false
  private var hasUnevaluatedTasmeeSpeech: Boolean = false
  private var tasmeeSpeechArrivalCounter: Long = 0L

  @Volatile
  private var isTasmeeStreamingActive: Boolean = false

  private var tasmeeTickerJob: Job? = null

  private fun runOnMainImmediate(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      block()
    } else {
      mainScope.launch { block() }
    }
  }

  fun latestPlaybackPositionMs(): Long = latestPlayheadMs

  fun latestAudioRms(): Float =
    if (isExoSpeakerPlaying) rmsAudioProcessor.latestRmsForPlayback() else 0f

  fun latestAcousticFrame(): AudioAcousticFrame =
    if (isExoSpeakerPlaying) rmsAudioProcessor.latestAcousticFrame() else AudioAcousticFrame()

  fun isPlayingSpeakerAudio(): Boolean = isExoSpeakerPlaying

  /**
   * Sets `ExoPlayer` playback speed while preserving natural pitch (`pitch = 1.0f`)
   * for `"Watch Mouth Slowly (0.75×)"` mode.
   */
  fun setPlaybackSpeed(speed: Float) {
    val clamped = speed.coerceIn(0.5f, 1.5f)
    playbackSpeedFactor = clamped
    runOnMainImmediate {
      try {
        exoPlayer?.playbackParameters = PlaybackParameters(clamped, 1.0f)
      } catch (_: Throwable) {
      }
    }
  }

  private fun getOrCreateExoPlayerOnMain(): ExoPlayer {
    exoPlayer?.let { return it }
    val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
      .setDataSourceFactory(mediaDataSourceFactory)
    val audioAttributes = AudioAttributes.Builder()
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .setUsage(C.USAGE_MEDIA)
      .build()
    val renderersFactory = object : DefaultRenderersFactory(appContext) {
      override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
      ): AudioSink {
        return DefaultAudioSink.Builder(context)
          .setEnableFloatOutput(enableFloatOutput)
          .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
          .setAudioProcessors(arrayOf(rmsAudioProcessor))
          .build()
      }
    }
    return ExoPlayer.Builder(appContext, renderersFactory)
      .setMediaSourceFactory(mediaSourceFactory)
      .build()
      .apply {
        setAudioAttributes(audioAttributes, false)
        setHandleAudioBecomingNoisy(true)
        playbackParameters = PlaybackParameters(playbackSpeedFactor, 1.0f)
        addListener(playerListener)
      }
      .also { exoPlayer = it }
  }

  /**
   * Starts Mode A Stage 1 (`Sheikh Recites`) -> Stage 2 (`Audio Kids Repeat`) for [verse] and [turn],
   * polling `ExoPlayer` every `20 ms`, prefetching [nextVerseToPrefetch] into the `150 MB` `SimpleCache`,
   * and pausing immediately at `kids_end_sec` before invoking [onStage2Completed].
   */
  fun playTurnQariAndKids(
    verse: TeacherVerseSpec,
    turn: TurnSpec,
    nextVerseToPrefetch: TeacherVerseSpec?,
    hasChildrenRepeat: Boolean = true,
    onStageChanged: (ClassroomStage) -> Unit,
    onActiveWordChanged: (Int) -> Unit,
    onStage2Completed: () -> Unit,
    onPlaybackError: (String) -> Unit
  ) {
    stopStreamingTasmeeInternal(releaseMicIfIdle = true)
    closeMicGate()

    runOnMainImmediate {
      playbackLoopJob?.cancel()
      activePlaybackErrorCallback = onPlaybackError

      try {
        val player = getOrCreateExoPlayerOnMain()
        val targetUri = Uri.parse(verse.audioUrl)
        val mediaKey = targetUri.toString()

        if (loadedMediaKey != mediaKey || player.mediaItemCount == 0) {
          loadedMediaKey = mediaKey
          player.setMediaItem(MediaItem.fromUri(targetUri))
          player.prepare()
        } else if (player.playbackState == Player.STATE_IDLE || player.playerError != null) {
          player.prepare()
        }

        prefetchNextVerseIntoSimpleCache(nextVerseToPrefetch)

        val qariStartMs = turn.effectiveQariStartMs
        val qariEndMs = turn.effectiveQariEndMs
        val kidsStartMs = turn.effectiveKidsStartMs
        val kidsEndMs = turn.effectiveKidsEndMs
        val isContiguous = turn.contiguousPlayback && turn.effectiveContiguousPlayback

        onStageChanged(ClassroomStage.QARI_RECITING)
        rmsAudioProcessor.resetEnvelope()
        player.playbackParameters = PlaybackParameters(playbackSpeedFactor, 1.0f)
        player.seekTo(qariStartMs)
        latestPlayheadMs = qariStartMs
        isExoSpeakerPlaying = true
        player.playWhenReady = true

        playbackLoopJob = mainScope.launch {
          var inKidsStage = false

          while (isActive) {
            val err = player.playerError
            if (err != null) {
              isExoSpeakerPlaying = false
              val cb = activePlaybackErrorCallback
              activePlaybackErrorCallback = null
              player.pause()
              cb?.invoke(err.localizedMessage ?: "Audio stream error")
              return@launch
            }

            val posMs = player.currentPosition
            latestPlayheadMs = posMs
            val wordCount = turn.uthmaniWords.size.coerceAtLeast(1)

            if (!inKidsStage) {
              val span = (qariEndMs - qariStartMs).coerceAtLeast(250L)
              val progress = ((posMs - qariStartMs).toFloat() / span.toFloat()).coerceIn(0f, 0.999f)
              val wordOffset = (progress * wordCount).toInt().coerceIn(0, wordCount - 1)
              onActiveWordChanged(turn.wordStartIdx + wordOffset + 1)

              if (posMs >= qariEndMs || player.playbackState == Player.STATE_ENDED) {
                if (!hasChildrenRepeat) {
                  // Reciters without a children chorus (e.g. Al-Husary Muallim) pause here for the child's turn.
                  // Wait briefly for Android AudioTrack / room speaker tail to decay before opening the mic gate.
                  activePlaybackErrorCallback = null
                  isExoSpeakerPlaying = false
                  rmsAudioProcessor.resetEnvelope()
                  player.pause()
                  delay(SPEAKER_DRAIN_PAUSE_MS)
                  onStage2Completed()
                  return@launch
                }
                inKidsStage = true
                onStageChanged(ClassroomStage.KIDS_REPEATING)
                if (!isContiguous && abs(posMs - kidsStartMs) > 60L) {
                  player.seekTo(kidsStartMs)
                  latestPlayheadMs = kidsStartMs
                }
              }
            } else {
              val span = (kidsEndMs - kidsStartMs).coerceAtLeast(250L)
              val progress = ((posMs - kidsStartMs).toFloat() / span.toFloat()).coerceIn(0f, 0.999f)
              val wordOffset = (progress * wordCount).toInt().coerceIn(0, wordCount - 1)
              onActiveWordChanged(turn.wordStartIdx + wordOffset + 1)

              if (posMs >= kidsEndMs || player.playbackState == Player.STATE_ENDED) {
                activePlaybackErrorCallback = null
                isExoSpeakerPlaying = false
                rmsAudioProcessor.resetEnvelope()
                player.pause()
                onStage2Completed()
                return@launch
              }
            }

            delay(PLAYHEAD_POLL_MS)
          }
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (t: Throwable) {
        isExoSpeakerPlaying = false
        activePlaybackErrorCallback = null
        Timber.e(t, "Error playing Minshawi Teacher audio")
        onPlaybackError(t.localizedMessage ?: "Playback failed")
      }
    }
  }

  /**
   * Plays **ONLY Sheikh Al-Minshawi** (skipping the kids chorus completely) from
   * `tailTurn.effectiveQariStartMs` to `tailTurn.effectiveQariEndMs` at `0.75×` Slow-Mo
   * (pitch-preserved `1.0f`), then restores the user's normal playback speed upon completion.
   */
  fun playQariOnlyTail(
    verse: TeacherVerseSpec,
    tailTurn: TurnSpec,
    nextVerseToPrefetch: TeacherVerseSpec?,
    playbackSpeed: Float = MISTAKE_TAIL_SLOW_MO_SPEED,
    onStageChanged: (ClassroomStage) -> Unit,
    onActiveWordChanged: (Int) -> Unit,
    onQariCompleted: () -> Unit,
    onPlaybackError: (String) -> Unit
  ) {
    stopStreamingTasmeeInternal(releaseMicIfIdle = true)
    closeMicGate()

    runOnMainImmediate {
      playbackLoopJob?.cancel()
      activePlaybackErrorCallback = onPlaybackError

      try {
        val player = getOrCreateExoPlayerOnMain()
        val targetUri = Uri.parse(verse.audioUrl)
        val mediaKey = targetUri.toString()

        if (loadedMediaKey != mediaKey || player.mediaItemCount == 0) {
          loadedMediaKey = mediaKey
          player.setMediaItem(MediaItem.fromUri(targetUri))
          player.prepare()
        } else if (player.playbackState == Player.STATE_IDLE || player.playerError != null) {
          player.prepare()
        }

        prefetchNextVerseIntoSimpleCache(nextVerseToPrefetch)

        val qariStartMs = tailTurn.effectiveQariStartMs
        val qariEndMs = tailTurn.effectiveQariEndMs
        val effectiveSpeed = playbackSpeed.coerceIn(0.5f, 1.25f)

        onStageChanged(ClassroomStage.QARI_RECITING)
        rmsAudioProcessor.resetEnvelope()
        player.playbackParameters = PlaybackParameters(effectiveSpeed, 1.0f)
        player.seekTo(qariStartMs)
        latestPlayheadMs = qariStartMs
        isExoSpeakerPlaying = true
        player.playWhenReady = true

        playbackLoopJob = mainScope.launch {
          val wordCount = tailTurn.uthmaniWords.size.coerceAtLeast(1)
          val span = (qariEndMs - qariStartMs).coerceAtLeast(250L)

          while (isActive) {
            val err = player.playerError
            if (err != null) {
              isExoSpeakerPlaying = false
              val cb = activePlaybackErrorCallback
              activePlaybackErrorCallback = null
              player.pause()
              player.playbackParameters = PlaybackParameters(playbackSpeedFactor, 1.0f)
              cb?.invoke(err.localizedMessage ?: "Audio stream error")
              return@launch
            }

            val posMs = player.currentPosition
            latestPlayheadMs = posMs
            val progress = ((posMs - qariStartMs).toFloat() / span.toFloat()).coerceIn(0f, 0.999f)
            val wordOffset = (progress * wordCount).toInt().coerceIn(0, wordCount - 1)
            onActiveWordChanged(tailTurn.wordStartIdx + wordOffset + 1)

            if (posMs >= qariEndMs || player.playbackState == Player.STATE_ENDED) {
              activePlaybackErrorCallback = null
              isExoSpeakerPlaying = false
              rmsAudioProcessor.resetEnvelope()
              player.pause()
              player.playbackParameters = PlaybackParameters(playbackSpeedFactor, 1.0f)
              delay(SPEAKER_DRAIN_PAUSE_MS)
              onQariCompleted()
              return@launch
            }

            delay(PLAYHEAD_POLL_MS)
          }
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (t: Throwable) {
        isExoSpeakerPlaying = false
        activePlaybackErrorCallback = null
        Timber.e(t, "Error playing Sheikh-only mistake tail audio")
        onPlaybackError(t.localizedMessage ?: "Playback failed")
      }
    }
  }

  /**
   * Plays a focused audio slice (e.g., a single missed word tapped by a child) at `0.75×` Slow-Mo
   * with natural pitch (`1.0f`) so the teacher demonstrates the exact Tajweed cue clearly.
   */
  fun playFocusedSegment(
    audioUrl: String,
    startMs: Long,
    endMs: Long,
    speed: Float = 0.75f,
    onCompleted: () -> Unit = {},
    onPlaybackError: (String) -> Unit = {}
  ) {
    stopStreamingTasmeeInternal(releaseMicIfIdle = true)
    closeMicGate()

    runOnMainImmediate {
      playbackLoopJob?.cancel()
      activePlaybackErrorCallback = onPlaybackError
      try {
        val player = getOrCreateExoPlayerOnMain()
        val targetUri = Uri.parse(audioUrl)
        val mediaKey = targetUri.toString()
        if (loadedMediaKey != mediaKey || player.mediaItemCount == 0) {
          loadedMediaKey = mediaKey
          player.setMediaItem(MediaItem.fromUri(targetUri))
          player.prepare()
        } else if (player.playbackState == Player.STATE_IDLE || player.playerError != null) {
          player.prepare()
        }

        rmsAudioProcessor.resetEnvelope()
        player.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 1.25f), 1.0f)
        player.seekTo(startMs)
        latestPlayheadMs = startMs
        isExoSpeakerPlaying = true
        player.playWhenReady = true

        playbackLoopJob = mainScope.launch {
          while (isActive) {
            val posMs = player.currentPosition
            latestPlayheadMs = posMs
            if (posMs >= endMs || player.playbackState == Player.STATE_ENDED) {
              activePlaybackErrorCallback = null
              isExoSpeakerPlaying = false
              rmsAudioProcessor.resetEnvelope()
              player.pause()
              player.playbackParameters = PlaybackParameters(playbackSpeedFactor, 1.0f)
              onCompleted()
              return@launch
            }
            delay(PLAYHEAD_POLL_MS)
          }
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (t: Throwable) {
        isExoSpeakerPlaying = false
        activePlaybackErrorCallback = null
        onPlaybackError(t.localizedMessage ?: "Slow-Mo playback failed")
      }
    }
  }

  private fun prefetchNextVerseIntoSimpleCache(nextVerse: TeacherVerseSpec?) {
    if (nextVerse == null || nextVerse.audioUrl.isBlank()) return
    prefetchUrlIntoSimpleCache(nextVerse.audioUrl)
  }

  private fun prefetchUrlIntoSimpleCache(url: String) {
    if (url.isBlank()) return
    val factory = cacheDataSourceFactory ?: return
    prefetchJob?.cancel()
    prefetchJob = ioScope.launch {
      var writer: CacheWriter? = null
      try {
        val dataSpec = DataSpec(Uri.parse(url))
        val cacheDataSource = factory.createDataSource()
        writer = CacheWriter(
          cacheDataSource,
          dataSpec,
          ByteArray(32 * 1024)
        ) { _, _, _ ->
          if (!isActive) {
            throw CancellationException("Prefetch cancelled")
          }
        }
        writer.cache()
      } catch (ce: CancellationException) {
        writer?.cancel()
        throw ce
      } catch (_: Throwable) {
        // Ignore background prefetch errors when offline
      }
    }
  }

  /**
   * Ensures the persistent `16,000 Hz` `ENCODING_PCM_16BIT` `AudioRecord` (`VOICE_RECOGNITION`)
   * is initialized and warm in the background while gated closed during Stages 1 & 2.
   */
  @SuppressLint("MissingPermission")
  fun ensureWarmAudioRecordStarted() {
    if (warmMicJob?.isActive == true && warmAudioRecord != null) return
    synchronized(this) {
      if (warmMicJob?.isActive == true && warmAudioRecord != null) return
      try {
        val minBufBytes = AudioRecord.getMinBufferSize(
          SAMPLE_RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufBytes <= 0) return
        val bufferSizeInBytes = max(minBufBytes * 2, SAMPLE_RATE * 2)

        var recorder = AudioRecord(
          MediaRecorder.AudioSource.VOICE_RECOGNITION,
          SAMPLE_RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSizeInBytes
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
          recorder.release()
          recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeInBytes
          )
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
          recorder.release()
          return
        }

        recorder.startRecording()
        warmAudioRecord = recorder

        warmMicJob = ioScope.launch {
          val chunkSamples = 320 // 20 ms at 16,000 Hz
          val chunkMs = 20L
          val readBuf = ShortArray(chunkSamples)
          val waveformHistory = FloatArray(VadWaveformMath.DEFAULT_BAR_COUNT)
          var lastVoiceTimestampMs = 0L
          var lastTelemetryEmitMs = 0L

          try {
            while (isActive) {
              val readCount = recorder.read(readBuf, 0, chunkSamples)
              if (readCount <= 0) {
                delay(5L)
                continue
              }

              var sumSq = 0.0
              for (i in 0 until readCount) {
                val norm = readBuf[i].toDouble() / 32768.0
                sumSq += norm * norm
              }
              val rms = sqrt(sumSq / readCount.toDouble()).toFloat()
              val nowMs = System.currentTimeMillis()

              // Feed Mode B ("Recite & Reveal / Tasmee'") 4.5s rolling ring buffer if active
              if (isTasmeeStreamingActive) {
                val isChunkVoiced = rms >= VadWaveformMath.LIVE_TASMEE_VAD_RMS_THRESHOLD
                if (isChunkVoiced) {
                  lastVoiceTimestampMs = nowMs
                }
                val isVadActiveNow = lastVoiceTimestampMs > 0L &&
                  (nowMs - lastVoiceTimestampMs) <= VadWaveformMath.VAD_HANGOVER_MS
                val barsSnapshot = VadWaveformMath.updateBarsFromShortChunk(
                  history = waveformHistory,
                  samples = readBuf,
                  sampleCount = readCount,
                  isVadActive = isVadActiveNow
                )
                appendChunkToTasmeeRingBuffer(readBuf, readCount, rms, chunkMs)
                if (nowMs - lastTelemetryEmitMs >= 40L) {
                  lastTelemetryEmitMs = nowMs
                  onMicLevelChangedCallback?.invoke(rms, 0L, isVadActiveNow, barsSnapshot)
                }
                continue
              }

              if (!isMicGateOpen) {
                if (!isExoSpeakerPlaying && rms < 0.045f) {
                  ambientNoiseRms = (0.94f * ambientNoiseRms + 0.06f * rms).coerceIn(0.004f, 0.040f)
                }
                continue
              }

              val currentCaptured = capturedSampleCount
              val toCopy = (MAX_RECORD_SAMPLES - currentCaptured).coerceAtMost(readCount)
              if (toCopy > 0) {
                System.arraycopy(readBuf, 0, pcmCaptureBuffer, currentCaptured, toCopy)
                capturedSampleCount = currentCaptured + toCopy
              }

              // Emit live PCM chunks (default 560 ms) for stateful StreamingQuranAsrEngine
              val chunkCb = onStreamingPcmChunkCallback
              val chunkTarget = streamingChunkSizeSamples
              if (chunkCb != null && chunkTarget > 0) {
                val available = capturedSampleCount - lastStreamedSampleCursor
                if (available >= chunkTarget) {
                  val chunkCopy = ShortArray(chunkTarget)
                  System.arraycopy(pcmCaptureBuffer, lastStreamedSampleCursor, chunkCopy, 0, chunkTarget)
                  lastStreamedSampleCursor += chunkTarget
                  computeScope.launch {
                    chunkCb(chunkCopy)
                  }
                }
              }

              val effectiveThreshold = computeEffectiveVoiceThreshold(ambientNoiseRms)

              if (rms >= effectiveThreshold) {
                lastVoiceTimestampMs = nowMs
                val streak = consecutiveVoicedFrames + 1
                consecutiveVoicedFrames = streak
                if (streak >= MIN_CONSECUTIVE_VOICED_FRAMES) {
                  if (streak == MIN_CONSECUTIVE_VOICED_FRAMES) {
                    voicedDurationMs += chunkMs * MIN_CONSECUTIVE_VOICED_FRAMES
                  } else {
                    voicedDurationMs += chunkMs
                  }
                  trailingSilenceMs = 0L
                } else {
                  trailingSilenceMs += chunkMs
                }
              } else {
                consecutiveVoicedFrames = 0
                trailingSilenceMs += chunkMs
                if (voicedDurationMs == 0L && rms < VOICE_RMS_THRESHOLD) {
                  ambientNoiseRms = (0.95f * ambientNoiseRms + 0.05f * rms).coerceIn(0.004f, 0.040f)
                }
              }

              val isVadActiveNow = consecutiveVoicedFrames >= 1 ||
                (lastVoiceTimestampMs > 0L && (nowMs - lastVoiceTimestampMs) <= VadWaveformMath.VAD_HANGOVER_MS)
              val barsSnapshot = VadWaveformMath.updateBarsFromShortChunk(
                history = waveformHistory,
                samples = readBuf,
                sampleCount = readCount,
                isVadActive = isVadActiveNow
              )
              if (nowMs - lastTelemetryEmitMs >= 40L) {
                lastTelemetryEmitMs = nowMs
                onMicLevelChangedCallback?.invoke(rms, voicedDurationMs, isVadActiveNow, barsSnapshot)
              }

              val totalElapsedMs = nowMs - gateOpenedAtMs
              val reachedEndpointAfterSpeech =
                voicedDurationMs >= MIN_VOICED_SPEECH_MS && trailingSilenceMs >= POST_SPEECH_SILENCE_STOP_MS
              val reachedDynamicMaxTimeout = totalElapsedMs >= activeMaxTimeoutMs
              val reachedBufferCap = capturedSampleCount >= MAX_RECORD_SAMPLES

              if (reachedEndpointAfterSpeech || reachedDynamicMaxTimeout || reachedBufferCap) {
                finishChildCaptureInternal()
              }
            }
          } catch (ce: CancellationException) {
            throw ce
          } catch (t: Throwable) {
            Timber.e(t, "Error inside warmMicJob loop")
          }
        }
      } catch (t: Throwable) {
        Timber.e(t, "Failed to start warm 16kHz AudioRecord")
      }
    }
  }

  private fun appendChunkToTasmeeRingBuffer(
    chunk: ShortArray,
    count: Int,
    rms: Float,
    chunkMs: Long
  ) {
    synchronized(tasmeeRingLock) {
      var srcOffset = 0
      var remaining = count
      while (remaining > 0) {
        val spaceToEnd = TASMEE_RING_SAMPLES - tasmeeRingWritePos
        val step = min(remaining, spaceToEnd)
        System.arraycopy(chunk, srcOffset, tasmeeRingBuffer, tasmeeRingWritePos, step)
        tasmeeRingWritePos = (tasmeeRingWritePos + step) % TASMEE_RING_SAMPLES
        srcOffset += step
        remaining -= step
      }
      tasmeeRingFilledSamples = min(TASMEE_RING_SAMPLES, tasmeeRingFilledSamples + count)

      if (rms < 0.015f) {
        tasmeeQuietMsSinceVoice += chunkMs
        if (tasmeeQuietMsSinceVoice >= 420L) {
          tasmeeSawSilenceGapFlag = true
        }
      } else {
        tasmeeQuietMsSinceVoice = 0L
        hasUnevaluatedTasmeeSpeech = true
        tasmeeSpeechArrivalCounter += 1L
      }
    }
  }

  private data class TasmeeWindowSnapshot(
    val samples: FloatArray,
    val hadSilenceGap: Boolean,
    val forceTrailingDecode: Boolean,
    val speechCounter: Long
  )

  /**
   * Slices the latest up-to-`4.5s` (`72,000` samples) from the rolling `AudioRecord` ring buffer.
   */
  private fun snapshotLatestTasmeeWindow(): TasmeeWindowSnapshot {
    synchronized(tasmeeRingLock) {
      val n = tasmeeRingFilledSamples
      val hadSilence = tasmeeSawSilenceGapFlag
      val forceTrailing = hasUnevaluatedTasmeeSpeech
      val counter = tasmeeSpeechArrivalCounter
      if (n <= 0) {
        return TasmeeWindowSnapshot(FloatArray(0), hadSilence, false, counter)
      }

      val out = FloatArray(n)
      val inv = 1.0f / 32768.0f
      val startIdx = if (n < TASMEE_RING_SAMPLES) {
        0
      } else {
        tasmeeRingWritePos
      }

      var pos = startIdx
      for (i in 0 until n) {
        out[i] = (tasmeeRingBuffer[pos].toInt() * inv).coerceIn(-1.0f, 1.0f)
        pos++
        if (pos == TASMEE_RING_SAMPLES) pos = 0
      }
      return TasmeeWindowSnapshot(out, hadSilence, forceTrailing, counter)
    }
  }

  private fun markTasmeeTrailingDecodeConsumed(speechCounterAtSnapshot: Long) {
    synchronized(tasmeeRingLock) {
      if (tasmeeSpeechArrivalCounter == speechCounterAtSnapshot) {
        hasUnevaluatedTasmeeSpeech = false
      }
    }
  }

  /**
   * Clears the rolling `4.5s` ring buffer (invoked when `revealedCount == uthmani_words.size`
   * before advancing to `next_surah:next_ayah`, or when resetting/changing Ayahs).
   */
  fun resetStreamingRingBuffer() {
    synchronized(tasmeeRingLock) {
      tasmeeRingWritePos = 0
      tasmeeRingFilledSamples = 0
      tasmeeQuietMsSinceVoice = 0L
      tasmeeSawSilenceGapFlag = false
      hasUnevaluatedTasmeeSpeech = false
      tasmeeSpeechArrivalCounter = 0L
    }
  }

  /**
   * Clears the silence-gap flag once a word reveal consumes it.
   */
  fun clearTasmeeSilenceGapFlag() {
    synchronized(tasmeeRingLock) {
      tasmeeSawSilenceGapFlag = false
    }
  }

  /**
   * Starts Mode B ("Recite & Reveal / Tasmee'"):
   * - Turns `ExoPlayer` off immediately.
   * - Every `350 ms`, slices the latest `4.5s` from the rolling `AudioRecord` ring buffer and
   *   executes [onWindowSlice] sequentially on `Dispatchers.Default`.
   */
  fun startStreamingTasmee(
    onMicLevelChanged: (rms: Float, isVadActive: Boolean, waveformBars: List<Float>) -> Unit,
    onWindowSlice: suspend (
      windowPcmFloat32: FloatArray,
      hadSilenceGap: Boolean,
      forceTrailingDecode: Boolean
    ) -> Unit
  ) {
    synchronized(this) {
      isMicGateOpen = false
      consecutiveVoicedFrames = 0
      onChildRecordingFinishedCallback = null
    }
    runOnMainImmediate {
      activePlaybackErrorCallback = null
      isExoSpeakerPlaying = false
      playbackLoopJob?.cancel()
      try {
        exoPlayer?.pause()
      } catch (_: Throwable) {
      }
    }

    resetStreamingRingBuffer()
    onMicLevelChangedCallback = { rms, _, isVadActive, bars ->
      onMicLevelChanged(rms, isVadActive, bars)
    }
    isTasmeeStreamingActive = true
    ensureWarmAudioRecordStarted()

    tasmeeTickerJob?.cancel()
    tasmeeTickerJob = computeScope.launch {
      while (isActive && isTasmeeStreamingActive) {
        delay(TASMEE_HOP_MS)
        if (!isTasmeeStreamingActive) break
        val snap = snapshotLatestTasmeeWindow()
        if (snap.samples.size >= NeMoMelPreprocessor.MIN_TRIMMED_SAMPLES) {
          onWindowSlice(snap.samples, snap.hadSilenceGap, snap.forceTrailingDecode)
          if (snap.forceTrailingDecode) {
            markTasmeeTrailingDecodeConsumed(snap.speechCounter)
          }
        }
      }
    }
  }

  private fun stopStreamingTasmeeInternal(releaseMicIfIdle: Boolean) {
    isTasmeeStreamingActive = false
    tasmeeTickerJob?.cancel()
    tasmeeTickerJob = null
    resetStreamingRingBuffer()
    if (!isMicGateOpen) {
      onMicLevelChangedCallback = null
      if (releaseMicIfIdle) {
        stopAndReleaseAudioRecord()
      }
    }
  }

  fun stopStreamingTasmee() {
    stopStreamingTasmeeInternal(releaseMicIfIdle = true)
  }

  /**
   * Opens the microphone gate for Mode A Stage 3 (`Child's Turn`),
   * configured with dynamic max timeout `maxOf(12.0f, (qari_end_sec - qari_start_sec) * 1.6f + 4.0f)`.
   */
  fun startChildTurnRecording(
    maxTimeoutSec: Float = DEFAULT_CHILD_MAX_TIMEOUT_SEC,
    streamingChunkSamples: Int = 8960,
    onStreamingPcmChunk: ((ShortArray) -> Unit)? = null,
    onMicLevelChanged: (
      rms: Float,
      voicedMs: Long,
      isVadActive: Boolean,
      waveformBars: List<Float>
    ) -> Unit,
    onRecordingCompleted: (pcmFloat32: FloatArray) -> Unit
  ) {
    stopStreamingTasmeeInternal(releaseMicIfIdle = false)
    runOnMainImmediate {
      activePlaybackErrorCallback = null
      isExoSpeakerPlaying = false
      playbackLoopJob?.cancel()
      try {
        exoPlayer?.pause()
      } catch (_: Throwable) {
      }
    }

    ensureWarmAudioRecordStarted()
    synchronized(this) {
      capturedSampleCount = 0
      lastStreamedSampleCursor = 0
      voicedDurationMs = 0L
      trailingSilenceMs = 0L
      consecutiveVoicedFrames = 0
      gateOpenedAtMs = System.currentTimeMillis()
      activeMaxTimeoutMs = (maxOf(DEFAULT_CHILD_MAX_TIMEOUT_SEC, maxTimeoutSec) * 1000f).toLong()
      streamingChunkSizeSamples = streamingChunkSamples.coerceAtLeast(2560)
      onStreamingPcmChunkCallback = onStreamingPcmChunk
      onMicLevelChangedCallback = onMicLevelChanged
      onChildRecordingFinishedCallback = onRecordingCompleted
      isMicGateOpen = true
    }
  }

  fun canStopChildRecordingByPageTap(): Boolean {
    return isMicGateOpen && (System.currentTimeMillis() - gateOpenedAtMs) >= MIN_TAP_TO_STOP_ELAPSED_MS
  }

  fun stopChildTurnRecordingNow() {
    if (isMicGateOpen) {
      finishChildCaptureInternal()
    }
  }

  private fun finishChildCaptureInternal() {
    val callback: ((FloatArray) -> Unit)?
    val floatSamples: FloatArray
    val shouldReleaseMic: Boolean
    synchronized(this) {
      if (!isMicGateOpen) return
      isMicGateOpen = false
      consecutiveVoicedFrames = 0
      val count = capturedSampleCount
      floatSamples = NeMoMelPreprocessor.pcm16ToFloat32(pcmCaptureBuffer, count)
      callback = onChildRecordingFinishedCallback
      onChildRecordingFinishedCallback = null
      onMicLevelChangedCallback = null
      onStreamingPcmChunkCallback = null
      shouldReleaseMic = !isTasmeeStreamingActive
    }
    if (shouldReleaseMic) {
      stopAndReleaseAudioRecord()
    }
    mainScope.launch {
      callback?.invoke(floatSamples)
    }
  }

  fun closeMicGate() {
    val shouldReleaseMic: Boolean
    synchronized(this) {
      isMicGateOpen = false
      consecutiveVoicedFrames = 0
      onChildRecordingFinishedCallback = null
      shouldReleaseMic = !isTasmeeStreamingActive
      if (shouldReleaseMic) {
        onMicLevelChangedCallback = null
      }
    }
    if (shouldReleaseMic) {
      stopAndReleaseAudioRecord()
    }
  }

  fun isMicrophoneRecording(): Boolean {
    return warmAudioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
  }

  fun stopAndReleaseAudioRecord() {
    val jobToCancel: Job?
    val recordToRelease: AudioRecord?
    synchronized(this) {
      jobToCancel = warmMicJob
      warmMicJob = null
      recordToRelease = warmAudioRecord
      warmAudioRecord = null
    }
    jobToCancel?.cancel()
    if (recordToRelease != null) {
      try {
        if (recordToRelease.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          recordToRelease.stop()
        }
      } catch (_: Throwable) {
      }
      try {
        recordToRelease.release()
      } catch (_: Throwable) {
      }
    }
  }

  fun stopPlaybackAndMicGate() {
    stopStreamingTasmeeInternal(releaseMicIfIdle = false)
    synchronized(this) {
      isMicGateOpen = false
      consecutiveVoicedFrames = 0
      onChildRecordingFinishedCallback = null
      onMicLevelChangedCallback = null
    }
    stopAndReleaseAudioRecord()
    runOnMainImmediate {
      activePlaybackErrorCallback = null
      isExoSpeakerPlaying = false
      playbackLoopJob?.cancel()
      try {
        exoPlayer?.pause()
        exoPlayer?.playbackParameters = PlaybackParameters(playbackSpeedFactor, 1.0f)
      } catch (_: Throwable) {
      }
    }
  }

  fun releaseAll() {
    stopStreamingTasmeeInternal(releaseMicIfIdle = false)
    synchronized(this) {
      isMicGateOpen = false
      consecutiveVoicedFrames = 0
      onChildRecordingFinishedCallback = null
      onMicLevelChangedCallback = null
    }
    prefetchJob?.cancel()
    stopAndReleaseAudioRecord()
    runOnMainImmediate {
      activePlaybackErrorCallback = null
      isExoSpeakerPlaying = false
      playbackLoopJob?.cancel()
      try {
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
      } catch (_: Throwable) {
      }
      exoPlayer = null
      loadedMediaKey = null
    }
  }
}
