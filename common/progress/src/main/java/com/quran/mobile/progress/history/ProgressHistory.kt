package com.quran.mobile.progress.history

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quran.data.di.AppScope
import com.quran.mobile.progress.db.ProgressDatabase
import com.quran.mobile.progress.log.RecordedMove
import com.quran.mobile.progress.log.toRecorded
import com.quran.mobile.progress.session.AyahResult
import com.quran.mobile.progress.session.SessionRecord
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Reading time on one local day, credited to [slot] by its move at [creditedAt] (both null until a
 * bookmark moves).
 */
data class DayMinutes(val slot: String?, val day: Long, val ms: Long, val creditedAt: Long? = null)

/** A session from the log, with the local day it started on. */
data class LoggedSession(val day: Long, val record: SessionRecord)

/** What Progress has kept, as it changes. */
@SingleIn(AppScope::class)
class ProgressHistory @Inject constructor(private val database: ProgressDatabase) {

  /** Every bookmark's history, by slot name, oldest first. */
  fun moves(): Flow<Map<String, List<RecordedMove>>> =
    database.bookmarkMoveQueries.allMoves().asFlow().mapToList(Dispatchers.IO)
      .map { rows -> rows.groupBy({ it.slot }, { it.toRecorded() }) }

  fun minutes(): Flow<List<DayMinutes>> =
    database.readingTimeQueries.minutesBySlotAndDay().asFlow().mapToList(Dispatchers.IO)
      .map { rows -> rows.map { DayMinutes(it.slot, it.local_day, it.ms ?: 0L, it.credited_at) } }

  fun sessions(): Flow<List<LoggedSession>> = combine(
    database.memorizeSessionQueries.sessions().asFlow().mapToList(Dispatchers.IO),
    database.memorizeSessionQueries.sessionAyahs().asFlow().mapToList(Dispatchers.IO),
  ) { sessions, ayahs ->
    val bySession = ayahs.groupBy { it.session_id }
    sessions.map { row ->
      LoggedSession(
        day = row.local_day,
        record = SessionRecord(
          id = row.id,
          mode = row.mode,
          startedAtMs = row.started_at_ms,
          endedAtMs = row.ended_at_ms,
          listenedMs = row.listened_ms,
          startSura = row.start_sura.toInt(),
          startAyah = row.start_ayah.toInt(),
          endSura = row.end_sura.toInt(),
          endAyah = row.end_ayah.toInt(),
          words = row.words.toInt(),
          recited = row.recited.toInt(),
          hinted = row.hinted.toInt(),
          missed = row.missed.toInt(),
          mistakes = row.mistakes.toInt(),
          finished = row.finished != 0L,
          recordingId = row.recording_id,
          ayahs = bySession[row.id].orEmpty().map {
            AyahResult(
              sura = it.sura.toInt(),
              ayah = it.ayah.toInt(),
              ayahWords = it.ayah_words.toInt(),
              words = it.words.toInt(),
              hinted = it.hinted.toInt(),
              missed = it.missed.toInt(),
              mistakes = it.mistakes.toInt(),
            )
          },
        ),
      )
    }
  }

  /** Forgets everything Progress kept: bookmark moves, reading time and sessions. */
  suspend fun clear() = withContext(Dispatchers.IO) {
    database.transaction {
      database.bookmarkMoveQueries.deleteAllMoves()
      database.readingTimeQueries.deleteAllTime()
      database.memorizeSessionQueries.deleteAllSessionAyahs()
      database.memorizeSessionQueries.deleteAllSessions()
    }
  }
}
