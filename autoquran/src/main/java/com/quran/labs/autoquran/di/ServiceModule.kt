package com.quran.labs.autoquran.di

import com.quran.data.page.provider.QuranDataModule
import com.quran.data.source.PageProvider
import com.quran.data.source.QuranDataSource
import dagger.Component
import dagger.Module
import dagger.Provides

@Module
class ServiceModule {
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
}

@Component(modules = [ServiceModule::class, QuranDataModule::class])
interface ServiceComponent {
  fun inject(service: QuranAudioService)
}
