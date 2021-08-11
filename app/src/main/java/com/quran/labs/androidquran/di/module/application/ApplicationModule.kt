package com.quran.labs.androidquran.di.module.application

import android.app.Application
import android.content.Context
import android.graphics.Point
import android.view.Display
import android.view.WindowManager
import com.quran.data.core.QuranFileManager
import com.quran.data.dao.Settings
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.SettingsImpl
import dagger.Module
import dagger.Provides
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import java.io.File
import javax.inject.Singleton

@Module
class ApplicationModule(private val application: Application) {

  @Provides
  fun provideApplicationContext(): Context {
    return application
  }

  @Provides
  fun provideDisplay(appContext: Context): Display {
    val w = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return w.defaultDisplay
  }

  @Provides
  fun provideDisplaySize(display: Display): DisplaySize {
    val point = Point()
    display.getRealSize(point)
    return DisplaySize(point.x, point.y)
  }

  @Provides
  fun provideQuranPageSizeCalculator(
    pageProvider: PageProvider,
    displaySize: DisplaySize
  ): PageSizeCalculator {
    return pageProvider.getPageSizeCalculator(displaySize)
  }

  @Provides
  @Singleton
  fun provideQuranSettings(): QuranSettings {
    return QuranSettings.getInstance(application)
  }

  @Provides
  fun provideSettings(settingsImpl: SettingsImpl): Settings {
    return settingsImpl
  }

  @Provides
  @Singleton
  fun provideQuranFileManager(quranFileUtils: QuranFileUtils): QuranFileManager {
    return quranFileUtils
  }

  @Provides
  fun provideMainThreadScheduler(): Scheduler {
    return AndroidSchedulers.mainThread()
  }

  @Provides
  fun provideCacheDirectory(): File {
    return application.cacheDir
  }
}
