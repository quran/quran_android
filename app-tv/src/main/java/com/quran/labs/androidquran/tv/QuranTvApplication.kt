package com.quran.labs.androidquran.tv

import android.app.Application
import timber.log.Timber

class QuranTvApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    setupTimber()
  }

  private fun setupTimber() {
    Timber.plant(Timber.DebugTree())
  }
}
