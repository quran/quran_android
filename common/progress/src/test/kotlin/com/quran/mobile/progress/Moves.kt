package com.quran.mobile.progress

import com.quran.mobile.progress.log.BookmarkPosition
import com.quran.mobile.progress.log.MoveKind
import com.quran.mobile.progress.log.RecordedMove

/**
 * A bookmark's history for tests, in UTC days: `Moves.of { day(3) { move(262, 15, 1) } }`. Each day's
 * rows start at 08:00, a minute apart unless [after] says otherwise.
 */
internal class Moves {
  val rows = mutableListOf<RecordedMove>()
  private var day = 0L
  private var atMs = 0L
  private var gapMs = 60_000L

  fun day(day: Long, block: Moves.() -> Unit) {
    this.day = day
    atMs = day * DAY + 8 * HOUR - 60_000L
    block()
  }

  /** The next row comes [seconds] after the last one. */
  fun after(seconds: Long) {
    gapMs = seconds * 1000
  }

  fun baseline(page: Int, sura: Int, ayah: Int, pageType: String = "madani") =
    add(MoveKind.BASELINE, BookmarkPosition(page, pageType, sura, ayah))

  fun move(page: Int, sura: Int, ayah: Int, pageType: String = "madani") =
    add(MoveKind.MOVE, BookmarkPosition(page, pageType, sura, ayah))

  fun clear() = add(MoveKind.CLEAR, null)
  fun mark() = add(MoveKind.CYCLE_MARK, null)

  private fun add(kind: MoveKind, position: BookmarkPosition?) {
    atMs += gapMs
    gapMs = 60_000L
    rows += RecordedMove(atMs, day, kind, position)
  }

  companion object {
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR
    fun of(block: Moves.() -> Unit): List<RecordedMove> = Moves().apply(block).rows
  }
}
