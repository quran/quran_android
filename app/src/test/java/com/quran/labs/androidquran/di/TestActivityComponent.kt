package com.quran.labs.androidquran.di

import android.content.Context
import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.di.component.activity.ActivityComponent
import com.quran.labs.androidquran.di.quran.TestQuranActivityComponent
import com.quran.mobile.di.qualifier.ActivityContext
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.BindsInstance
import dagger.Subcomponent

@ActivityScope
@MergeSubcomponent(ActivityLevelScope::class)
interface TestActivityComponent : ActivityComponent {
  // subcomponents
  override fun quranActivityComponentFactory(): TestQuranActivityComponent.Factory

  @Subcomponent.Factory
  interface Factory : ActivityComponent.Factory {
    override fun generate(@BindsInstance @ActivityContext context: Context): TestActivityComponent
  }
}

