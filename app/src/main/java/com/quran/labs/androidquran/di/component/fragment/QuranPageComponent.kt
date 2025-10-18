package com.quran.labs.androidquran.di.component.fragment

import com.quran.data.di.QuranPageScope
import com.quran.data.di.QuranReadingPageScope
import com.quran.labs.androidquran.ui.fragment.QuranPageFragment
import com.quran.labs.androidquran.ui.fragment.TabletFragment
import com.quran.labs.androidquran.ui.fragment.TranslationFragment
import com.quran.mobile.di.QuranReadingPageComponent
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

@QuranPageScope
@GraphExtension(QuranReadingPageScope::class)
interface QuranPageComponent: QuranReadingPageComponent {
  fun inject(quranPageFragment: QuranPageFragment)
  fun inject(tabletFragment: TabletFragment)
  fun inject(translationFragment: TranslationFragment)

  @GraphExtension.Factory
  interface Factory {
    fun generate(@Provides pages: IntArray): QuranPageComponent
  }
}
