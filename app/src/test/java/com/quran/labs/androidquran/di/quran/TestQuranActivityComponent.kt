package com.quran.labs.androidquran.di.quran

import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.di.component.activity.QuranActivityComponent
import dagger.Subcomponent

@ActivityScope
@Subcomponent(modules = [TestQuranActivityModule::class])
interface TestQuranActivityComponent : QuranActivityComponent {

  @Subcomponent.Factory
  interface Factory : QuranActivityComponent.Factory {
    override fun generate(): TestQuranActivityComponent
  }
}
