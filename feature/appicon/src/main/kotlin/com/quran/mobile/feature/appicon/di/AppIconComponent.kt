package com.quran.mobile.feature.appicon.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.appicon.AppIconActivity
import dev.zacsweers.metro.GraphExtension

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface AppIconComponent {
  fun inject(appIconActivity: AppIconActivity)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): AppIconComponent
  }
}
