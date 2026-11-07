package com.quran.labs.androidquran.feature.reading

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReadingHistoryTest {

  @Test
  fun `control is hidden before the first jump`() {
    val history = ReadingHistory()

    assertThat(history.chrome()).isEqualTo(ReadingHistoryChrome())
    assertThat(history.chrome().isVisible).isFalse()
    assertThat(history.back()).isNull()
    assertThat(history.forward()).isNull()
    assertThat(history.snapshot().entries).isEmpty()
  }

  @Test
  fun `only back is available at the top of the stack`() {
    val history = ReadingHistory()

    history.recordJump(place(10), place(200))
    assertThat(pages(history)).containsExactly(10, 200).inOrder()
    assertThat(history.snapshot().index).isEqualTo(1)
    assertThat(history.chrome()).isEqualTo(
      ReadingHistoryChrome(ReadingHistoryDirection.BACK, showsCaret = false)
    )

    history.recordJump(place(200), place(400))
    assertThat(pages(history)).containsExactly(10, 200, 400).inOrder()
    assertThat(history.snapshot().index).isEqualTo(2)
    assertThat(history.chrome()).isEqualTo(
      ReadingHistoryChrome(ReadingHistoryDirection.BACK, showsCaret = false)
    )
  }

  @Test
  fun `only forward is available at the bottom of the stack`() {
    val history = stackAt(10, 200, 400)

    assertThat(history.back()).isEqualTo(place(200))
    assertThat(history.back()).isEqualTo(place(10))
    assertThat(history.snapshot().index).isEqualTo(0)
    assertThat(history.chrome()).isEqualTo(
      ReadingHistoryChrome(ReadingHistoryDirection.FORWARD, showsCaret = false)
    )
    assertThat(history.back()).isNull()
  }

  @Test
  fun `primary direction stays put while moving through the middle`() {
    val history = stackAt(10, 200, 400)

    assertThat(history.back()).isEqualTo(place(200))
    assertThat(history.chrome()).isEqualTo(
      ReadingHistoryChrome(ReadingHistoryDirection.BACK, showsCaret = true)
    )

    assertThat(history.back()).isEqualTo(place(10))
    assertThat(history.chrome().primary).isEqualTo(ReadingHistoryDirection.FORWARD)

    assertThat(history.forward()).isEqualTo(place(200))
    assertThat(history.chrome()).isEqualTo(
      ReadingHistoryChrome(ReadingHistoryDirection.FORWARD, showsCaret = true)
    )

    assertThat(history.forward()).isEqualTo(place(400))
    assertThat(history.chrome()).isEqualTo(
      ReadingHistoryChrome(ReadingHistoryDirection.BACK, showsCaret = false)
    )
  }

  @Test
  fun `caret is only offered when both directions exist`() {
    val history = stackAt(10, 200, 400)

    assertThat(history.chrome().showsCaret).isFalse()

    history.back()
    assertThat(history.chrome().showsCaret).isTrue()

    history.back()
    assertThat(history.chrome().showsCaret).isFalse()

    history.forward()
    assertThat(history.chrome().showsCaret).isTrue()
    assertThat(history.chrome().primary).isEqualTo(ReadingHistoryDirection.FORWARD)
  }

  @Test
  fun `jump from the middle drops forward entries and ends on back`() {
    val history = stackAt(10, 200, 400)
    history.back()
    history.back()
    history.forward()
    assertThat(history.chrome().primary).isEqualTo(ReadingHistoryDirection.FORWARD)

    history.recordJump(place(200), place(50))

    assertThat(pages(history)).containsExactly(10, 200, 50).inOrder()
    assertThat(history.snapshot().index).isEqualTo(2)
    assertThat(history.chrome()).isEqualTo(
      ReadingHistoryChrome(ReadingHistoryDirection.BACK, showsCaret = false)
    )
  }

  @Test
  fun `jump after a swipe records the page being left`() {
    val history = stackAt(10, 200, 400)

    history.recordJump(place(205), place(300))

    assertThat(pages(history)).containsExactly(10, 200, 400, 205, 300).inOrder()
    assertThat(history.snapshot().index).isEqualTo(4)
    assertThat(history.chrome().primary).isEqualTo(ReadingHistoryDirection.BACK)
    assertThat(history.chrome().showsCaret).isFalse()
  }

  @Test
  fun `swipe correction is applied before the jump when not at the top`() {
    val history = stackAt(10, 200, 400)
    history.back()

    history.recordJump(place(210), place(50))

    assertThat(pages(history)).containsExactly(10, 200, 210, 50).inOrder()
    assertThat(history.back()).isEqualTo(place(210))
    assertThat(history.back()?.page).isEqualTo(200)
  }

  @Test
  fun `back and forward do not push entries`() {
    val history = stackAt(10, 200, 400)
    val before = history.snapshot().entries

    assertThat(history.back()).isEqualTo(place(200))
    assertThat(history.back()).isEqualTo(place(10))
    assertThat(history.forward()).isEqualTo(place(200))
    assertThat(history.forward()).isEqualTo(place(400))
    assertThat(history.forward()).isNull()

    assertThat(history.snapshot().entries).isEqualTo(before)
    assertThat(pages(history)).containsExactly(10, 200, 400).inOrder()
  }

  @Test
  fun `jumping to the current place does not truncate or duplicate`() {
    val history = stackAt(10, 200, 400)
    history.back()

    history.recordJump(place(200), place(200))

    assertThat(pages(history)).containsExactly(10, 200, 400).inOrder()
    assertThat(history.snapshot().index).isEqualTo(1)
    assertThat(history.chrome().showsCaret).isTrue()
  }

  @Test
  fun `highlighted jumps are stored and restored`() {
    val history = ReadingHistory()
    val highlighted = ReadingPlace(page = 200, sura = 16, ayah = 1)

    history.recordJump(place(10), highlighted)
    assertThat(history.snapshot().entries).containsExactly(place(10), highlighted).inOrder()

    assertThat(history.back()).isEqualTo(place(10))
    assertThat(history.forward()).isEqualTo(highlighted)

    val saved = history.snapshot()
    val restored = ReadingHistory()
    restored.restore(saved)

    assertThat(restored.snapshot()).isEqualTo(saved)
    assertThat(restored.chrome()).isEqualTo(history.chrome())
    assertThat(restored.snapshot().entries.last().hasHighlight).isTrue()
  }

  @Test
  fun `save and restore keeps the sticky direction in the middle`() {
    val history = stackAt(10, 200, 400)
    history.back()
    history.back()
    history.forward()
    assertThat(history.chrome().primary).isEqualTo(ReadingHistoryDirection.FORWARD)
    assertThat(history.chrome().showsCaret).isTrue()

    val saved = history.snapshot()
    val restored = ReadingHistory()
    restored.restore(saved)

    assertThat(restored.snapshot()).isEqualTo(saved)
    assertThat(restored.chrome()).isEqualTo(
      ReadingHistoryChrome(ReadingHistoryDirection.FORWARD, showsCaret = true)
    )
    assertThat(restored.forward()).isEqualTo(place(400))
    assertThat(restored.chrome().primary).isEqualTo(ReadingHistoryDirection.BACK)
  }

  @Test
  fun `restore ignores an empty or invalid snapshot`() {
    val history = stackAt(10, 200)
    history.restore(ReadingHistorySnapshot(emptyList(), index = 0, ReadingHistoryDirection.BACK))

    assertThat(history.chrome().isVisible).isFalse()
    assertThat(history.snapshot().entries).isEmpty()

    history.recordJump(place(10), place(200))
    history.restore(
      ReadingHistorySnapshot(
        entries = listOf(place(10), place(200)),
        index = 5,
        primaryDirection = ReadingHistoryDirection.FORWARD
      )
    )
    assertThat(history.chrome().isVisible).isFalse()
  }

  private fun stackAt(vararg pages: Int): ReadingHistory {
    val history = ReadingHistory()
    for (i in 0 until pages.lastIndex) {
      history.recordJump(place(pages[i]), place(pages[i + 1]))
    }
    return history
  }

  private fun place(page: Int) = ReadingPlace(page)

  private fun pages(history: ReadingHistory) = history.snapshot().entries.map { it.page }
}
