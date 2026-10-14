package com.quran.mobile.feature.sync.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.sync.QuranSyncActivity
import dev.zacsweers.metro.GraphExtension

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface QuranSyncComponent {
  fun inject(quranSyncActivity: QuranSyncActivity)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): QuranSyncComponent
  }
}
