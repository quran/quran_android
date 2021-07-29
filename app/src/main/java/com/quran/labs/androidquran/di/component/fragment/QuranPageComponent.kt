package com.quran.labs.androidquran.di.component.fragment

import com.quran.labs.androidquran.di.QuranPageScope
import com.quran.labs.androidquran.di.module.fragment.QuranPageModule
import com.quran.labs.androidquran.ui.fragment.QuranPageFragment
import com.quran.labs.androidquran.ui.fragment.TabletFragment
import com.quran.labs.androidquran.ui.fragment.TranslationFragment
import dagger.Subcomponent

@QuranPageScope
@Subcomponent(modules = [QuranPageModule::class])
interface QuranPageComponent {
  fun inject(quranPageFragment: QuranPageFragment)
  fun inject(tabletFragment: TabletFragment)
  fun inject(translationFragment: TranslationFragment)

  @Subcomponent.Builder
  interface Builder {
    fun withQuranPageModule(quranPageModule: QuranPageModule): Builder
    fun build(): QuranPageComponent
  }
}
