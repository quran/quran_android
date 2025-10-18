package com.quran.mobile.feature.downloadmanager.di

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
interface DownloadManagerComponentInterface {
  fun downloadManagerComponentFactory(): DownloadManagerComponent.Factory
}
