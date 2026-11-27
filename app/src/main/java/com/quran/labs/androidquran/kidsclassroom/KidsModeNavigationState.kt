package com.quran.labs.androidquran.kidsclassroom

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Top-level destinations inside the dedicated **Full Kids Mode** experience.
 */
enum class KidsModeTab {
  ISLANDS,
  REWARDS,
  PRACTICE_HUB
}

/**
 * Optional quick-launch mode when sailing from an Island into a Kids Mushaf lesson.
 */
enum class KidsLessonLaunchMode {
  OPEN_READY,
  LISTEN_AND_REPEAT,
  RECITE_AND_REVEAL
}

/**
 * One-shot return payload emitted when the child navigates Back from a Kids Mode Mushaf lesson
 * to the Progress Islands, driving the interactive island bounce, explorer boat dock/sail, and
 * welcome-back ribbon.
 */
data class KidsIslandReturnEvent(
  val eventId: Long,
  val surahNumber: Int,
  val starsGained: Int,
  val ayatMasteredGained: Int,
  val surahJustCompleted: Boolean
)

/**
 * Lightweight coordinator connecting `PagerActivity` (in dedicated Kids Session Mode) back to
 * `KidsModeActivity`'s Progress Islands so returning feels playful, grounded, and immediate.
 */
object KidsModeNavigationState {
  private val nextId = AtomicLong(1L)
  private val _pendingReturnEvent = MutableStateFlow<KidsIslandReturnEvent?>(null)
  val pendingReturnEvent: StateFlow<KidsIslandReturnEvent?> = _pendingReturnEvent.asStateFlow()

  fun emitReturnToIslands(
    surahNumber: Int,
    starsGained: Int = 0,
    ayatMasteredGained: Int = 0,
    surahJustCompleted: Boolean = false
  ): KidsIslandReturnEvent {
    val event = KidsIslandReturnEvent(
      eventId = nextId.getAndIncrement(),
      surahNumber = surahNumber.coerceIn(1, 114),
      starsGained = starsGained.coerceAtLeast(0),
      ayatMasteredGained = ayatMasteredGained.coerceAtLeast(0),
      surahJustCompleted = surahJustCompleted
    )
    _pendingReturnEvent.value = event
    return event
  }

  fun consumeReturnEvent(eventId: Long) {
    if (_pendingReturnEvent.value?.eventId == eventId) {
      _pendingReturnEvent.value = null
    }
  }

  fun clearForTests() {
    _pendingReturnEvent.value = null
  }

  /**
   * Resolves which Progress Islands archipelago (and optional Juz drill-down) contains [surahNumber].
   */
  fun resolveArchipelagoForSurah(surahNumber: Int): Pair<SurahJourneyScope, Int?> {
    val safeSurah = surahNumber.coerceIn(1, 114)
    return when {
      safeSurah == 1 || safeSurah in 78..114 -> SurahJourneyScope.JUZ_AMMA to null
      safeSurah in 67..77 -> SurahJourneyScope.JUZ_TABARAK to null
      else -> {
        val matchingJuz = SurahJourneyPlanner.QURAN_JUZ_SPECS
          .firstOrNull { safeSurah in it.surahNumbers }
          ?.juzNumber
          ?: 1
        SurahJourneyScope.FULL_QURAN to matchingJuz
      }
    }
  }
}
