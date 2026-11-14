package com.quran.mobile.progress.session

import com.quran.data.di.AppScope
import com.quran.mobile.progress.ProgressPreferences
import com.quran.mobile.progress.db.ProgressDatabase
import com.quran.mobile.progress.time.ProgressClock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Counts for one ayah of a session: of its [ayahWords] words, the session went through [words]. */
data class AyahResult(
  val sura: Int,
  val ayah: Int,
  val ayahWords: Int,
  val words: Int,
  val hinted: Int,
  val missed: Int,
  val mistakes: Int,
)

/**
 * What one Memorize, Recitation or Salah session came to: counts only, no audio and no words heard.
 *
 * @param mode the session's mode's name (MEMORIZE, RECITE or SALAH).
 * @param listenedMs audio the session heard.
 * @param finished whether the passage was recited to its end.
 */
data class SessionRecord(
  val id: String,
  val mode: String,
  val startedAtMs: Long,
  val endedAtMs: Long,
  val listenedMs: Long,
  val startSura: Int,
  val startAyah: Int,
  val endSura: Int,
  val endAyah: Int,
  val words: Int,
  val recited: Int,
  val hinted: Int,
  val missed: Int,
  val mistakes: Int,
  val finished: Boolean,
  val recordingId: String?,
  val ayahs: List<AyahResult>,
)

/** Keeps session results on the device, when the user lets it (see [ProgressPreferences.sessionLog]). */
@SingleIn(AppScope::class)
class SessionLog @Inject constructor(
  private val database: ProgressDatabase,
  private val preferences: ProgressPreferences,
  private val clock: ProgressClock,
) {
  val enabled: Boolean get() = preferences.sessionLog

  suspend fun record(record: SessionRecord) {
    if (!enabled) return
    withContext(Dispatchers.IO) {
      val queries = database.memorizeSessionQueries
      // the session's day by the progress clock, which a debuggable build may move
      val shift = clock.nowMs() - System.currentTimeMillis()
      queries.transaction {
        queries.insertSession(
          record.id, record.mode, record.startedAtMs + shift, clock.dayOf(record.startedAtMs + shift),
          record.endedAtMs + shift, record.listenedMs, record.startSura.toLong(), record.startAyah.toLong(),
          record.endSura.toLong(), record.endAyah.toLong(), record.words.toLong(), record.recited.toLong(),
          record.hinted.toLong(), record.missed.toLong(), record.mistakes.toLong(), if (record.finished) 1L else 0L,
          record.recordingId,
        )
        record.ayahs.forEach {
          queries.insertSessionAyah(
            record.id, it.sura.toLong(), it.ayah.toLong(), it.ayahWords.toLong(), it.words.toLong(), it.hinted.toLong(),
            it.missed.toLong(), it.mistakes.toLong(),
          )
        }
      }
    }
  }

  /** Forgets every session. */
  suspend fun clear() = withContext(Dispatchers.IO) {
    database.memorizeSessionQueries.transaction {
      database.memorizeSessionQueries.deleteAllSessionAyahs()
      database.memorizeSessionQueries.deleteAllSessions()
    }
  }
}
