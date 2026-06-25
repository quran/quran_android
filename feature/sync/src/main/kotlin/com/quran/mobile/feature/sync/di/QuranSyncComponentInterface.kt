package com.quran.mobile.feature.sync.di

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
interface QuranSyncComponentInterface {
  fun quranSyncComponentFactory(): QuranSyncComponent.Factory
}
