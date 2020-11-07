package com.quran.labs.androidquran

import android.content.Context
import android.content.res.Resources
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import androidx.work.WorkManager
import com.quran.labs.androidquran.core.worker.QuranWorkerFactory
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import com.quran.labs.androidquran.di.component.application.DaggerApplicationComponent
import com.quran.labs.androidquran.di.module.application.ApplicationModule
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.RecordingLogTree
import com.quran.labs.androidquran.widget.BookmarksWidgetSubscriber
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

open class QuranApplication : MultiDexApplication() {
  lateinit var applicationComponent: ApplicationComponent

  @Inject
  lateinit var quranWorkerFactory: QuranWorkerFactory

  @Inject
  lateinit var bookmarksWidgetSubscriber: BookmarksWidgetSubscriber

  override fun onCreate() {
    super.onCreate()
    setupTimber()
    applicationComponent = initializeInjector()
    applicationComponent.inject(this)
    WorkManager.initialize(
        this,
        Configuration.Builder()
            .setWorkerFactory(quranWorkerFactory)
            .build()
    )
    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()
  }

  open fun setupTimber() {
    Timber.plant(RecordingLogTree())
  }

  open fun initializeInjector(): ApplicationComponent {
    return DaggerApplicationComponent.builder()
        .applicationModule(ApplicationModule(this))
        .build()
  }

  fun refreshLocale(
    context: Context,
    force: Boolean
  ) {
    val language = if (QuranSettings.getInstance(this).isArabicNames) "ar" else null
    val locale: Locale
    locale = when {
      "ar" == language -> {
        Locale("ar")
      }
      force -> {
        // get the system locale (since we overwrote the default locale)
        Resources.getSystem().configuration.locale
      }
      else -> {
        // nothing to do...
        return
      }
    }
    updateLocale(context, locale)
    val appContext = context.applicationContext
    if (context !== appContext) {
      updateLocale(appContext, locale)
    }
  }

  private fun updateLocale(context: Context, locale: Locale) {
    val resources = context.resources
    val config = resources.configuration
    config.locale = locale
    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
      config.setLayoutDirection(config.locale)
    }
    resources.updateConfiguration(config, resources.displayMetrics)
  }
}
