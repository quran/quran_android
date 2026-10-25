package com.quran.mobile.feature.appicon.di

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
interface AppIconComponentInterface {
  fun appIconComponentFactory(): AppIconComponent.Factory
}
