package com.quran.labs.androidquran.core.worker.di

import com.quran.labs.androidquran.feature.audio.api.AudioUpdateService
import dagger.Module
import dagger.Provides
import retrofit2.Retrofit.Builder
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
object AudioUpdateModule {

  @Provides
  fun provideAudioUpdateService(): AudioUpdateService {
    val retrofit = Builder()
        .baseUrl("https://quran.app/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
    return retrofit.create(AudioUpdateService::class.java)
  }
}
