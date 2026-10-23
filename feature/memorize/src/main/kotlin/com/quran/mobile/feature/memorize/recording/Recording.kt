package com.quran.mobile.feature.memorize.recording

import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.mobile.feature.memorize.tracker.WordId
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
) {
  data class Part(val file: File, val durationMs: Long)

  data class Word(val id: WordId, val startMs: Long?, val endMs: Long?, val status: WordStatus, val phonemes: String)

  enum class WordStatus {
    /** Recited by the user. */
    RECITED,
    /** Given as a hint (spoken by the hint voice). */
    HINTED,
    /** Skipped by the user. */
    MISSED,
  }

  val durationMs: Long get() = parts.sumOf { it.durationMs }

  val sizeBytes: Long get() = parts.sumOf { it.file.length() }

  /** The ayah being recited [positionMs] into the recording. */
  fun ayahAt(positionMs: Long): SuraAyah {
    val word = words.lastOrNull { (it.startMs ?: it.endMs ?: Long.MAX_VALUE) <= positionMs } ?: return start
    return SuraAyah(word.id.sura, word.id.ayah)
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

  /** Audio the session heard (16 kHz mono), right after what came before; never blocks. */
  fun write(samples: FloatArray)

  /**
   * The session went through [word]; times are in ms of the session's audio (as written), and
   * [phonemes] is how the word is pronounced (in the speech model's phonetic script). [endsSura]
   * when it is the last word of its sura.
   */
  fun word(word: WordId, startMs: Long?, endMs: Long?, status: Recording.WordStatus, phonemes: String, endsSura: Boolean)

  /** Listening paused: what was recorded so far can be played. */
  fun pause()

  /** The session is over. */
  fun finish()
}

/** Starts recording sessions; replaced by a fake in tests. */
fun interface Recorders {
  fun start(start: SuraAyah, mode: MemorizeState.Mode): SessionRecorder?
}

/** Writes 16 kHz mono 16-bit audio to a file (encoded, see [AacFileWriter]). */
interface AudioFileWriter {
  fun write(pcm: ShortArray, count: Int)

  /** Finishes the file; true if it holds any audio. */
  fun close(): Boolean
}
