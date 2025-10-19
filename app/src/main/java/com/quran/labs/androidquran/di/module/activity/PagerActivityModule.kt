package com.quran.labs.androidquran.di.module.activity

import android.content.Context
import com.quran.data.core.QuranInfo
import com.quran.data.core.QuranPageInfo
import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.util.QuranPageInfoImpl
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.di.qualifier.ActivityContext
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.Provides

@ContributesTo(ActivityLevelScope::class)
@BindingContainer
object PagerActivityModule {

  @Provides
  fun provideQuranPageInfo(
    @ActivityContext context: Context,
    quranInfo: QuranInfo,
    quranDisplayData: QuranDisplayData
  ): QuranPageInfo {
    return QuranPageInfoImpl(context, quranInfo, quranDisplayData)
  }

  @Provides
  @ActivityScope
  fun provideImageWidth(@ActivityContext context: Context, screenInfo: QuranScreenInfo): String {
    return if (QuranUtils.isDualPages(context, screenInfo, false)) {
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
