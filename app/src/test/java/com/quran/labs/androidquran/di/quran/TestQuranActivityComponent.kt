package com.quran.labs.androidquran.di.quran

import com.quran.data.di.QuranActivityLevelScope
import com.quran.data.di.QuranActivityScope
import com.quran.labs.androidquran.di.component.activity.QuranActivityComponent
import com.squareup.anvil.annotations.MergeSubcomponent

@QuranActivityScope
@MergeSubcomponent(scope = QuranActivityLevelScope::class, modules = [TestQuranActivityModule::class])
interface TestQuranActivityComponent : QuranActivityComponent {

  @MergeSubcomponent.Factory
  interface Factory : QuranActivityComponent.Factory {
    override fun generate(): TestQuranActivityComponent
  }
}
