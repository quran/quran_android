package com.quran.labs.androidquran.base

import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.di.DaggerTestApplicationComponent
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import com.quran.labs.androidquran.di.module.application.ApplicationModule

class TestApplication : QuranApplication() {

  override fun initializeInjector(): ApplicationComponent {
    return DaggerTestApplicationComponent.builder()
        .applicationModule(ApplicationModule(this))
        .build()
  }

  override fun setupTimber() {
  }
}
