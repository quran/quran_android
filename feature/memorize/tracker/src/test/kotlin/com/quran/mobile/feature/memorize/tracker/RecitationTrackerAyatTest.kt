package com.quran.mobile.feature.memorize.tracker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Ayat left out, restarts and ayat that open alike (as 2:149 and 2:150, which share their first 8
 * words), with the words' ayat known. Latin stand-ins for phonemes keep the scenarios readable.
 */
class RecitationTrackerAyatTest {

  // ayah 1; ayat 2 and 3 open alike (7 words); ayah 4
  private val ayat = listOf(
    listOf("faqadkaf", "arnabiha", "tamaman"),
    listOf("wamin", "haythu", "kharajta", "fawalli", "wajhaka", "shatra", "masjidi", "wainnahu", "lalhaqqu", "minrabbik"),
    listOf("wamin", "haythu", "kharajta", "fawalli", "wajhaka", "shatra", "masjidi", "wahaythu", "makuntum", "fawallu"),
    listOf("liallayakuna", "linnasi", "alaykum", "hujjatun"),
  )
  private val words = ayat.flatten()
  private val ayahs = ayat.flatMapIndexed { a, ws -> List(ws.size) { a + 1 } }
  private fun index(ayah: Int, word: Int) = ayat.take(ayah - 1).sumOf { it.size } + word - 1

  private val memorize = TrackerConfig(skipAyat = false, maxMissedWords = 2)
  private val strict = TrackerConfig(skipAyat = false, maxMissedWords = 0)

  private var time = 0L

  private fun RecitationTracker.say(vararg text: String): List<TrackerEvent> =
    text.flatMap { word -> feed(word.map { TimedPhoneme(it, (time++) * 80) }) }

  private fun RecitationTracker.sayAyah(ayah: Int, from: Int = 1, to: Int = ayat[ayah - 1].size, skip: Set<Int> = emptySet()) =
    say(*(from..to).filter { it !in skip }.map { ayat[ayah - 1][it - 1] }.toTypedArray())

  private fun List<TrackerEvent>.missed() = filterIsInstance<TrackerEvent.WordMissed>().map { it.index }
  private fun List<TrackerEvent>.mistakes() = filterIsInstance<TrackerEvent.Mistake>().map { it.index }

  @Test
  fun `an ayah left out is held at, not stepped over`() {
    val tracker = RecitationTracker(words, memorize, ayahs = ayahs)

    // ayah 1, then ayah 3 (ayah 2 left out; it opens like ayah 3), then ayah 4
    val events = tracker.sayAyah(1) + tracker.sayAyah(3) + tracker.sayAyah(4)

    // the opening the two share counts for ayah 2; where they differ, it holds
    assertThat(tracker.cursor).isEqualTo(index(2, 8))
    assertThat(events.missed()).isEmpty()
    assertThat(events.mistakes()).containsExactly(index(2, 8))
  }

  @Test
  fun `once the user goes back to the ayah left out, it goes on`() {
    val tracker = RecitationTracker(words, memorize, ayahs = ayahs)
    tracker.sayAyah(1)
    tracker.sayAyah(3)

    val events = tracker.sayAyah(2, from = 8) + tracker.sayAyah(3) + tracker.sayAyah(4)

    assertThat(events.missed()).isEmpty()
    assertThat(tracker.isDone).isTrue()
  }

  @Test
  fun `where ayat may be left out, as in salah, the session follows the reciter past one`() {
    val tracker = RecitationTracker(words, ayahs = ayahs)

    val events = tracker.sayAyah(1) + tracker.sayAyah(3) + tracker.sayAyah(4)

    assertThat(events.missed()).isNotEmpty()
    assertThat(tracker.isDone).isTrue()
  }

