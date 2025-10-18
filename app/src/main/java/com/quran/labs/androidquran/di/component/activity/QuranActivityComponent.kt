package com.quran.labs.androidquran.di.component.activity

import com.quran.data.di.QuranActivityLevelScope
import com.quran.data.di.QuranActivityScope
import com.quran.labs.androidquran.di.module.activity.QuranActivityBindingContainer
import com.quran.labs.androidquran.ui.QuranActivity
import dev.zacsweers.metro.GraphExtension

@QuranActivityScope
@GraphExtension(scope = QuranActivityLevelScope::class, bindingContainers = [QuranActivityBindingContainer::class])
interface QuranActivityComponent {
  fun inject(quranActivity: QuranActivity)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): QuranActivityComponent
  }
}
