package com.quran.labs.androidquran

import android.app.Application
import android.content.ComponentCallbacks2
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.VisibleForTesting
import androidx.work.Configuration
import com.quran.common.search.RustArabicSearchEngine
import com.quran.data.di.AppCoroutineScope
import com.quran.labs.androidquran.common.audio.util.AudioDecoderCapabilities
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.BitmapPoolTrimRegistry
import com.quran.labs.androidquran.core.worker.QuranWorkerFactory
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import com.quran.labs.androidquran.hybrid.contract.DailyWirdRepository
import com.quran.labs.androidquran.hybrid.hub.HybridIndexDataSource
import com.quran.labs.androidquran.hybrid.ui.VectorPathDrawable
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.RecordingLogTree
import com.quran.labs.androidquran.util.SearchResultFormatter
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.labs.androidquran.widget.BookmarksWidgetSubscriber
import com.quran.mobile.di.QuranApplicationComponent
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.sync.QuranSyncManager
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HasMemberInjections
open class QuranApplication : Application(), QuranApplicationComponentProvider, Configuration.Provider {
  lateinit var applicationComponent: ApplicationComponent

  @Inject lateinit var quranWorkerFactory: Provider<QuranWorkerFactory>
  @Inject lateinit var bookmarksWidgetSubscriber: Provider<BookmarksWidgetSubscriber>
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var quranSyncManager: Provider<QuranSyncManager>
  @Inject lateinit var appCoroutineScope: AppCoroutineScope
  @Inject lateinit var audioDecoderCapabilities: Provider<AudioDecoderCapabilities>
  @Inject lateinit var dailyWirdRepository: Provider<DailyWirdRepository>
  @Inject lateinit var hybridIndexDataSource: Provider<HybridIndexDataSource>

  override fun provideQuranApplicationComponent(): QuranApplicationComponent {
    return applicationComponent
  }

  override fun onCreate() {
    super.onCreate()
    setupTimber()
    applicationComponent = initializeInjector()
    applicationComponent.inject(this)

    // Register foreground sync observer off Application.onCreate(); heavy graph warm-up,
    // widget check, and sync observer setup run asynchronously without stalling cold start.
    initializeWorkManager()
    scheduleColdStartPrewarm()
    scheduleStartupSync()

    // theme setup
    val theme = quranSettings.currentTheme()
    ThemeUtil.setTheme(theme)
    ThemeUtil.applyThemeStyle(AppThemeStyle.fromPreference(quranSettings.appThemeStyle))
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
      .setWorkerFactory(quranWorkerFactory())
      .build()

  /**
   * WorkManager uses on-demand initialization via [Configuration.Provider] when first requested
   * off the main thread; retained as an open hook for test subclasses.
   */
  open fun initializeWorkManager() {
    // On-demand initialization via workManagerConfiguration avoids main-thread Room/SQLite startup.
  }

  open fun scheduleColdStartPrewarm() {
    appCoroutineScope.launch(Dispatchers.IO) {
      delay(COLD_START_PREWARM_DELAY_MS)
      dailyWirdRepository().stateFlow
      hybridIndexDataSource().prewarmSuras(this@QuranApplication)
      VectorPathDrawable.prewarmHubPaths()
    }
  }

  open fun scheduleStartupSync() {
    appCoroutineScope.launch {
      delay(STARTUP_SYNC_DELAY_MS)
      val syncManager = quranSyncManager()
      syncManager.warmUpIfConfigured()
      if (syncManager.isConfigured) {
        withContext(Dispatchers.Main.immediate) {
          syncManager.startForegroundSync()
        }
      }
      bookmarksWidgetSubscriber().subscribeBookmarksWidgetIfNecessary()
    }
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    trimCaches(level)
  }

  @Deprecated("Deprecated in Java")
  override fun onLowMemory() {
    super.onLowMemory()
    trimCaches(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
  }

  @VisibleForTesting
  internal fun trimCaches(level: Int) {
    if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
      BitmapPoolTrimRegistry.clearAll()
    }
    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
      level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
    ) {
      SearchResultFormatter.clearCache()
      SQLiteDatabase.releaseMemory()
    }
    if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
      level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
    ) {
      RustArabicSearchEngine.releaseIndex()
    }
    if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
      QuranUtils.clearLocalizedNumberCache()
      clearSuraHeaderCache()
    }
  }

  private fun clearSuraHeaderCache() {
    try {
      val clazz = Class.forName(SURA_HEADER_DRAWER_CLASS)
      clazz.getMethod("clearCache").invoke(null)
    } catch (_: Throwable) {
      // Flavor may not include SuraHeaderDrawer; ignore safely.
    }
  }

  private companion object {
    private const val COLD_START_PREWARM_DELAY_MS = 1200L
    private const val STARTUP_SYNC_DELAY_MS = 8500L
    private const val SURA_HEADER_DRAWER_CLASS =
      "com.quran.labs.androidquran.common.drawing.helper.SuraHeaderDrawer"
  }
}
