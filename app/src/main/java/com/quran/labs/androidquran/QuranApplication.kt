package com.quran.labs.androidquran

import android.app.Application
import android.os.Build
import androidx.core.os.UserManagerCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import com.quran.labs.androidquran.core.worker.QuranWorkerFactory
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.RecordingLogTree
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.labs.androidquran.widget.BookmarksWidgetSubscriber
import com.quran.mobile.di.QuranApplicationComponent
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.sync.QuranSyncManager
import com.quran.mobile.feature.progress.ProgressStartup
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import timber.log.Timber
import java.io.File

@HasMemberInjections
open class QuranApplication : Application(), QuranApplicationComponentProvider, Configuration.Provider {
  lateinit var applicationComponent: ApplicationComponent

  @Inject lateinit var quranWorkerFactory: QuranWorkerFactory
  @Inject lateinit var bookmarksWidgetSubscriber: BookmarksWidgetSubscriber
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var quranSyncManager: QuranSyncManager
  @Inject lateinit var progressStartup: ProgressStartup

  override fun provideQuranApplicationComponent(): QuranApplicationComponent {
    return applicationComponent
  }

  override fun onCreate() {
    super.onCreate()
    // A secondary process (e.g. memorize's double-check, which keeps its model apart from the
    // speech engine's) runs a service of its own and needs none of the app's setup.
    if (isSecondaryProcess()) return
    setupTimber()
    applicationComponent = initializeInjector()
    applicationComponent.inject(this)
    quranSyncManager.startForegroundSync()
    initializeWorkManager()
    startProgress()
    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()

    // theme setup
    val theme = quranSettings.currentTheme()
    ThemeUtil.setTheme(theme)
  }

  private fun isSecondaryProcess(): Boolean {
    val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      getProcessName()
    } else {
      runCatching { File("/proc/self/cmdline").readText().trim(Char(0), ' ') }.getOrNull()
    }
    return name != null && name.contains(':')
  }

  open fun setupTimber() {
    Timber.plant(RecordingLogTree())
  }

  open fun initializeInjector(): ApplicationComponent {
    return createGraphFactory<ApplicationComponent.Factory>()
      .generate(this)
  }

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder()
      .setWorkerFactory(quranWorkerFactory)
      .build()

  /**
   * Progress keeps each reading bookmark's moves and checks the daily reminder (its data is only
   * readable once the user unlocked, as WorkManager is only initialized then).
   */
  open fun startProgress() {
    if (UserManagerCompat.isUserUnlocked(this)) progressStartup.start()
  }

  open fun initializeWorkManager() {
    if (UserManagerCompat.isUserUnlocked(this)) {
      WorkManager.initialize(this, workManagerConfiguration)
    }
  }
}
