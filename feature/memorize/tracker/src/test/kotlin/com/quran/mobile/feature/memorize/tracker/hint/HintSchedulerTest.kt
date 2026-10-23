package com.quran.mobile.feature.memorize.tracker.hint

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.memorize.tracker.WordId
import com.quran.mobile.feature.memorize.tracker.hint.HintScheduler.Effect
import org.junit.Test

class HintSchedulerTest {

  private val word = WordId(67, 1, 3)

  @Test
  fun `a mistake is marked without revealing anything`() {
    val scheduler = armed()

    val effects = scheduler.onMistake(1_000, word)

    assertThat(effects).containsExactly(Effect.MarkMistake(word))
  }

  @Test
  fun `one word is hinted after the delay following a mistake`() {
    val scheduler = armed()
    scheduler.onMistake(1_000, word)

    assertThat(scheduler.onTick(4_999)).isEmpty()
    assertThat(scheduler.onTick(5_000)).containsExactly(Effect.ClearMistake(word), Effect.GiveHint(1))
  }

  @Test
  fun `hints escalate one word at a time until the user continues`() {
    val scheduler = armed()
    scheduler.onMistake(1_000, word)
    assertThat(scheduler.onTick(5_000)).contains(Effect.GiveHint(1))

    // no further hint while the first one is playing
    assertThat(scheduler.onTick(20_000)).isEmpty()
    scheduler.onHintFinished(21_000)

    assertThat(scheduler.onTick(24_999)).isEmpty()
    assertThat(scheduler.onTick(25_000)).containsExactly(Effect.GiveHint(1))
    scheduler.onHintFinished(26_000)

    // the user continues on their own: the escalation stops
    assertThat(scheduler.onWordRecited(27_000, atAyahBoundary = false)).isEmpty()
    assertThat(scheduler.onTick(30_999)).isEmpty()
  }

  @Test
  fun `recovering from a mistake clears the mark and cancels the hint`() {
    val scheduler = armed()
    scheduler.onMistake(1_000, word)

    assertThat(scheduler.onWordRecited(2_000, atAyahBoundary = false)).containsExactly(Effect.ClearMistake(word))
    assertThat(scheduler.nextDeadlineMs()).isEqualTo(6_000)
  }

  @Test
  fun `a pause only counts once the user has been silent`() {
    val scheduler = armed()
    scheduler.onWordRecited(1_000, atAyahBoundary = false)
    scheduler.onSpeechActivity(1_500, active = true)

    // still making sound (e.g. a long madd): no hint even after the delay
    assertThat(scheduler.onTick(10_000)).isEmpty()

    // the recognizer may still be about to hear the end of the last word
    scheduler.onSpeechActivity(10_000, active = false)
    assertThat(scheduler.nextDeadlineMs()).isEqualTo(12_000)
    assertThat(scheduler.onTick(12_000)).containsExactly(Effect.GiveHint(1))
  }

  @Test
  fun `a hint settles the mistake it was given for`() {
    val scheduler = armed()
    scheduler.onMistake(1_000, word)

    // e.g. the user asked for the word before the delay was over
    scheduler.onHintStarted()
    scheduler.onHintFinished(2_000)

    // no second hint for the same mistake; only the usual wait for the user to go on
    assertThat(scheduler.onTick(5_000)).isEmpty()
    assertThat(scheduler.nextDeadlineMs()).isEqualTo(6_000)
  }

  @Test
  fun `repetition keeps the user from being treated as stuck`() {
    val scheduler = armed()
    scheduler.onRepetition(3_000)

    assertThat(scheduler.nextDeadlineMs()).isEqualTo(7_000)
  }

  @Test
  fun `pauses at the end of an ayah are ignored when configured`() {
    val scheduler = HintScheduler(HintConfig(ayahBoundaryDelayMs = null))
    scheduler.arm(0, atAyahBoundary = false)
    scheduler.onWordRecited(1_000, atAyahBoundary = true)

    assertThat(scheduler.nextDeadlineMs()).isNull()

    // a mistake at the start of the next ayah is still hinted
    scheduler.onMistake(30_000, WordId(67, 2, 1))
    assertThat(scheduler.nextDeadlineMs()).isEqualTo(34_000)
  }

  @Test
  fun `pauses at the end of an ayah can take longer to count`() {
    val scheduler = HintScheduler(HintConfig(delayMs = 4_000, ayahBoundaryDelayMs = 8_000))
    scheduler.arm(0, atAyahBoundary = false)
    scheduler.onWordRecited(1_000, atAyahBoundary = true)

    assertThat(scheduler.nextDeadlineMs()).isEqualTo(9_000)
    scheduler.onWordRecited(10_000, atAyahBoundary = false)
    assertThat(scheduler.nextDeadlineMs()).isEqualTo(14_000)
  }

  @Test
  fun `a mistake can wait for the reciter to pause (salah)`() {
    val scheduler = HintScheduler(HintConfig(delayMs = 4_000, mistakesWaitForPause = true))
    scheduler.arm(0, atAyahBoundary = false)
    scheduler.onSpeechActivity(500, active = true)
    scheduler.onMistake(1_000, word)

    // still reciting fluently: no hint, however long it takes
    assertThat(scheduler.onTick(30_000)).isEmpty()

    scheduler.onSpeechActivity(30_000, active = false)
    assertThat(scheduler.nextDeadlineMs()).isEqualTo(32_000)
    assertThat(scheduler.onTick(32_000)).containsExactly(Effect.ClearMistake(word), Effect.GiveHint(1))
  }

