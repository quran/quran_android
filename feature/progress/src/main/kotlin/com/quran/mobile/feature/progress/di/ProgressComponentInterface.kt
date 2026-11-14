package com.quran.mobile.feature.progress.di

import com.quran.data.dao.ReadingBookmarkSlotSettings
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppScope
import com.quran.mobile.di.RecordedSessionLauncher
import com.quran.mobile.feature.progress.data.ProgressRepository
import com.quran.mobile.feature.progress.reminder.ReminderNotifier
import com.quran.mobile.feature.progress.reminder.ReminderScheduler
import com.quran.mobile.progress.ProgressPreferences
import com.quran.mobile.progress.history.ProgressHistory
import com.quran.mobile.progress.log.BookmarkMoveRecorder
import com.quran.mobile.progress.reading.ReaderPresence
import com.quran.mobile.progress.time.ProgressClock
import com.quran.page.common.data.QuranNaming
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.Provides

/** Lets Android components that aren't injected (the screen, the reminder's worker) reach the feature. */
@ContributesTo(AppScope::class)
interface ProgressComponentInterface {
  fun progressRepository(): ProgressRepository
  fun progressPreferences(): ProgressPreferences
  fun progressHistory(): ProgressHistory
  fun progressClock(): ProgressClock
  fun readingBookmarkSlotSettings(): ReadingBookmarkSlotSettings
  fun readingBookmarksDao(): ReadingBookmarksDao
  fun bookmarkMoveRecorder(): BookmarkMoveRecorder
  fun readerPresence(): ReaderPresence
  fun reminderScheduler(): ReminderScheduler
  fun reminderNotifier(): ReminderNotifier
  fun quranNaming(): QuranNaming
  fun recordedSessionLaunchers(): Set<RecordedSessionLauncher>
}

@BindingContainer
@ContributesTo(AppScope::class)
object ProgressBindings {
  /** Memorize adds one when it's in the build. */
  @Provides
  @ElementsIntoSet
  fun provideRecordedSessionLaunchers(): Set<RecordedSessionLauncher> = emptySet()
}
