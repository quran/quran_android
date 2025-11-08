package com.quran.mobile.linebyline.data.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.core.QuranFileManager
import com.quran.data.di.QuranReadingScope
import com.quran.data.di.QuranScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.linebyline.data.LineByLineAyahInfoDatabase
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@BindingContainer
@ContributesTo(QuranReadingScope::class)
class LineByLineDataModule {

  @QuranScope
  @Provides
  fun provideDatabase(@ApplicationContext appContext: Context, quranFileManager: QuranFileManager): LineByLineAyahInfoDatabase {
    val filePath = quranFileManager.ayahInfoFileDirectory().absolutePath
    val driver = AndroidSqliteDriver(LineByLineAyahInfoDatabase.Schema, appContext, name = filePath)
    return LineByLineAyahInfoDatabase(driver)
  }
}
