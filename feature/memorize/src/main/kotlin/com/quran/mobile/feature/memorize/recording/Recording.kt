package com.quran.mobile.feature.memorize.recording

import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.mobile.feature.memorize.tracker.WordId
import com.quran.mobile.feature.memorize.tracker.engine.RecognizedToken
import java.io.File

/**
 * A recording the user chose to make of their own recitation: one sura of a session, or the whole
 * session (see [RecordingSplit]). Each stretch of listening between pauses is its own file, played
 * one after the other. [words] align the recitation with the Quran, e.g. for training speech models.
 */
data class Recording(
  val id: String,
  /** The memorization session it was recorded in. */
  val session: String,
  val mode: MemorizeState.Mode,
  /** When recording started (wall clock). */
  val startedAt: Long,
  val start: SuraAyah,
  /** The last ayah reached. */
  val end: SuraAyah,
  val parts: List<Part>,
  /** Every word the session went through, with times in ms from the start of the recording. */
  val words: List<Word>,
  /** Recorded to help improve the speech models ("Record and contribute"). */
  val contribute: Boolean = false,
  /** What the recognizer heard and what the session did ([RecordingDetails]), kept when contributing. */
  val details: File? = null,
) {
  data class Part(val file: File, val durationMs: Long)

  data class Word(
    val id: WordId,
    val startMs: Long?,
    val endMs: Long?,
    val status: WordStatus,
    val phonemes: String,
    /** How many times a mistake was marked on it; null in recordings made before this was kept. */
    val mistakes: Int? = 0,
  )

  enum class WordStatus {
    /** Recited by the user. */
    RECITED,
    /** Given as a hint (spoken by the hint voice). */
    HINTED,
    /** Skipped by the user. */
    MISSED,
  }

  val durationMs: Long get() = parts.sumOf { it.durationMs }

  val sizeBytes: Long get() = parts.sumOf { it.file.length() } + (details?.length() ?: 0L)

  /** The ayah being recited [positionMs] into the recording. */
  fun ayahAt(positionMs: Long): SuraAyah {
    val word = words.lastOrNull { (it.startMs ?: it.endMs ?: Long.MAX_VALUE) <= positionMs } ?: return start
    return SuraAyah(word.id.sura, word.id.ayah)
  }
}

/** A session's recordings (one per sura, or one for all of it), in the order they were made. */
data class RecordingSession(val recordings: List<Recording>) {
  init {
    require(recordings.isNotEmpty())
  }

  private val first get() = recordings.first()

  val id: String get() = first.session
  val mode: MemorizeState.Mode get() = first.mode
  val startedAt: Long get() = first.startedAt
  val start: SuraAyah get() = first.start
  val end: SuraAyah get() = recordings.last().end
  val durationMs: Long get() = recordings.sumOf { it.durationMs }
  val sizeBytes: Long get() = recordings.sumOf { it.sizeBytes }
  val contribute: Boolean get() = recordings.any { it.contribute }

  private val words get() = recordings.asSequence().flatMap { it.words }

  val recited: Int get() = words.count { it.status == Recording.WordStatus.RECITED }
  val hinted: Int get() = words.count { it.status == Recording.WordStatus.HINTED }
  val missed: Int get() = words.count { it.status == Recording.WordStatus.MISSED }
  /** Words a mistake was marked on (unknown in recordings made before this was kept). */
  val mistakes: Int get() = words.count { (it.mistakes ?: 0) > 0 }
}

/**
 * Something a session did, kept with recordings made to contribute; [atMs] is in the session's audio
 * time (as the recognizer's tokens).
 */
data class SessionEvent(val atMs: Long, val kind: Kind, val word: WordId? = null, val detail: String? = null) {
  enum class Kind {
    /** The voice activity detector heard sound, or silence. */
    SPEECH,
    SILENCE,
    /** The tracker found the user said something other than [word] (detail: substitution, skip, elsewhere). */
    MISTAKE,
    /** A mistake mark was shown on [word], or taken off it. */
    MARK_SHOWN,
    MARK_CLEARED,
    /** Words were given from [word] on (detail: how many). */
    HINT,
    /** The hint voice started or stopped playing (the microphone hears it). */
    PROMPT_STARTED,
    PROMPT_ENDED,
    /** The user went back to [word] (detail: the last word repeated). */
    REPETITION,
    /** A phrase that isn't part of the passage (detail: basmala, takbir...). */
    PHRASE,
    /** The double-check listened again for [word] (detail: said or not said, and how long it took). */
    DOUBLE_CHECK,
    /** Hints stopped after too many in a row. */
    GAVE_UP,
    CALL_STARTED,
    CALL_ENDED,
    /** Listening was paused by the user (no audio until it resumes). */
    PAUSED,
  }
}

/** How recordings are cut. */
enum class RecordingSplit {
  /** A recording per sura recited in a session. */
  BY_SURA,
  /** A recording per session. */
  BY_SESSION,
}

/** Where a session's audio and progress go when it is being recorded. */
interface SessionRecorder {
  /** Identifies the session's recordings. */
  val session: String

  /**
   * Whether it keeps what the recognizer heard and what the session did ([heard], [event]), for
   * recordings made to contribute; the caller may skip them otherwise.
   */
  val keepsDetails: Boolean

  /** Audio the session heard (16 kHz mono), right after what came before; never blocks. */
  fun write(samples: FloatArray)

  /**
   * The session went through [word]; times are in ms of the session's audio (as written), and
   * [phonemes] is how the word is pronounced (in the speech model's phonetic script). [endsSura]
   * when it is the last word of its sura; [mistakes] how many times a mistake was marked on it.
   */
  fun word(
    word: WordId,
    startMs: Long?,
    endMs: Long?,
    status: Recording.WordStatus,
    phonemes: String,
    endsSura: Boolean,
    mistakes: Int = 0,
  )

  /** What the recognizer heard; token times are in ms of the session's audio. */
  fun heard(tokens: List<RecognizedToken>) {}

  fun event(event: SessionEvent) {}

  /** Listening paused: what was recorded so far can be played. */
  fun pause()

  /** The session is over. */
  fun finish()
}

/** Starts recording sessions; replaced by a fake in tests. */
fun interface Recorders {
  /**
   * With [contribute], the recordings are marked as made to help improve the speech models and keep
   * the session's details; [info] describes the session for them (settings, speech model).
   */
  fun start(start: SuraAyah, mode: MemorizeState.Mode, contribute: Boolean, info: Map<String, Any>): SessionRecorder?
}

/** Writes 16 kHz mono 16-bit audio to a file (encoded, see [AacFileWriter]). */
interface AudioFileWriter {
  fun write(pcm: ShortArray, count: Int)

  /** Finishes the file; true if it holds any audio. */
  fun close(): Boolean
}
