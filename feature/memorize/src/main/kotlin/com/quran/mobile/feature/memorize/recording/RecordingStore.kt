package com.quran.mobile.feature.memorize.recording

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import com.quran.mobile.feature.memorize.tracker.WordId
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * The user's recordings of their own recitation, only made when they turned recording on. They are
 * kept in the app's no-backup storage: never backed up, and only leave the phone if the user exports
 * them. Each recording is saved as its audio files and an alignment ([RecordingFormat]).
 */
@SingleIn(AppScope::class)
class RecordingStore private constructor(
  context: Context,
  private val preferences: MemorizePreferences,
  private val writers: (File) -> AudioFileWriter,
  /** Where a session's audio is encoded: one thread per session. */
  private val executors: () -> Executor,
  private val clock: () -> Long,
) : Recorders {
  @Inject constructor(@ApplicationContext context: Context, preferences: MemorizePreferences) : this(
    context, preferences, ::AacFileWriter, { Executors.newSingleThreadExecutor { Thread(it, "memorize-recorder") } },
    System::currentTimeMillis,
  )

  private val directory = File(context.noBackupFilesDir, "memorize/recordings")
  private val active = HashMap<String, ActiveSession>()
  private val _changes = MutableStateFlow(0)
  /** Changes whenever a recording is saved or deleted. */
  val changes: StateFlow<Int> = _changes.asStateFlow()

  override fun start(start: SuraAyah, mode: MemorizeState.Mode): SessionRecorder {
    val session = ActiveSession(UUID.randomUUID().toString(), start, mode, preferences.recordingSplit, executors())
    synchronized(active) { active[session.session] = session }
    return session
  }

  /** The saved recordings, newest first. Reads files. */
  fun list(): List<Recording> =
    directory.listFiles { file -> file.name.endsWith(METADATA) }.orEmpty()
      .mapNotNull { read(it) }
      .sortedByDescending { it.startedAt }

  /**
   * Everything recorded in [session] so far as one recording (all its suras in a row): for a session
   * that is paused, up to the pause. Reads files.
   */
  suspend fun session(session: String): Recording? {
    val recordings = synchronized(active) { active[session] }?.snapshot()
      ?: list().filter { it.session == session }.sortedBy { it.startedAt }
    return combine(session, recordings)
  }

  fun delete(id: String) {
    read(File(directory, id + METADATA))?.parts?.forEach { it.file.delete() }
    File(directory, id + METADATA).delete()
    _changes.value++
  }

  fun deleteAll() {
    directory.deleteRecursively()
    _changes.value++
  }

  private fun read(file: File): Recording? =
    runCatching { RecordingFormat.read(file.readText(), directory) }.getOrNull()
      ?.takeIf { recording -> recording.parts.isNotEmpty() && recording.parts.all { it.file.isFile } }

  private fun save(recording: Recording) {
    val file = File(directory, recording.id + METADATA)
    val partial = File(directory, recording.id + METADATA + ".part")
    partial.writeText(RecordingFormat.write(recording))
    partial.renameTo(file)
    _changes.value++
  }

  /** With "replace the same place", a new recording replaces the older ones of the same start. */
  private fun applyRetention(saved: Recording) {
    if (preferences.keepRecordings != MemorizePreferences.KeepRecordings.REPLACE_SAME_PLACE) return
    list().filter { it.id != saved.id && it.start == saved.start && it.mode == saved.mode }.forEach { delete(it.id) }
  }

  private fun combine(session: String, recordings: List<Recording>): Recording? {
    if (recordings.isEmpty()) return null
    var offset = 0L
    val words = ArrayList<Recording.Word>()
    for (recording in recordings) {
      recording.words.mapTo(words) { it.copy(startMs = it.startMs?.plus(offset), endMs = it.endMs?.plus(offset)) }
      offset += recording.durationMs
    }
    val first = recordings.first()
    return first.copy(id = session, end = recordings.last().end, parts = recordings.flatMap { it.parts }, words = words)
  }

  /**
   * A session being recorded. The last [HOLD_MS] of audio are held back before being written, so
   * that a new sura's recording can start just after the previous sura's last word even though the
   * recognizer only reports it a moment later. Everything but [write]'s conversion runs on [executor].
   */
  private inner class ActiveSession(
    override val session: String,
    start: SuraAyah,
    private val mode: MemorizeState.Mode,
    private val split: RecordingSplit,
    private val executor: Executor,
  ) : SessionRecorder {
    private val queued = AtomicInteger()

    // on the executor
    private val held = ArrayDeque<Chunk>()
    private var receivedSamples = 0L
    private var current: Open? = null
    private var nextStart = start
    private val done = ArrayList<Recording>()

    override fun write(samples: FloatArray) {
      // a phone too busy to keep up loses some recording rather than slowing down the listening
      if (queued.get() >= MAX_QUEUED_FRAMES) return
      val pcm = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
      queued.incrementAndGet()
      submit {
        queued.decrementAndGet()
        held.addLast(Chunk(receivedSamples, pcm))
        receivedSamples += pcm.size
        while (held.isNotEmpty() && held.first().endSample <= receivedSamples - HOLD_SAMPLES) writeChunk(held.removeFirst())
      }
    }

    override fun word(word: WordId, startMs: Long?, endMs: Long?, status: Recording.WordStatus, phonemes: String, endsSura: Boolean) {
      submit {
        val open = current
        // a sura the session moved to without its end being heard (e.g. the user skipped ahead)
        if (split == RecordingSplit.BY_SURA && open != null && open.words.isNotEmpty() && open.sura != word.sura) {
          endRecording(open, startMs ?: endMs ?: (receivedSamples / SAMPLES_PER_MS), SuraAyah(word.sura, word.ayah))
        }
        val target = current ?: open(held.firstOrNull()?.startSample ?: receivedSamples)
        val offset = target.startSample / SAMPLES_PER_MS
        target.words += Recording.Word(word, startMs?.minus(offset), endMs?.minus(offset), status, phonemes)
        target.end = SuraAyah(word.sura, word.ayah)
        // the sura's recording ends a little after its last word (the end of a long madd), before
        // whatever comes next (the basmala, the next sura, ruku...)
        if (split == RecordingSplit.BY_SURA && endsSura && word.sura < LAST_SURA) {
          val at = (endMs ?: startMs ?: (receivedSamples / SAMPLES_PER_MS)) + SPLIT_AFTER_MS
          endRecording(target, at, SuraAyah(word.sura + 1, 1))
        }
      }
    }

    /** Ends [open] at [atMs] of the session's audio (as far back as is held); [next] starts after it. */
    private fun endRecording(open: Open, atMs: Long, next: SuraAyah) {
      while (held.isNotEmpty() && held.first().startSample < atMs * SAMPLES_PER_MS) writeChunk(held.removeFirst())
      close(open)
      nextStart = next
    }

    override fun pause() {
      submit {
        while (held.isNotEmpty()) writeChunk(held.removeFirst())
        val open = current ?: return@submit
        if (open.closePart()) save(open.recording())
      }
    }

    override fun finish() {
      submit {
        while (held.isNotEmpty()) writeChunk(held.removeFirst())
        current?.let { close(it) }
        synchronized(active) { active.remove(session) }
        (executor as? ExecutorService)?.shutdown()
      }
    }

    /** What has been recorded so far (after whatever was queued before), or null once it's over. */
    suspend fun snapshot(): List<Recording>? = suspendCancellableCoroutine { continuation ->
      val queuedRun = submit {
        val open = current?.takeIf { it.parts.isNotEmpty() }?.recording()
        continuation.resume(done + listOfNotNull(open))
      }
      if (!queuedRun) continuation.resume(null)
    }

    private fun writeChunk(chunk: Chunk) {
      val open = current ?: open(chunk.startSample)
      open.append(chunk.pcm)
    }

    private fun open(startSample: Long): Open =
      Open(UUID.randomUUID().toString(), clock(), nextStart, startSample).also { current = it }

    private fun close(open: Open) {
      current = null
      open.closePart()
      if (open.words.isEmpty()) {
        open.parts.forEach { it.file.delete() }
        return
      }
      if (open.parts.isEmpty()) return
      val recording = open.recording()
      save(recording)
      applyRetention(recording)
      done += recording
    }

    /** A recording being written; its times count from [startSample] of the session's audio. */
    private inner class Open(val id: String, val startedAt: Long, val start: SuraAyah, val startSample: Long) {
      val sura: Int get() = start.sura
      var end = start
      val parts = ArrayList<Recording.Part>()
      val words = ArrayList<Recording.Word>()
      private var writer: AudioFileWriter? = null
      private var file: File? = null
      private var partSamples = 0L
      private var failed = false

      fun append(pcm: ShortArray) {
        if (failed) return
        val writer = writer ?: try {
          directory.mkdirs()
          val next = File(directory, "${id}_${parts.size + 1}.m4a")
          file = next
          writers(next).also { writer = it }
        } catch (_: Exception) {
          // no encoder on this phone: the session goes on without recording
          failed = true
          return
        }
        writer.write(pcm, pcm.size)
        partSamples += pcm.size
      }

      /** True if a part was added. */
      fun closePart(): Boolean {
        val writer = writer ?: return false
        this.writer = null
        val saved = runCatching { writer.close() }.getOrDefault(false)
        val file = file
        if (saved && file != null) parts += Recording.Part(file, partSamples / SAMPLES_PER_MS) else file?.delete()
        partSamples = 0
        return saved
      }

      fun recording() = Recording(id, session, mode, startedAt, start, end, parts.toList(), words.toList())
    }

    private fun submit(task: () -> Unit): Boolean = try {
      executor.execute(task)
      true
    } catch (_: RejectedExecutionException) {
      false // already finished
    }
  }

  private class Chunk(val startSample: Long, val pcm: ShortArray) {
    val endSample: Long get() = startSample + pcm.size
  }

  companion object {
    /** A store with its own writers and executors (e.g. running tasks right away), for tests. */
    internal fun forTest(
      context: Context,
      preferences: MemorizePreferences,
      writers: (File) -> AudioFileWriter,
      executors: () -> Executor,
      clock: () -> Long,
    ) = RecordingStore(context, preferences, writers, executors, clock)

    private const val METADATA = ".json"
    private const val SAMPLES_PER_MS = 16L
    // longer than the recognizer takes to report a word, even on a slow phone
    private const val HOLD_MS = 4_000L
    private const val HOLD_SAMPLES = HOLD_MS * SAMPLES_PER_MS
    /** How long after a sura's last word its recording ends (the end of a long madd). */
    private const val SPLIT_AFTER_MS = 500L
    private const val LAST_SURA = 114
    // 30 seconds of 100 ms frames
    private const val MAX_QUEUED_FRAMES = 300
  }
}
