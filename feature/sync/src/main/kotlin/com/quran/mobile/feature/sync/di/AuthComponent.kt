package com.quran.mobile.feature.sync.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.sync.QuranLoginActivity
import com.squareup.anvil.annotations.MergeSubcomponent

@ActivityScope
@MergeSubcomponent(ActivityLevelScope::class)
interface AuthComponent {
  fun inject(loginActivity: QuranLoginActivity)

  @MergeSubcomponent.Factory
  interface Factory {
    fun generate(): AuthComponent
  }
}

