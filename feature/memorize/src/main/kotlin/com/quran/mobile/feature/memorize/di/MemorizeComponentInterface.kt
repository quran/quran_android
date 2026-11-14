package com.quran.mobile.feature.memorize.di

import com.quran.data.core.QuranInfo
import com.quran.data.di.AppScope
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import com.quran.mobile.feature.memorize.recording.RecordingPlayer
import com.quran.mobile.feature.memorize.recording.RecordingStore
import com.quran.mobile.feature.memorize.session.MemorizeSessionController
import com.quran.recitation.pages.PagePreviews
import dev.zacsweers.metro.ContributesTo

/** Lets Android components that aren't injected (the service, the model import) reach the feature. */
@ContributesTo(AppScope::class)
interface MemorizeComponentInterface {
  fun memorizeSessionController(): MemorizeSessionController
  fun speechModelStore(): SpeechModelStore
  fun recordingStore(): RecordingStore
  fun recordingPlayer(): RecordingPlayer
  fun pagePreviews(): PagePreviews
  fun memorizePreferences(): MemorizePreferences
  fun quranInfo(): QuranInfo
}
