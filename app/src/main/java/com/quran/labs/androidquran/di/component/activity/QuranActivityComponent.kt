package com.quran.labs.androidquran.di.component.activity

import com.quran.data.di.QuranActivityLevelScope
import com.quran.data.di.QuranActivityScope
import com.quran.labs.androidquran.di.module.activity.QuranActivityModule
import com.quran.labs.androidquran.ui.QuranActivity
import com.squareup.anvil.annotations.MergeSubcomponent

@QuranActivityScope
@MergeSubcomponent(scope = QuranActivityLevelScope::class, modules = [QuranActivityModule::class])
interface QuranActivityComponent {
  fun inject(quranActivity: QuranActivity)

  @MergeSubcomponent.Factory
  interface Factory {
    fun generate(): QuranActivityComponent
  }
}