  @Test
  fun `pause hints can be turned off while mistake hints stay on`() {
    val scheduler = HintScheduler(HintConfig(onPauses = false))
    scheduler.arm(0, atAyahBoundary = false)

    assertThat(scheduler.nextDeadlineMs()).isNull()
    scheduler.onMistake(1_000, word)
    assertThat(scheduler.nextDeadlineMs()).isEqualTo(5_000)
  }

  @Test
  fun `words per hint is configurable`() {
    val scheduler = HintScheduler(HintConfig(wordsPerHint = 3))
    scheduler.arm(0, atAyahBoundary = false)

    assertThat(scheduler.onTick(4_000)).containsExactly(Effect.GiveHint(3))
  }

  @Test
  fun `hinting gives up after too many hints in a row`() {
    val scheduler = HintScheduler(HintConfig(maxConsecutiveHints = 2))
    scheduler.arm(0, atAyahBoundary = false)

    var now = 0L
    repeat(2) {
      now += 4_000
      assertThat(scheduler.onTick(now)).containsExactly(Effect.GiveHint(1))
      scheduler.onHintFinished(now)
    }
    now += 4_000
    assertThat(scheduler.onTick(now)).containsExactly(Effect.GiveUp)
    assertThat(scheduler.isArmed).isFalse()
  }

  @Test
  fun `disarming clears a pending mistake and stops hints`() {
    val scheduler = armed()
    scheduler.onMistake(1_000, word)

    assertThat(scheduler.disarm()).containsExactly(Effect.ClearMistake(word))
    assertThat(scheduler.onTick(100_000)).isEmpty()
  }

  @Test
  fun `nothing happens before arming`() {
    val scheduler = HintScheduler(HintConfig())

    assertThat(scheduler.onMistake(0, word)).isEmpty()
    assertThat(scheduler.onTick(100_000)).isEmpty()
  }

  @Test
  fun `a word gotten wrong twice is given at the next pause without waiting for the delay`() {
    val scheduler = HintScheduler(HintConfig(delayMs = 10_000))
    scheduler.arm(0, atAyahBoundary = false)
    scheduler.onSpeechActivity(500, active = true)
    scheduler.onMistake(1_000, word)
    // first attempt over; the user tries again and gets it wrong again
    scheduler.onSpeechActivity(1_500, active = false)
    scheduler.onSpeechActivity(2_500, active = true)
    assertThat(scheduler.onTick(3_000)).isEmpty()
    scheduler.onSpeechActivity(4_000, active = false)

    // given once the recognizer has had time to catch up, long before the 10 s delay
    assertThat(scheduler.nextDeadlineMs()).isEqualTo(6_000)
    assertThat(scheduler.onTick(6_000)).containsExactly(Effect.ClearMistake(word), Effect.GiveHint(1))
  }

  @Test
  fun `a mistake recognized after the user stopped counts that attempt`() {
    val scheduler = HintScheduler(HintConfig(delayMs = 10_000))
    scheduler.arm(0, atAyahBoundary = false)
    scheduler.onSpeechActivity(500, active = true)
    scheduler.onSpeechActivity(1_000, active = false)
    scheduler.onMistake(1_400, word)
    assertThat(scheduler.nextDeadlineMs()).isEqualTo(11_400)

    scheduler.onSpeechActivity(2_000, active = true)
    scheduler.onSpeechActivity(3_000, active = false)
    assertThat(scheduler.nextDeadlineMs()).isEqualTo(5_000)
  }

  @Test
  fun `a second attempt that turns out right gives no hint`() {
    val scheduler = HintScheduler(HintConfig(delayMs = 10_000))
    scheduler.arm(0, atAyahBoundary = false)
    scheduler.onSpeechActivity(500, active = true)
    scheduler.onMistake(1_000, word)
    scheduler.onSpeechActivity(1_500, active = false)
    scheduler.onSpeechActivity(2_500, active = true)
    scheduler.onSpeechActivity(4_000, active = false)

    // the word arrives from the recognizer before the pause is long enough
    assertThat(scheduler.onWordRecited(5_000, atAyahBoundary = false)).containsExactly(Effect.ClearMistake(word))
    assertThat(scheduler.onTick(6_000)).isEmpty()
  }

  @Test
  fun `after three single word hints in a row the rest of the ayah or line is given`() {
    val scheduler = armed()
    var now = 0L
    repeat(3) {
      now += 4_000
      assertThat(scheduler.onTick(now)).containsExactly(Effect.GiveHint(1, HintScheduler.Extent.WORDS))
      scheduler.onHintFinished(now)
    }
    now += 4_000
    assertThat(scheduler.onTick(now)).containsExactly(Effect.GiveHint(1, HintScheduler.Extent.AYAH_OR_LINE))
    scheduler.onHintFinished(now)

    // reciting on their own starts over with single words
    scheduler.onWordRecited(now + 1_000, atAyahBoundary = false)
    assertThat(scheduler.onTick(now + 5_000)).containsExactly(Effect.GiveHint(1, HintScheduler.Extent.WORDS))
  }

  private fun armed(): HintScheduler {
    val scheduler = HintScheduler(HintConfig(delayMs = 4_000))
    scheduler.arm(0, atAyahBoundary = false)
    return scheduler
  }
}
