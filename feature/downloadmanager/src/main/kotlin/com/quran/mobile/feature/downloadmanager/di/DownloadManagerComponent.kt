package com.quran.mobile.feature.downloadmanager.di

import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.downloadmanager.AudioManagerActivity
import dagger.Subcomponent

@ActivityScope
@Subcomponent
interface DownloadManagerComponent {
  fun inject(audioManagerActivity: AudioManagerActivity)

  @Subcomponent.Builder
  interface Builder {
    fun build(): DownloadManagerComponent
  }
}
