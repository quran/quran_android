package com.quran.labs.androidquran.base

import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import dev.zacsweers.metro.createGraphFactory

class TestApplication : QuranApplication() {

  override fun initializeInjector(): ApplicationComponent {
    return createGraphFactory<ApplicationComponent.Factory>()
      .generate(this)
  }

  override fun setupTimber() {
  }

  override fun initializeWorkManager() {
  }
}
