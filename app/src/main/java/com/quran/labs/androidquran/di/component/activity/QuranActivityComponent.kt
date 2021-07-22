package com.quran.labs.androidquran.di.component.activity

import com.quran.labs.androidquran.di.module.activity.QuranActivityModule
import com.quran.labs.androidquran.ui.QuranActivity
import dagger.Subcomponent

@Subcomponent(modules = [QuranActivityModule::class])
interface QuranActivityComponent {
  fun inject(quranActivity: QuranActivity)

  @Subcomponent.Builder
  interface Builder {
    fun build(): QuranActivityComponent
  }
}
