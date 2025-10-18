package com.quran.labs.autoquran

import android.app.Application
import com.quran.labs.autoquran.di.ServiceBindings
import com.quran.labs.autoquran.di.ServiceGraph
import com.quran.labs.feature.autoquran.di.QuranAutoInjector
import com.quran.labs.feature.autoquran.service.QuranBrowsableAudioPlaybackService
import com.quran.mobile.di.QuranApplicationComponent
import com.quran.mobile.di.QuranApplicationComponentProvider
import dev.zacsweers.metro.createGraphFactory

class QuranAutoApplication : Application(), QuranApplicationComponentProvider {
  private lateinit var quranAutoApplicationComponent: QuranApplicationComponent

  override fun onCreate() {
    super.onCreate()
    quranAutoApplicationComponent = object : QuranApplicationComponent, QuranAutoInjector {
      private val serviceGraph by lazy {
        createGraphFactory<ServiceGraph.Factory>()
          .create(ServiceBindings(this@QuranAutoApplication))
      }

      override fun inject(service: QuranBrowsableAudioPlaybackService) {
        serviceGraph.inject(service)
      }
    }
  }

  override fun provideQuranApplicationComponent(): QuranApplicationComponent {
    return quranAutoApplicationComponent
  }
}
