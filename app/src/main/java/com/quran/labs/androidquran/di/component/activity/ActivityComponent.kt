package com.quran.labs.androidquran.di.component.activity

import android.content.Context
import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.model.quran.CoordinatesModelInterface
import com.quran.labs.androidquran.ui.helpers.QuranPageLoaderInterface
import com.quran.mobile.di.qualifier.ActivityContext
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface ActivityComponent {
  // subcomponents
  fun pagerActivityComponentFactory(): PagerActivityComponent.Factory
  fun quranActivityComponentFactory(): QuranActivityComponent.Factory

  // pages outside the reading screen (AppPagePreviews)
  fun quranPageLoader(): QuranPageLoaderInterface
  fun coordinatesModel(): CoordinatesModelInterface

  @GraphExtension.Factory
  interface Factory {
    fun generate(@Provides @ActivityContext context: Context): ActivityComponent
  }
}
