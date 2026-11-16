package com.quran.mobile.progress.cycle

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.progress.Moves
import org.junit.Test

class CycleDetectorTest {

  @Test
  fun anNasBackToAlFatihaClosesTheCycleAndStartsAnother() {
    val moves = Moves.of {
      day(1) { baseline(1, 1, 1) }
      day(20) { move(604, 114, 1) }
      day(21) { move(1, 1, 1) }
    }
    val cycles = CycleDetector.cycles(moves)
    assertThat(cycles).hasSize(2)
    assertThat(cycles[0].partial).isFalse()
    assertThat(cycles[0].startDay).isEqualTo(1)
    assertThat(cycles[0].endDay).isEqualTo(21)
    assertThat(cycles[0].wrapped).isTrue()
    assertThat(cycles[1].startDay).isEqualTo(21)
    assertThat(cycles[1].isOpen).isTrue()
    assertThat(cycles[1].partial).isFalse()
  }

  @Test
  fun anyWhereInJuzAmmaToTheFirstPageOfAlBaqaraIsAWrap() {
    val moves = Moves.of {
      day(1) { baseline(582, 78, 1) }
      day(2) { move(2, 2, 1) }
    }
    val cycles = CycleDetector.cycles(moves)
    assertThat(cycles).hasSize(2)
    assertThat(cycles[0].partial).isTrue()
    assertThat(cycles[1].startDay).isEqualTo(2)
  }

  @Test
  fun goingBackToAlFatihaFromJuz29IsNotAWrap() {
    val moves = Moves.of {
      day(1) { baseline(562, 67, 1) }
      day(2) { move(1, 1, 1) }
    }
    assertThat(CycleDetector.cycles(moves)).hasSize(1)
  }

  @Test
  fun aWrapUndoneRightAfterIsNotOne() {
    val moves = Moves.of {
      day(1) {
        baseline(600, 109, 1)
        move(1, 1, 1)
        after(10)
        move(600, 109, 1)
      }
    }
    assertThat(CycleDetector.cycles(moves)).hasSize(1)
  }

  @Test
  fun aWrapStillCountsWhenTheNextMoveComesLater() {
    val moves = Moves.of {
      day(1) {
        baseline(600, 109, 1)
        move(1, 1, 1)
        after(120)
        move(3, 2, 6)
      }
    }
    assertThat(CycleDetector.cycles(moves)).hasSize(2)
  }

  @Test
  fun theFirstCycleSeenIsPartialUnlessItStartsAtTheStart() {
    val partial = CycleDetector.cycles(Moves.of { day(1) { baseline(262, 15, 1) } })
    assertThat(partial.single().partial).isTrue()
    val atStart = CycleDetector.cycles(Moves.of { day(1) { baseline(1, 1, 1) } })
    assertThat(atStart.single().partial).isFalse()
  }

  @Test
  fun aCycleStartedByHandEndsTheOpenOne() {
    val moves = Moves.of {
      day(1) { baseline(262, 15, 1) }
      day(5) { mark() }
      day(6) { move(270, 15, 50) }
    }
    val cycles = CycleDetector.cycles(moves)
    assertThat(cycles).hasSize(2)
    assertThat(cycles[0].endDay).isEqualTo(5)
    assertThat(cycles[0].wrapped).isFalse()
    assertThat(cycles[1].startDay).isEqualTo(5)
    assertThat(cycles[1].partial).isFalse()
  }

  @Test
  fun aChangeOfPageTypeIsNotAMove() {
    // Warsh numbers its pages differently; the end zone is by sura, so the wrap is still seen
    val moves = Moves.of {
      day(1) { baseline(600, 109, 1) }
      day(2) { baseline(556, 109, 1, pageType = "warsh") }
      day(3) { move(1, 1, 1, pageType = "warsh") }
    }
    assertThat(CycleDetector.cycles(moves)).hasSize(2)
  }

  @Test
  fun aClearDoesNotEndTheCycle() {
    val moves = Moves.of {
      day(1) { baseline(600, 109, 1) }
      day(2) { clear() }
      day(3) { move(1, 1, 1) }
    }
    // the bookmark went from the end to the start, with a clear in between
    assertThat(CycleDetector.cycles(moves)).hasSize(2)
  }
}
