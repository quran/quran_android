package com.quran.mobile.feature.downloadmanager.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.downloadmanager.AudioManagerActivity
import com.quran.mobile.feature.downloadmanager.SheikhAudioDownloadsActivity
import dev.zacsweers.metro.GraphExtension

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface DownloadManagerComponent {
  fun inject(audioManagerActivity: AudioManagerActivity)
  fun inject(sheikhAudioDownloadsActivity: SheikhAudioDownloadsActivity)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): DownloadManagerComponent
  }
}
