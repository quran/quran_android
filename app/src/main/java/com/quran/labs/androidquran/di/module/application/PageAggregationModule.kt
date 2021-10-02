package com.quran.labs.androidquran.di.module.application

import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.source.PageProvider
import com.quran.data.source.QuranDataSource
import dagger.Module
import dagger.Provides
import javax.inject.Named

@Module
object PageAggregationModule {

  @Provides
  fun provideQuranPageProvider(
    providers: Map<@JvmSuppressWildcards String, @JvmSuppressWildcards PageProvider>,
    @Named(DependencyInjectionConstants.CURRENT_PAGE_TYPE) pageType: String
  ): PageProvider {
    // explicitly error if this doesn't exist, since it should never happen
    return providers[pageType]!!
  }

  @Provides
  fun provideQuranDataSource(pageProvider: PageProvider): QuranDataSource {
    return pageProvider.getDataSource()
  }
}
