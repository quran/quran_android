package com.quran.labs.androidquran

import android.app.Application
import androidx.core.os.UserManagerCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import com.quran.data.di.AppCoroutineScope
import com.quran.labs.androidquran.common.audio.util.AudioDecoderCapabilities
import com.quran.labs.androidquran.core.worker.QuranWorkerFactory
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.RecordingLogTree
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.labs.androidquran.widget.BookmarksWidgetSubscriber
import com.quran.mobile.di.QuranApplicationComponent
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.sync.QuranSyncManager
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.launch
import timber.log.Timber

@HasMemberInjections
open class QuranApplication : Application(), QuranApplicationComponentProvider, Configuration.Provider {
  lateinit var applicationComponent: ApplicationComponent

  @Inject lateinit var quranWorkerFactory: QuranWorkerFactory
  @Inject lateinit var bookmarksWidgetSubscriber: BookmarksWidgetSubscriber
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var quranSyncManager: QuranSyncManager
  @Inject lateinit var appCoroutineScope: AppCoroutineScope
  @Inject lateinit var audioDecoderCapabilities: AudioDecoderCapabilities

  override fun provideQuranApplicationComponent(): QuranApplicationComponent {
    return applicationComponent
  }

  override fun onCreate() {
    super.onCreate()
    setupTimber()
    applicationComponent = initializeInjector()
    applicationComponent.inject(this)
    quranSyncManager.startForegroundSync()
    initializeWorkManager()
    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()

    // Warm up the (lazy, cached) decoder-capability probe off the main thread so the first
    // playback doesn't pay the MediaCodecList query cost synchronously.
    appCoroutineScope.launch { audioDecoderCapabilities.isOpusSupported() }

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
      .setWorkerFactory(quranWorkerFactory)
      .build()

  open fun initializeWorkManager() {
    if (UserManagerCompat.isUserUnlocked(this)) {
      WorkManager.initialize(this, workManagerConfiguration)
    }
  }
}
