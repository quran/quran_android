package com.quran.mobile.feature.memorize.session

import com.quran.mobile.feature.memorize.tracker.RecitationTracker
import com.quran.mobile.feature.memorize.tracker.TimedPhoneme
import com.quran.mobile.feature.memorize.tracker.TrackerEvent
import com.quran.mobile.feature.memorize.tracker.salah.SalahFlow

/** Follows the user through the passage; the session doesn't care whether it's salah or not. */
internal interface Follower {
  val cursor: Int
  val isDone: Boolean

  /** Whether it is known where the user started (see [RecitationTracker.anchored]). */
  val started: Boolean

  /** Whether hints may be given now (in salah: only while the passage itself is being recited). */
  val canHint: Boolean

  /** Whether the start waits for the words that tell two places apart (see [RecitationTracker.startPending]). */
  val startPending: Boolean

  fun feed(phonemes: List<TimedPhoneme>): List<TrackerEvent>
  fun onPause(): List<TrackerEvent>
  fun markHinted(count: Int)

  /** More of the passage (the next suras): the session goes on past the end of a sura. */
  fun extend(words: List<String>, ayahs: List<Int>)

  /** Phrases that become possible later, e.g. the basmala before the next sura. */
  fun addPhrases(phrases: List<Pair<String, String>>)

  /** Diagnostics: what the tracker decides and why. */
  fun trace(trace: (String) -> Unit)

  /** Hints gave up: the user went on past what the tracker holds at; it may follow them. */
  fun releaseHold() {}
}

internal class MemorizeFollower(private val tracker: RecitationTracker) : Follower {
  override val cursor: Int get() = tracker.cursor
  override val started: Boolean get() = tracker.anchored
  override val isDone: Boolean get() = tracker.isDone
  override val canHint: Boolean get() = true
  override val startPending: Boolean get() = tracker.startPending
  override fun feed(phonemes: List<TimedPhoneme>) = tracker.feed(phonemes)
  override fun onPause() = tracker.onPause()
  override fun markHinted(count: Int) = tracker.markHinted(count)
  override fun extend(words: List<String>, ayahs: List<Int>) = tracker.extend(words, ayahs)
  override fun releaseHold() = tracker.releaseHold()
  override fun addPhrases(phrases: List<Pair<String, String>>) = tracker.addPhrases(phrases)
  override fun trace(trace: (String) -> Unit) {
    tracker.trace = trace
  }
}

internal class SalahFollower(private val flow: SalahFlow) : Follower {
  override val cursor: Int get() = flow.passage.cursor
  override val started: Boolean get() = flow.passage.anchored
  override val isDone: Boolean get() = flow.passage.isDone
  override val canHint: Boolean get() = flow.isReadingPassage
  override val startPending: Boolean get() = flow.passage.startPending
  override fun feed(phonemes: List<TimedPhoneme>) = flow.feed(phonemes)
  override fun onPause() = flow.onPause()
  override fun markHinted(count: Int) = flow.passage.markHinted(count)
  override fun extend(words: List<String>, ayahs: List<Int>) = flow.extendPassage(words, ayahs)
  override fun addPhrases(phrases: List<Pair<String, String>>) = flow.passage.addPhrases(phrases)
  override fun trace(trace: (String) -> Unit) {
    flow.trace = trace
  }
}
