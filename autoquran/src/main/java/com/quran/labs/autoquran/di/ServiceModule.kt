package com.quran.labs.autoquran.di

import android.content.Context
import com.quran.data.model.audio.Qari
import com.quran.data.page.provider.QuranDataModule
import com.quran.data.source.PageProvider
import com.quran.data.source.QuranDataSource
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider
import com.quran.labs.feature.autoquran.service.QuranBrowsableAudioPlaybackService
import com.quran.mobile.di.qualifier.ApplicationContext
import dagger.Component
import dagger.Module
import dagger.Provides

@Module
class ServiceModule(private val appContext: Context) {

  @ApplicationContext
  @Provides
  fun provideApplicationContext(): Context = appContext

  @Provides
  fun provideQuranPageProvider(
    providers: Map<@JvmSuppressWildcards String, @JvmSuppressWildcards PageProvider>
  ): PageProvider {
    return providers["madani"]!!
  }

  @Provides
  fun provideQuranDataSource(pageProvider: PageProvider): QuranDataSource {
    return pageProvider.getDataSource()
  }

  @Provides
  fun provideAudioExtensionDecider(): AudioExtensionDecider {
    return object : AudioExtensionDecider {
      override fun audioExtensionForQari(qari: Qari): String = "mp3"
      override fun audioExtensionForQari(qariItem: QariItem): String = "mp3"
      override fun allowedAudioExtensions(qari: Qari): List<String> = listOf("mp3")
      override fun allowedAudioExtensions(qariItem: QariItem): List<String> = listOf("mp3")
    }
  }
}

@Component(modules = [ServiceModule::class, QuranDataModule::class])
interface ServiceComponent {
  fun inject(service: QuranBrowsableAudioPlaybackService)
}
