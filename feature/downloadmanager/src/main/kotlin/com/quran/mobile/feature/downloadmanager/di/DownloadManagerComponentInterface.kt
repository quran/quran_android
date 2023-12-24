package com.quran.mobile.feature.downloadmanager.di

import com.quran.data.di.AppScope
import com.squareup.anvil.annotations.ContributesTo

@ContributesTo(AppScope::class)
interface DownloadManagerComponentInterface {
  fun downloadManagerComponentFactory(): DownloadManagerComponent.Factory
}
