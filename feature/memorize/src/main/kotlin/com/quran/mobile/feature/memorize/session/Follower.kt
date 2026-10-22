package com.quran.mobile.feature.memorize.session

import com.quran.mobile.feature.memorize.tracker.RecitationTracker
import com.quran.mobile.feature.memorize.tracker.TimedPhoneme
import com.quran.mobile.feature.memorize.tracker.TrackerEvent
import com.quran.mobile.feature.memorize.tracker.salah.SalahFlow

/** Follows the user through the passage; the session doesn't care whether it's salah or not. */
internal interface Follower {
  val cursor: Int
  val isDone: Boolean

  /** Whether hints may be given now (in salah: only while the passage itself is being recited). */
  val canHint: Boolean

  fun feed(phonemes: List<TimedPhoneme>): List<TrackerEvent>
  fun onPause(): List<TrackerEvent>
  fun markHinted(count: Int)

  /** More of the passage (the next suras): the session goes on past the end of a sura. */
  fun extend(words: List<String>)

  /** Phrases that become possible later, e.g. the basmala before the next sura. */
  fun addPhrases(phrases: List<Pair<String, String>>)
}

internal class MemorizeFollower(private val tracker: RecitationTracker) : Follower {
  override val cursor: Int get() = tracker.cursor
  override val isDone: Boolean get() = tracker.isDone
  override val canHint: Boolean get() = true
  override fun feed(phonemes: List<TimedPhoneme>) = tracker.feed(phonemes)
  override fun onPause() = tracker.onPause()
  override fun markHinted(count: Int) = tracker.markHinted(count)
  override fun extend(words: List<String>) = tracker.extend(words)
  override fun addPhrases(phrases: List<Pair<String, String>>) = tracker.addPhrases(phrases)
}

internal class SalahFollower(private val flow: SalahFlow) : Follower {
  override val cursor: Int get() = flow.passage.cursor
  override val isDone: Boolean get() = flow.passage.isDone
  override val canHint: Boolean get() = flow.isReadingPassage
  override fun feed(phonemes: List<TimedPhoneme>) = flow.feed(phonemes)
  override fun onPause() = flow.onPause()
  override fun markHinted(count: Int) = flow.passage.markHinted(count)
  override fun extend(words: List<String>) = flow.extendPassage(words)
  override fun addPhrases(phrases: List<Pair<String, String>>) = flow.passage.addPhrases(phrases)
}
