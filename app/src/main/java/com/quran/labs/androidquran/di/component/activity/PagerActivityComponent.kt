package com.quran.labs.androidquran.di.component.activity

import com.quran.data.di.QuranReadingScope
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.di.component.fragment.QuranPageComponent
import com.quran.labs.androidquran.di.module.activity.PagerActivityModule
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.fragment.AyahTranslationFragment
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.Subcomponent

@ActivityScope
@MergeSubcomponent(QuranReadingScope::class, modules = [PagerActivityModule::class])
interface PagerActivityComponent {
  // subcomponents
  fun quranPageComponentBuilder(): QuranPageComponent.Builder

  fun inject(pagerActivity: PagerActivity?)
  fun inject(ayahTranslationFragment: AyahTranslationFragment)

  @Subcomponent.Builder
  interface Builder {
    fun withPagerActivityModule(pagerModule: PagerActivityModule): Builder
    fun build(): PagerActivityComponent
  }
}
