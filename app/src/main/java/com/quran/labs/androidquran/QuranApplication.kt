package com.quran.labs.androidquran

import android.app.Application
import android.content.ComponentCallbacks2
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.VisibleForTesting
import androidx.work.Configuration
import com.quran.common.search.RustArabicSearchEngine
import com.quran.data.di.AppCoroutineScope
import com.quran.labs.androidquran.common.audio.util.AudioDecoderCapabilities
import com.quran.labs.androidquran.core.worker.QuranWorkerFactory
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
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
import kotlinx.coroutines.launch
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

  override fun provideQuranApplicationComponent(): QuranApplicationComponent {
    return applicationComponent
  }

  override fun onCreate() {
    super.onCreate()
    setupTimber()
    applicationComponent = initializeInjector()
    applicationComponent.inject(this)

    // Register foreground sync observer on the main thread without eagerly initializing the
    // sync database/graph; heavy graph warm-up, widget check, and codec probe run off-main.
    val syncManager = quranSyncManager()
    syncManager.startForegroundSync()
    initializeWorkManager()

    appCoroutineScope.launch {
      syncManager.warmUpIfConfigured()
      bookmarksWidgetSubscriber().subscribeBookmarksWidgetIfNecessary()
      audioDecoderCapabilities().isOpusSupported()
    }

    // theme setup
    val theme = quranSettings.currentTheme()
    ThemeUtil.setTheme(theme)
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
    private const val SURA_HEADER_DRAWER_CLASS =
      "com.quran.labs.androidquran.common.drawing.helper.SuraHeaderDrawer"
  }
}
