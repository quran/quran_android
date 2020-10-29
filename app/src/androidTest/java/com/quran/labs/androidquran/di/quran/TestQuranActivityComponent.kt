package com.quran.labs.androidquran.di.quran

import com.quran.labs.androidquran.di.ActivityScope
import com.quran.labs.androidquran.di.component.activity.QuranActivityComponent
import dagger.Subcomponent

@ActivityScope
@Subcomponent(modules = [TestQuranActivityModule::class])
interface TestQuranActivityComponent : QuranActivityComponent {

  @Subcomponent.Builder
  interface Builder : QuranActivityComponent.Builder {
    override fun build(): TestQuranActivityComponent
  }
}
