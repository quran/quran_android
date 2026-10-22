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
}

internal class MemorizeFollower(private val tracker: RecitationTracker) : Follower {
  override val cursor: Int get() = tracker.cursor
  override val isDone: Boolean get() = tracker.isDone
  override val canHint: Boolean get() = true
  override fun feed(phonemes: List<TimedPhoneme>) = tracker.feed(phonemes)
  override fun onPause() = tracker.onPause()
  override fun markHinted(count: Int) = tracker.markHinted(count)
}

internal class SalahFollower(private val flow: SalahFlow) : Follower {
  override val cursor: Int get() = flow.passage.cursor
  override val isDone: Boolean get() = flow.passage.isDone
  override val canHint: Boolean get() = flow.isReadingPassage
  override fun feed(phonemes: List<TimedPhoneme>) = flow.feed(phonemes)
  override fun onPause() = flow.onPause()
  override fun markHinted(count: Int) = flow.passage.markHinted(count)
}
