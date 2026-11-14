package com.quran.mobile.progress.cycle

import com.quran.mobile.progress.log.BookmarkPosition
import com.quran.mobile.progress.log.MoveKind
import com.quran.mobile.progress.log.RecordedMove

/**
 * One reading of the Quran by a reading bookmark, from Al-Fatiha to An-Nas.
 *
 * @param partial whether it started before Progress kept track, so its start isn't known.
 * @param endMs when it ended, or null while it is open.
 * @param wrapped whether it ended with the bookmark going from the last suras back to Al-Fatiha
 * (rather than a new cycle started by hand).
 */
data class Cycle(
  val startMs: Long,
  val startDay: Long,
  val partial: Boolean,
  val endMs: Long? = null,
  val endDay: Long? = null,
  val wrapped: Boolean = false,
) {
  val isOpen: Boolean get() = endMs == null
}

/** Works out a bookmark's cycles from its history; a new one starts when it wraps or by hand. */
object CycleDetector {
  /** A wrap undone this soon (e.g. with the reader's Undo) wasn't one. */
  const val UNDO_MS = 60_000L

  /** Juz 'Amma, where a reading ends. */
  fun isEndZone(position: BookmarkPosition): Boolean = position.sura >= 78

  /** Al-Fatiha or the first page of Al-Baqara, where a reading starts. */
  fun isStartZone(position: BookmarkPosition): Boolean =
    position.sura == 1 || (position.sura == 2 && position.ayah <= 5)

  /** Whether the move at [index] goes from the end back to the start and isn't undone right after. */
  fun isWrap(moves: List<RecordedMove>, index: Int, previous: BookmarkPosition?): Boolean {
    val move = moves[index]
    val position = move.position ?: return false
    if (move.kind != MoveKind.MOVE || previous == null) return false
    if (!isEndZone(previous) || !isStartZone(position)) return false
    val next = moves.drop(index + 1).firstOrNull { it.position != null } ?: return true
    val undone = next.kind == MoveKind.MOVE && next.atMs - move.atMs <= UNDO_MS && isEndZone(next.position!!)
    return !undone
  }

  fun cycles(moves: List<RecordedMove>): List<Cycle> {
    val cycles = mutableListOf<Cycle>()
    var open: Cycle? = null
    var previous: BookmarkPosition? = null
    moves.forEachIndexed { index, move ->
      when (move.kind) {
        MoveKind.CYCLE_MARK -> {
          open?.let { cycles += it.copy(endMs = move.atMs, endDay = move.day) }
          open = Cycle(move.atMs, move.day, partial = false)
        }
        MoveKind.BASELINE -> {
          if (open == null) {
            open = Cycle(move.atMs, move.day, partial = !isStartZone(move.position!!))
          }
          previous = move.position
        }
        MoveKind.MOVE -> {
          val current = open
          if (current == null) {
            open = Cycle(move.atMs, move.day, partial = true)
          } else if (isWrap(moves, index, previous)) {
            cycles += current.copy(endMs = move.atMs, endDay = move.day, wrapped = true)
            open = Cycle(move.atMs, move.day, partial = false)
          }
          previous = move.position
        }
        MoveKind.CLEAR -> Unit
      }
    }
    open?.let { cycles += it }
    return cycles
  }
}
