package com.quran.mobile.feature.memorize.tracker

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Starts the phone heard (tools: the lab's tracker/export_live_start.py), replayed through the start
 * search, which the reference implementation doesn't have: what the speech model heard on the Mi 11,
 * with its pauses, in a session that may start anywhere in a window of words.
 */
class RecitationTrackerLiveStartTest {

  // 95:1-114:6, expected at 100:10 (word 297): 102:1 is word 342, 102:2:2 (زرتم) 345, 102:3 347-349,
  // 102:4 350-353, 102:5 354-358. In each, the start is found on 102:1-2, then زرتم is misheard; كلا and
  // سوف are often misheard too, so three clear words in a row don't come again.

  @Test
  fun `a start dropped after a misheard word is taken up again when the user goes on from it`() {
    // Husary recites 102:1-3 then 102:5, leaving 102:4 out
    val (tracker, events) = replay("live_mi11_102_dropped_start")
    val recited = events.filterIsInstance<TrackerEvent.WordRecited>().map { it.index }
    val mistakes = events.filterIsInstance<TrackerEvent.Mistake>()

    // 102:1 is where the user started; the misheard زرتم passes, and 102:3 is followed
    assertThat(recited).containsAtLeast(342, 343, 344, 345, 346, 347, 348, 349)
    // nothing marked as recited elsewhere: the user was where the start had been found
    assertThat(mistakes.filter { it.kind == TrackerEvent.Mistake.Kind.ELSEWHERE }).isEmpty()
    // 102:4 left out: the session holds at its first word (the hint gives it), 102:5 isn't taken as recited
    assertThat(recited.filter { it >= 350 }).isEmpty()
    assertThat(tracker.cursor).isEqualTo(350)
  }

  @Test
  fun `a start dropped after a misheard word is followed to the end of a correct recitation`() {
    // Husary recites 102:1-5; the session stopped him with hints before he could go on
    val (tracker, events) = replay("live_mi11_102_dropped_start_correct")
    val recited = events.filterIsInstance<TrackerEvent.WordRecited>().map { it.index }

    assertThat(recited).containsAtLeast(342, 345, 349, 350, 353, 354, 358)
    assertThat(tracker.cursor).isEqualTo(359)
  }

  private fun replay(name: String): Pair<RecitationTracker, List<TrackerEvent>> {
    val words = mutableListOf<String>()
    val phrases = mutableListOf<Pair<String, String>>()
    var ayahs = emptyList<Int>()
    var config = TrackerConfig()
    var start = 0
    var window = 0
    val actions = mutableListOf<List<TimedPhoneme>?>()
    fixture(name).readLines().filter { it.isNotEmpty() }.forEach { line ->
      val parts = line.split('\t')
      when (parts[0]) {
        "W" -> words += parts[1]
        "P" -> phrases += parts[1] to parts[2]
        "A" -> ayahs = parts[1].split(',').map { it.toInt() }
        "C" -> parts[1].split(';').forEach {
          val (key, value) = it.split('=')
          config = when (key) {
            "skip_ayat" -> config.copy(skipAyat = value == "1")
            "max_missed_words" -> config.copy(maxMissedWords = value.toInt())
            else -> error("unknown setting $key in $name")
          }
        }
        "I" -> parts[1].split(';').forEach {
          val (key, value) = it.split('=')
          when (key) {
            "start" -> start = value.toInt()
            "window" -> window = value.toInt()
            else -> error("unknown start setting $key in $name")
          }
        }
        "F" -> actions += parts[1].split(';').map {
          val (symbol, ms) = it.split(',')
          TimedPhoneme(symbol.single(), ms.toLong())
        }
        "S" -> actions += null
      }
    }
    val tracker = RecitationTracker(words, config, startIndex = start, phrases = phrases, startWindow = window, ayahs = ayahs)
    val events = actions.flatMap { phonemes -> if (phonemes == null) tracker.onPause() else tracker.feed(phonemes) }
    return tracker to events
  }

  private fun fixture(name: String): File =
    File(javaClass.classLoader!!.getResource("live_start/$name.tsv")!!.toURI())
}
