package com.quran.labs.androidquran.di.component.fragment

import com.quran.data.di.QuranPageScope
import com.quran.data.di.QuranReadingPageScope
import com.quran.labs.androidquran.ui.fragment.QuranPageFragment
import com.quran.labs.androidquran.ui.fragment.TabletFragment
import com.quran.labs.androidquran.ui.fragment.TranslationFragment
import com.quran.mobile.di.QuranReadingPageComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.BindsInstance
import dagger.Subcomponent

@QuranPageScope
@MergeSubcomponent(QuranReadingPageScope::class)
interface QuranPageComponent: QuranReadingPageComponent {
  fun inject(quranPageFragment: QuranPageFragment)
  fun inject(tabletFragment: TabletFragment)
  fun inject(translationFragment: TranslationFragment)

  @Subcomponent.Factory
  interface Factory {
    fun generate(@BindsInstance pages: IntArray): QuranPageComponent
  }
}
