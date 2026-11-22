package com.quran.labs.androidquran.feature.reading

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReadingPageSettleTrackerTest {

  @Test
  fun `the first settled page does not create history`() {
    val tracker = ReadingPageSettleTracker()

    assertThat(tracker.onSettled(page = 10, position = 594)).isNull()
  }

  @Test
  fun `stopping on the next page does not create history`() {
    val tracker = ReadingPageSettleTracker()
    tracker.anchor(page = 10, position = 594)

    assertThat(tracker.onSettled(page = 11, position = 593)).isNull()
    assertThat(tracker.onSettled(page = 12, position = 592)).isNull()
  }

  @Test
  fun `stopping after a fast flip records the page that was being read`() {
    val tracker = ReadingPageSettleTracker()
    tracker.anchor(page = 10, position = 594)

    val jump = tracker.onSettled(page = 40, position = 564)

    assertThat(jump).isEqualTo(
      SettleJump(leaving = ReadingPlace(10), destination = ReadingPlace(40))
    )
  }

  @Test
  fun `one dual-page swipe is adjacent even when the page number moves by two`() {
    val tracker = ReadingPageSettleTracker()
    tracker.anchor(page = 2, position = 301)

    assertThat(tracker.onSettled(page = 4, position = 300)).isNull()
  }

  @Test
  fun `a later fast flip starts from the page they last stopped on`() {
    val tracker = ReadingPageSettleTracker()
    tracker.anchor(page = 10, position = 594)
    tracker.onSettled(page = 11, position = 593)

    val jump = tracker.onSettled(page = 80, position = 524)

    assertThat(jump).isEqualTo(
      SettleJump(leaving = ReadingPlace(11), destination = ReadingPlace(80))
    )
  }

  @Test
  fun `anchoring again drops a page the reader only passed through`() {
    val tracker = ReadingPageSettleTracker()
    tracker.anchor(page = 10, position = 594)
    tracker.anchor(page = 15, position = 589)

    assertThat(tracker.onSettled(page = 16, position = 588)).isNull()
  }
}
