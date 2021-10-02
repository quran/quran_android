package com.quran.labs.androidquran.di.module.activity

import android.content.Context
import androidx.core.content.ContextCompat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.TranslationUtil
import dagger.Module
import dagger.Provides

@Module
class PagerActivityModule(private val pagerActivity: PagerActivity) {

  @Provides
  fun provideAyahSelectedListener(): AyahSelectedListener {
    return pagerActivity
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
  fun provideTranslationUtil(context: Context, quranInfo: QuranInfo): TranslationUtil {
    return TranslationUtil(
      ContextCompat.getColor(context, R.color.translation_translator_color),
      quranInfo
    )
  }
}
