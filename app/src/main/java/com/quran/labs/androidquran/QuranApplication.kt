package com.quran.labs.androidquran

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration
import androidx.work.WorkManager
import com.quran.labs.androidquran.core.worker.QuranWorkerFactory
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import com.quran.labs.androidquran.di.component.application.DaggerApplicationComponent
import com.quran.labs.androidquran.util.RecordingLogTree
import com.quran.labs.androidquran.widget.BookmarksWidgetSubscriber
import com.quran.mobile.di.QuranApplicationComponent
import com.quran.mobile.di.QuranApplicationComponentProvider
import timber.log.Timber
import javax.inject.Inject

open class QuranApplication : Application(), QuranApplicationComponentProvider {
  lateinit var applicationComponent: ApplicationComponent

  @Inject lateinit var quranWorkerFactory: QuranWorkerFactory
  @Inject lateinit var bookmarksWidgetSubscriber: BookmarksWidgetSubscriber

  override fun provideQuranApplicationComponent(): QuranApplicationComponent {
    return applicationComponent
  }

  override fun onCreate() {
    super.onCreate()
    setupTimber()
    applicationComponent = initializeInjector()
    applicationComponent.inject(this)
    initializeWorkManager()
    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()

    // set dark mode as the default for now
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
  }

  open fun setupTimber() {
    Timber.plant(RecordingLogTree())
  }

  open fun initializeInjector(): ApplicationComponent {
    return DaggerApplicationComponent.factory()
      .generate(this)
  }

  open fun initializeWorkManager() {
    WorkManager.initialize(
      this,
      Configuration.Builder()
        .setWorkerFactory(quranWorkerFactory)
        .build()
    )
  }
}
