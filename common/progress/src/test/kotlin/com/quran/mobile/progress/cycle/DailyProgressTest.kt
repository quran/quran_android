package com.quran.mobile.progress.cycle

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.progress.Moves
import com.quran.mobile.progress.log.BookmarkPosition
import org.junit.Test

class DailyProgressTest {
  private val quranInfo = QuranInfo(MadaniDataSource())
  private val pageOf: (BookmarkPosition) -> Int = { position ->
    if (position.pageType == "madani") position.page else quranInfo.getPageFromSuraAyah(position.sura, position.ayah)
  }

  private fun pages(moves: List<com.quran.mobile.progress.log.RecordedMove>, cycle: Int = 0): Map<Long, Int> {
    val cycles = CycleDetector.cycles(moves)
    return DailyProgress.pagesPerDay(cycles[cycle], moves, pageOf, firstPage = 1, lastPage = 604)
  }

  @Test
  fun countsTheNetMoveOfEachDay() {
    val moves = Moves.of {
      day(1) { baseline(1, 1, 1) }
      day(2) {
        move(10, 2, 62)
        move(5, 2, 30)
        move(22, 2, 142)
      }
      day(4) { move(40, 3, 1) }
    }
    assertThat(pages(moves)).containsExactly(1L, 0, 2L, 21, 4L, 18)
  }

  @Test
  fun aMoveBackIsZeroNotNegative() {
    val moves = Moves.of {
      day(1) { baseline(50, 3, 75) }
      day(2) { move(40, 3, 1) }
    }
    assertThat(pages(moves)[2L]).isEqualTo(0)
  }

  @Test
  fun theDayOfAWrapCountsThePagesToTheEndForTheOldCycleAndTheRestForTheNew() {
    val moves = Moves.of {
      day(1) { baseline(590, 87, 1) }
      day(2) {
        move(600, 109, 1)
        move(2, 2, 1)
      }
    }
    // 590 -> 600 -> the end (604 read): 15 pages; then 1 -> 2: 1 page
    assertThat(pages(moves, 0)[2L]).isEqualTo(15)
    assertThat(pages(moves, 1)).containsExactly(2L, 1)
  }

  @Test
  fun aChangeOfPageTypeMeasuresAgainFromThere() {
    val moves = Moves.of {
      day(1) { baseline(100, 5, 1) }
      day(2) {
        move(110, 5, 77)
        baseline(120, 5, 77, pageType = "other")
        move(125, 5, 100, pageType = "other")
      }
    }
    // 10 pages in madani, then 5:77 to 5:100 in madani's pages
    assertThat(pages(moves)[2L]).isEqualTo(10 + (quranInfo.getPageFromSuraAyah(5, 100) - quranInfo.getPageFromSuraAyah(5, 77)))
  }

  @Test
  fun aClearedBookmarkPlacedAgainCountsFromWhereItWas() {
    val moves = Moves.of {
      day(1) { baseline(100, 5, 1) }
      day(2) { clear() }
      day(3) { move(130, 6, 1) }
    }
    assertThat(pages(moves)[3L]).isEqualTo(30)
  }
}
