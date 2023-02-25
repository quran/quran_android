package com.quran.labs.androidquran.di.module.activity

import com.quran.data.core.QuranInfo
import com.quran.data.core.QuranPageInfo
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.util.QuranPageInfoImpl
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.di.AyahActionFragmentProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet

@Module
class PagerActivityModule(private val pagerActivity: PagerActivity) {

  @Provides
  fun provideAyahSelectedListener(): AyahSelectedListener {
    return pagerActivity
  }

  @Provides
  fun provideQuranPageInfo(
    quranInfo: QuranInfo,
    quranDisplayData: QuranDisplayData
  ): QuranPageInfo {
    return QuranPageInfoImpl(pagerActivity, quranInfo, quranDisplayData)
  }

  @Provides
  @ActivityScope
  fun provideImageWidth(screenInfo: QuranScreenInfo): String {
    return if (QuranUtils.isDualPages(pagerActivity, screenInfo)) {
      screenInfo.tabletWidthParam
    } else {
      screenInfo.widthParam
    }
  }

  @Provides
  @ActivityScope
  fun provideTranslationUtil(quranInfo: QuranInfo): TranslationUtil {
    return TranslationUtil(quranInfo)
  }

  @Provides
  @ElementsIntoSet
  fun provideAdditionalAyahPanels(): Set<AyahActionFragmentProvider> {
    return emptySet()
  }
}
