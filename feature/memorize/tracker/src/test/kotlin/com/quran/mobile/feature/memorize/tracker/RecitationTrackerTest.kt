package com.quran.mobile.feature.memorize.tracker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecitationTrackerTest {

  // Latin stand-ins for phonemes keep the scenarios readable.
  private val ikhlas = listOf("qulhuwa", "llahu", "ahadun", "llahu", "ssamadu")
  private val falaq = listOf("qulauzu", "birabbi", "lfalaqi")
  private val basmala = "basmala" to "bismillahirrahmanirrahim"

  private var time = 0L

  private fun RecitationTracker.say(text: String): List<TrackerEvent> =
    feed(text.map { TimedPhoneme(it, (time++) * 80) })

  private fun recited(events: List<TrackerEvent>) =
    events.filterIsInstance<TrackerEvent.WordRecited>().map { it.index }

  @Test
  fun `the passage can grow while the user recites`() {
    val tracker = RecitationTracker(ikhlas)
    tracker.say("qulhuwallahuahadunallahussamadu")
    assertThat(tracker.isDone).isTrue()

    tracker.extend(falaq)

    assertThat(tracker.isDone).isFalse()
    assertThat(recited(tracker.say("qulauzubirabbilfalaqi"))).containsExactly(5, 6, 7).inOrder()
    assertThat(tracker.isDone).isTrue()
  }

  @Test
  fun `the basmala between suras is not a mistake once it is a known phrase`() {
    val tracker = RecitationTracker(ikhlas, phrases = listOf(basmala))
    tracker.say("qulhuwallahuahadunallahussamadu")
    tracker.extend(falaq)

    val events = tracker.say("bismillahirrahmanirrahimqulauzubirabbilfalaqi")

    assertThat(events.filterIsInstance<TrackerEvent.Mistake>()).isEmpty()
    assertThat(recited(events)).containsExactly(5, 6, 7).inOrder()
  }

  @Test
  fun `phrases added later are recognized`() {
    val tracker = RecitationTracker(ikhlas)
    tracker.say("qulhuwallahuahadunallahussamadu")
    tracker.extend(falaq)
    tracker.addPhrases(listOf(basmala, basmala))

    val events = tracker.say("bismillahirrahmanirrahimqulauzubirabbilfalaqi")

    assertThat(events.filterIsInstance<TrackerEvent.Mistake>()).isEmpty()
    assertThat(recited(events)).containsExactly(5, 6, 7).inOrder()
  }

  // two "pages" on screen: Al-Ikhlas then Al-Falaq
  private val screen = ikhlas + falaq

  @Test
  fun `the user can start anywhere on the pages on screen`() {
    val tracker = RecitationTracker(screen + listOf("qulauzu", "birabbi", "nnasi"), startWindow = screen.size)
    assertThat(tracker.anchored).isFalse()

    val events = tracker.say("qulauzubirabbilfalaqi")

    assertThat(tracker.anchored).isTrue()
    assertThat(recited(events)).containsExactly(5, 6, 7).inOrder()
    // the words before the start weren't skipped: the user started there
    assertThat(events.filterIsInstance<TrackerEvent.WordMissed>()).isEmpty()
    assertThat(events.filterIsInstance<TrackerEvent.Mistake>()).isEmpty()
  }

  @Test
  fun `the basmala before the start is fine`() {
    val tracker = RecitationTracker(screen, phrases = listOf(basmala), startWindow = screen.size)

    val events = tracker.say("bismillahirrahmanirrahimllahussamaduqulauzu")

    assertThat(events.filterIsInstance<TrackerEvent.Mistake>()).isEmpty()
    assertThat(recited(events)).containsExactly(3, 4, 5).inOrder()
  }

  @Test
  fun `reciting something that isn't on screen marks the first word`() {
    val tracker = RecitationTracker(screen, startWindow = screen.size)

    val events = tracker.say("waddhuhawallayliidhasajamawaddaakarabbukawamaqala")

    assertThat(events).containsExactly(TrackerEvent.Mistake(0, events.single().timeMs, TrackerEvent.Mistake.Kind.ELSEWHERE))
    assertThat(tracker.anchored).isFalse()

    // once the user recites something on screen, that is where they start
    val start = tracker.say("qulhuwallahuahadun")
    assertThat(tracker.anchored).isTrue()
    assertThat(recited(start)).containsExactly(0, 1, 2).inOrder()
  }

  @Test
  fun `a hint before the start starts at the first word`() {
    val tracker = RecitationTracker(screen, startWindow = screen.size)

    tracker.markHinted(1)

    assertThat(tracker.anchored).isTrue()
    assertThat(tracker.cursor).isEqualTo(1)
    assertThat(recited(tracker.say("llahuahadun"))).containsExactly(1, 2).inOrder()
  }

  @Test
  fun `two short words that happen to match somewhere aren't a start`() {
    val tracker = RecitationTracker(listOf("qulhuwa", "allahu", "ma", "lakum", "min", "ilahin", "ghayruhu"), startWindow = 7)

    tracker.say("malakum")

    assertThat(tracker.anchored).isFalse()
  }

  @Test
  fun `a phrase that is repeated is placed where the user is expected`() {
    // the same three words twice in the window, the user expected at the second
    val words = listOf("yaqawmi", "budullaha", "malakum", "wailaa", "thamuda", "yaqawmi", "budullaha", "malakum")
    val tracker = RecitationTracker(words, startIndex = 3, startWindow = words.size)

    val events = tracker.say("yaqawmibudullahamalakum")

    assertThat(recited(events)).containsExactly(5, 6, 7).inOrder()
  }

  @Test
  fun `a start contradicted right away is looked for again`() {
    val words = listOf("qulhuwa", "llahu", "ahadun", "llahu", "ssamadu", "lamyalid", "walamyulad", "walamyakun")
    val tracker = RecitationTracker(words, startWindow = words.size)
    tracker.say("qulhuwallahuahadun")
    assertThat(tracker.anchored).isTrue()

    // not what follows: the start was a guess
    val events = tracker.say("wassamaiwattariqi")

    assertThat(tracker.anchored).isFalse()
    assertThat(events.filterIsInstance<TrackerEvent.Mistake>().map { it.kind }).doesNotContain(TrackerEvent.Mistake.Kind.SUBSTITUTION)
  }
}
