package com.quran.labs.autoquran.di

import android.content.Context
import com.quran.data.source.PageProvider
import com.quran.data.source.QuranDataSource
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider
import com.quran.labs.androidquran.common.audio.util.QuranAudioExtensionDecider
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides

@BindingContainer
class ServiceBindings(private val appContext: Context) {

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
  fun provideAudioExtensionDecider(quranAudioExtensionDecider: QuranAudioExtensionDecider): AudioExtensionDecider =
    quranAudioExtensionDecider
}
