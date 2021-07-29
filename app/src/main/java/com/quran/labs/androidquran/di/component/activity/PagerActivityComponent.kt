package com.quran.labs.androidquran.di.component.activity

import com.quran.labs.androidquran.di.ActivityScope
import com.quran.labs.androidquran.di.component.fragment.QuranPageComponent
import com.quran.labs.androidquran.di.module.activity.PagerActivityModule
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.fragment.AyahTranslationFragment
import dagger.Subcomponent

@ActivityScope
@Subcomponent(modules = [PagerActivityModule::class])
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
