package com.quran.labs.androidquran.di.quran

import com.quran.labs.androidquran.di.component.activity.QuranActivityComponent
import dagger.Subcomponent

@Subcomponent(modules = [TestQuranActivityModule::class])
interface TestQuranActivityComponent : QuranActivityComponent {

  @Subcomponent.Factory
  interface Factory : QuranActivityComponent.Factory {
    override fun generate(): TestQuranActivityComponent
  }
}
