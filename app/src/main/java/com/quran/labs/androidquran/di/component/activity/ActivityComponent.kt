package com.quran.labs.androidquran.di.component.activity

import android.content.Context
import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.di.qualifier.ActivityContext
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface ActivityComponent {
  // subcomponents
  fun pagerActivityComponentFactory(): PagerActivityComponent.Factory
  fun quranActivityComponentFactory(): QuranActivityComponent.Factory

  @GraphExtension.Factory
  interface Factory {
    fun generate(@Provides @ActivityContext context: Context): ActivityComponent
  }
}