  @Test
  fun `starting the ayah again from far back is a repetition, not a jump to the ayah alike`() {
    listOf(TrackerConfig(), memorize).forEach { config ->
      val tracker = RecitationTracker(words, config, startIndex = index(2, 1), ayahs = ayahs)

      val events = tracker.sayAyah(2, to = 9) + tracker.sayAyah(2, to = 7)

      assertThat(tracker.cursor).isEqualTo(index(2, 10))
      assertThat(events.missed()).isEmpty()
      assertThat(events.mistakes()).isEmpty()
      assertThat(events.filterIsInstance<TrackerEvent.Repetition>()).isNotEmpty()
    }
  }

  @Test
  fun `a word the recognizer didn't hear at the end of an ayah passes, unless every word must be heard`() {
    val loose = RecitationTracker(words, memorize, startIndex = index(3, 1), ayahs = ayahs)
    val looseEvents = loose.sayAyah(3, skip = setOf(10)) + loose.sayAyah(4)
    assertThat(looseEvents.missed()).containsExactly(index(3, 10))
    assertThat(loose.isDone).isTrue()

    val held = RecitationTracker(words, strict, startIndex = index(3, 1), ayahs = ayahs)
    val heldEvents = held.sayAyah(3, skip = setOf(10)) + held.sayAyah(4)
    assertThat(heldEvents.missed()).isEmpty()
    assertThat(held.cursor).isEqualTo(index(3, 10))
  }

  @Test
  fun `an ayah of one word is never left out`() {
    val short = listOf(listOf("qadnara", "taqalluba", "wajhika"), listOf("mudhammatan"), listOf("fabiayyi", "alai", "rabbikuma"))
    val tracker = RecitationTracker(short.flatten(), memorize, ayahs = listOf(1, 1, 1, 2, 3, 3, 3))

    val events = tracker.say("qadnara", "taqalluba", "wajhika", "fabiayyi", "alai", "rabbikuma")

    assertThat(events.missed()).isEmpty()
    assertThat(tracker.cursor).isEqualTo(3)
  }

  @Test
  fun `a start alike in two places waits for the words that tell them apart`() {
    // the user may start anywhere in ayat 1 to 4 and is expected at ayah 1; they start at ayah 3
    val tracker = RecitationTracker(words, memorize, startIndex = 0, startWindow = words.size, ayahs = ayahs)

    val events = tracker.sayAyah(3) + tracker.sayAyah(4)

    assertThat(events.mistakes()).isEmpty()
    assertThat(events.missed()).isEmpty()
    assertThat(events.filterIsInstance<TrackerEvent.WordRecited>().map { it.index }.first()).isEqualTo(index(3, 1))
    assertThat(tracker.isDone).isTrue()
  }

  @Test
  fun `a start dropped after a misheard word stays where the user is expected, not back at the page`() {
    // expected at ayah 3 (the page on screen); the user starts at ayah 1, then a word is misheard
    val tracker = RecitationTracker(words, memorize, startIndex = index(3, 1), startWindow = words.size, ayahs = ayahs)
    tracker.sayAyah(1)
    assertThat(tracker.anchored).isTrue()
    tracker.say("zzzzzzzzzzzz")
    assertThat(tracker.anchored).isFalse()

    // a hint before a new start gives the word where they were, not the first word on screen
    tracker.markHinted(1)

    assertThat(tracker.cursor).isEqualTo(index(2, 2))
  }

  @Test
  fun `a start alike in two places is settled at the nearer one when the user stops`() {
    val tracker = RecitationTracker(words, memorize, startIndex = 0, startWindow = words.size, ayahs = ayahs)

    assertThat(tracker.sayAyah(2, to = 5)).isEmpty()
    assertThat(tracker.startPending).isTrue()

    val events = tracker.onPause()

    assertThat(tracker.startPending).isFalse()
    assertThat(tracker.anchored).isTrue()
    assertThat(events.filterIsInstance<TrackerEvent.WordRecited>().map { it.index })
      .containsExactlyElementsIn(index(2, 1)..index(2, 5)).inOrder()
    assertThat(tracker.cursor).isEqualTo(index(2, 6))
  }
}
