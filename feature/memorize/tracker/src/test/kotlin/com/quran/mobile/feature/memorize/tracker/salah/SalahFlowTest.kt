package com.quran.mobile.feature.memorize.tracker.salah

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.memorize.tracker.TimedPhoneme
import com.quran.mobile.feature.memorize.tracker.TrackerEvent
import org.junit.Test

class SalahFlowTest {

  // Latin stand-ins for phonemes keep the scenario readable.
  private val fatiha = listOf("alhamdu", "lillahi", "rabbi", "lalamin", "arrahmani", "rrahimi")
  private val passage = listOf("qulhuwa", "llahu", "ahadun", "llahu", "ssamadu", "lamyalid", "walamyulad")
  private val phrases = listOf(
    "takbir" to "allahuakbar", "ameen" to "amin", "ruku" to "subhanarabbiyalazim", "tasmi" to "samiallahulimanhamidah",
  )

  private var time = 0L

  private fun SalahFlow.say(text: String): List<TrackerEvent> =
    feed(text.map { TimedPhoneme(it, (time++) * 80) })

  private fun recitedIndexes(events: List<TrackerEvent>) =
    events.filterIsInstance<TrackerEvent.WordRecited>().map { it.index }

  @Test
  fun `prayer speech around the passage is never a mistake and the passage continues next rak'ah`() {
    val flow = SalahFlow(passage, fatiha, phrases)
    val all = mutableListOf<TrackerEvent>()

    // rak'ah 1
    all += flow.say("allahuakbar")
    all += flow.say("alhamdulillahirabbilalamin")
    all += flow.say("arrahmanirrahimi")
    all += flow.say("amin")
    assertThat(flow.isReadingPassage).isFalse()

    val first = flow.say("qulhuwallahuahadun")
    assertThat(recitedIndexes(first)).containsExactly(0, 1, 2).inOrder()
    assertThat(flow.isReadingPassage).isTrue()

    all += flow.say("allahuakbar")
    all += flow.onPause() // ruku: silence after the takbir
    assertThat(flow.phase).isEqualTo(SalahFlow.Phase.BETWEEN_RAKAHS)
    all += flow.say("subhanarabbiyalazim")

    // rak'ah 2: Al-Fatiha again, then the passage from where it stopped
    all += flow.say("alhamdulillahirabbilalamin")
    all += flow.say("arrahmanirrahimi")
    val second = flow.say("llahussamadulamyalidwalamyulad")

    assertThat(all.filterIsInstance<TrackerEvent.Mistake>()).isEmpty()
    assertThat(all.filterIsInstance<TrackerEvent.WordRecited>()).isEmpty()
    assertThat(recitedIndexes(second)).containsExactly(3, 4, 5, 6).inOrder()
    assertThat(second.filterIsInstance<TrackerEvent.Mistake>()).isEmpty()
  }

  @Test
  fun `the passage is over once ruku is heard, even if the takbir wasn't recognized`() {
    val flow = SalahFlow(passage, fatiha, phrases)
    flow.say("qulhuwallahuahadun")
    assertThat(flow.isReadingPassage).isTrue()

    // a takbir lost in the echo, then rising from ruku
    flow.say("xx")
    flow.say("samiallahulimanhamidah")
    flow.onPause()

    assertThat(flow.phase).isEqualTo(SalahFlow.Phase.BETWEEN_RAKAHS)
    assertThat(flow.isReadingPassage).isFalse()
  }

  @Test
  fun `a wrong word inside the passage is still a mistake`() {
    val flow = SalahFlow(passage, fatiha, phrases)
    flow.say("qulhuwallahu")

    val events = flow.say("xyzxyzxyzxyz")

    assertThat(events.filterIsInstance<TrackerEvent.Mistake>().map { it.index }).containsExactly(2)
  }
}
