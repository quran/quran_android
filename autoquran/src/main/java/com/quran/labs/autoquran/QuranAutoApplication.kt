package com.quran.labs.autoquran

import android.app.Application
import com.quran.labs.autoquran.di.DaggerServiceComponent
import com.quran.labs.feature.autoquran.di.QuranAutoInjector
import com.quran.labs.feature.autoquran.service.QuranBrowsableAudioPlaybackService
import com.quran.mobile.di.QuranApplicationComponent
import com.quran.mobile.di.QuranApplicationComponentProvider

class QuranAutoApplication : Application(), QuranApplicationComponentProvider {
  private lateinit var quranAutoApplicationComponent: QuranApplicationComponent

  override fun onCreate() {
    super.onCreate()
    quranAutoApplicationComponent = object : QuranApplicationComponent, QuranAutoInjector {
      private val serviceComponent by lazy { DaggerServiceComponent.create() }

      override fun inject(service: QuranBrowsableAudioPlaybackService) {
        serviceComponent.inject(service)
      }
    }
  }

  override fun provideQuranApplicationComponent(): QuranApplicationComponent {
    return quranAutoApplicationComponent
  }
}
