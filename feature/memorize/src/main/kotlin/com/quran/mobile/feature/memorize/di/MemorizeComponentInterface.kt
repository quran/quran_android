package com.quran.mobile.feature.memorize.di

import com.quran.data.di.AppScope
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import com.quran.mobile.feature.memorize.session.MemorizeSessionController
import dev.zacsweers.metro.ContributesTo

/** Lets Android components that aren't injected (the service, the model import) reach the feature. */
@ContributesTo(AppScope::class)
interface MemorizeComponentInterface {
  fun memorizeSessionController(): MemorizeSessionController
  fun speechModelStore(): SpeechModelStore
}
