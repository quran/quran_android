package com.quran.mobile.feature.downloadmanager.di

import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.downloadmanager.AudioManagerActivity
import com.quran.mobile.feature.downloadmanager.SheikhAudioDownloadsActivity
import dagger.Subcomponent

@ActivityScope
@Subcomponent
interface DownloadManagerComponent {
  fun inject(audioManagerActivity: AudioManagerActivity)
  fun inject(sheikhAudioDownloadsActivity: SheikhAudioDownloadsActivity)

  @Subcomponent.Factory
  interface Factory {
    fun generate(): DownloadManagerComponent
  }
}
