package com.quran.labs.androidquran.di.module.application

import android.content.Context
import android.graphics.Point
import android.view.Display
import android.view.WindowManager
import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.core.QuranFileManager
import com.quran.data.dao.Settings
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.data.QuranFileConstants
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.SettingsImpl
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.di.ExtraPreferencesProvider
import com.quran.mobile.di.ExtraScreenProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Scheduler
import okio.FileSystem
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
object ApplicationModule {

  @Provides
  fun provideDisplay(@ApplicationContext appContext: Context): Display {
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
  fun provideQuranSettings(@ApplicationContext appContext: Context): QuranSettings {
    return QuranSettings.getInstance(appContext)
  }

  @Provides
  @Singleton
  fun provideSettings(settingsImpl: SettingsImpl): Settings {
    return settingsImpl
  }

  @Named(DependencyInjectionConstants.CURRENT_PAGE_TYPE)
  @Provides
  fun provideCurrentPageType(quranSettings: QuranSettings): String {
    val currentKey = quranSettings.pageType
    val result = currentKey ?: QuranFileConstants.FALLBACK_PAGE_TYPE
    if (currentKey == null) {
      quranSettings.pageType = result
    }
    return result
  }

  @Provides
  fun provideQuranFileManager(quranFileUtils: QuranFileUtils): QuranFileManager {
    return quranFileUtils
  }

  @Provides
  fun provideFileSystem(): FileSystem {
    return FileSystem.SYSTEM
  }

  @Provides
  fun provideMainThreadScheduler(): Scheduler {
    return AndroidSchedulers.mainThread()
  }

  @Provides
  fun provideCacheDirectory(@ApplicationContext appContext: Context): File {
    return appContext.cacheDir
  }

  @Provides
  @ElementsIntoSet
  fun provideExtraPreferences(): Set<ExtraPreferencesProvider> {
    return emptySet()
  }

  @Provides
  @ElementsIntoSet
  fun provideExtraScreens(): Set<ExtraScreenProvider> {
    return emptySet()
  }
}
