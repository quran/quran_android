package com.quran.mobile.feature.downloadmanager.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.downloadmanager.AudioManagerActivity
import com.quran.mobile.feature.downloadmanager.SheikhAudioDownloadsActivity
import com.squareup.anvil.annotations.MergeSubcomponent

@ActivityScope
@MergeSubcomponent(ActivityLevelScope::class)
interface DownloadManagerComponent {
  fun inject(audioManagerActivity: AudioManagerActivity)
  fun inject(sheikhAudioDownloadsActivity: SheikhAudioDownloadsActivity)

  @MergeSubcomponent.Factory
  interface Factory {
    fun generate(): DownloadManagerComponent
  }
}
