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
}
