package com.quran.mobile.feature.memorize.tracker

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Replays fixtures exported from the reference implementation (tools: tracker/export_golden.py)
 * and checks that this port produces exactly the same events. Fixtures cover synthetic edge cases
 * and phoneme streams the speech model produced for real recitations.
 */
class RecitationTrackerGoldenTest {

  @Test
  fun `matches the reference implementation on every fixture`() {
    val fixtures = goldenDirectory().listFiles { file -> file.extension == "tsv" }!!.sortedBy { it.name }
    assertWithMessage("no fixtures found").that(fixtures).isNotEmpty()
    fixtures.forEach { replay(it) }
  }

  private fun replay(file: File) {
    val words = mutableListOf<String>()
    val phrases = mutableListOf<Pair<String, String>>()
    val actions = mutableListOf<Action>()
    val expected = mutableMapOf<Int, MutableList<String>>()
    file.readLines().filter { it.isNotEmpty() }.forEach { line ->
      val parts = line.split('\t')
      when (parts[0]) {
        "W" -> words += parts[1]
        "P" -> phrases += parts[1] to parts[2]
        "F" -> actions += Action.Feed(parts[1].split(';').map {
          val (symbol, ms) = it.split(',')
          TimedPhoneme(symbol.single(), ms.toLong())
        })
        "H" -> actions += Action.Hint(parts[1].toInt())
        "S" -> actions += Action.Pause
        "E" -> expected.getOrPut(parts[1].toInt()) { mutableListOf() } += parts.drop(2).joinToString("\t")
      }
    }

    val tracker = RecitationTracker(words, phrases = phrases)
    actions.forEachIndexed { index, action ->
      when (action) {
        is Action.Hint -> tracker.markHinted(action.count)
        Action.Pause -> {
          val actual = tracker.onPause().map { it.describe() }
          assertWithMessage("${file.name}, pause #$index")
            .that(actual)
            .containsExactlyElementsIn(expected[index].orEmpty())
            .inOrder()
        }
        is Action.Feed -> {
          val actual = tracker.feed(action.phonemes).map { it.describe() }
          assertWithMessage("${file.name}, feed #$index")
            .that(actual)
            .containsExactlyElementsIn(expected[index].orEmpty())
            .inOrder()
        }
      }
    }
  }

  private fun TrackerEvent.describe(): String = when (this) {
    is TrackerEvent.WordRecited ->
      "word\t$index\t$timeMs\t${if (fromContext) "ctx" else if (recovered) "rec" else ""}"
    is TrackerEvent.WordMissed -> "missed\t$index\t$timeMs\t"
    is TrackerEvent.Mistake -> "mistake\t$index\t$timeMs\t${kind.name}"
    is TrackerEvent.Repetition -> "repetition\t$index\t$timeMs\t$lastIndex"
    is TrackerEvent.NonTarget -> "nontarget\t$index\t$timeMs\t$kind"
  }

  private sealed interface Action {
    data class Feed(val phonemes: List<TimedPhoneme>) : Action
    data class Hint(val count: Int) : Action
    data object Pause : Action
  }

  private fun goldenDirectory(): File =
    File(javaClass.classLoader!!.getResource("golden")!!.toURI())
}
