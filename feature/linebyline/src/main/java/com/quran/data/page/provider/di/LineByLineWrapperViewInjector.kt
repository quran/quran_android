package com.quran.data.page.provider.di

import com.quran.data.di.QuranReadingPageScope
import com.quran.labs.androidquran.extra.feature.linebyline.QuranLineByLineWrapperView
import dev.zacsweers.metro.ContributesTo

@ContributesTo(QuranReadingPageScope::class)
interface LineByLineWrapperViewInjector {
  fun inject(lineByLineWrapperView: QuranLineByLineWrapperView)
}
