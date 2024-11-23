package com.quran.data.page.provider.di

import com.quran.labs.androidquran.extra.feature.linebyline.QuranLineByLineWrapperView
import com.quran.mobile.di.QuranReadingPageComponentProvider

fun QuranLineByLineWrapperView.inject(vararg pages: Int) {
  (((context as? QuranReadingPageComponentProvider)
    ?.provideQuranReadingPageComponent(*pages)) as? LineByLineWrapperViewInjector)
    ?.inject(this)
}
