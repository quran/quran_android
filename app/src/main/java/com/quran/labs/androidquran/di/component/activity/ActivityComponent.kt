package com.quran.labs.androidquran.di.component.activity

import android.content.Context
import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.di.qualifier.ActivityContext
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.BindsInstance
import dagger.Subcomponent

@ActivityScope
@MergeSubcomponent(ActivityLevelScope::class)
interface ActivityComponent {
  // subcomponents
  fun pagerActivityComponentFactory(): PagerActivityComponent.Factory
  fun quranActivityComponentFactory(): QuranActivityComponent.Factory

  @Subcomponent.Factory
  interface Factory {
    fun generate(@BindsInstance @ActivityContext context: Context): ActivityComponent
  }
}
