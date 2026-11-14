package com.quran.mobile.feature.progress.ui

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Days shown, faded and labelled "Sample data", until there is history to show. */
internal object SampleProgress {
  private val pages = listOf(16, 22, 19, 26, 10, 0, 24, 21, 27, 15, 25, 24)
  private val minutes = listOf(34, 49, 38, 55, 22, 3, 52, 42, 59, 31, 54, 58)
  private val accuracy = listOf(88, 91, 84, 0, 93, 95, 90, 0, 96, 92, 94, 97)
  private val hints = listOf(9, 6, 11, 0, 5, 3, 6, 0, 2, 4, 3, 2)
  private val prayers = listOf(5, 4, 5, 5, 3, 5, 5, 4, 5, 5, 5, 2)
  private val salahHints = listOf(2, 3, 1, 0, 4, 1, 0, 2, 1, 0, 1, 0)

  /** [columns] with sample values in place of their own, for the days up to today. */
  fun reading(columns: ImmutableList<DayColumn>): ImmutableList<DayColumn> = fill(columns, pages, minutes)

  fun memorizing(columns: ImmutableList<DayColumn>): ImmutableList<DayColumn> = fill(columns, accuracy, hints)

  fun salah(columns: ImmutableList<DayColumn>): ImmutableList<DayColumn> = fill(columns, prayers, salahHints)

  private fun fill(columns: List<DayColumn>, above: List<Int>, below: List<Int>): ImmutableList<DayColumn> {
    val today = columns.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: (columns.size - 1)
    return columns.mapIndexed { index, column ->
      if (column.above == null) {
        column
      } else {
        // the last sample day lands on today
        val sample = (above.size - 1 + index - today).mod(above.size)
        column.copy(above = above[sample], below = below[sample], empty = above[sample] == 0 && below[sample] == 0)
      }
    }.toImmutableList()
  }
}
