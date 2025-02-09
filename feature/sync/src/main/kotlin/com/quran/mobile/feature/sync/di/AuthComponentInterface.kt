package com.quran.mobile.feature.sync.di

import com.quran.data.di.AppScope
import com.squareup.anvil.annotations.ContributesTo

@ContributesTo(AppScope::class)
interface AuthComponentInterface {
  fun authComponentFactory(): AuthComponent.Factory
}
