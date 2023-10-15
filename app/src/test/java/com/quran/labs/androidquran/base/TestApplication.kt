package com.quran.labs.androidquran.base

import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.di.DaggerTestApplicationComponent
import com.quran.labs.androidquran.di.component.application.ApplicationComponent

class TestApplication : QuranApplication() {

  override fun initializeInjector(): ApplicationComponent {
    return DaggerTestApplicationComponent.factory()
      .generate(this)
  }

  override fun setupTimber() {
  }

  override fun initializeWorkManager() {
  }
}
