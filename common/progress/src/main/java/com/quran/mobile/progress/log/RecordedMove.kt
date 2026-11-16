package com.quran.mobile.progress.log

/** Where a reading bookmark is: its page in [pageType], and the first ayah it marks. */
data class BookmarkPosition(val page: Int, val pageType: String, val sura: Int, val ayah: Int)

enum class MoveKind {
  /** Where the bookmark was first seen, or seen again in another page type: not a move. */
  BASELINE,
  MOVE,
  CLEAR,
  /** The user started a new cycle by hand. */
  CYCLE_MARK,
}

/** One row of the bookmark's history (see BookmarkMove.sq). [position] is null for CLEAR and CYCLE_MARK. */
data class RecordedMove(val atMs: Long, val day: Long, val kind: MoveKind, val position: BookmarkPosition?)
