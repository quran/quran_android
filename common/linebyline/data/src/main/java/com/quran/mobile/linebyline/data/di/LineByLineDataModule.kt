package com.quran.mobile.linebyline.data.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.core.QuranFileManager
import com.quran.data.di.QuranReadingScope
import com.quran.data.di.QuranScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.linebyline.data.Database
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides

@Module
@ContributesTo(QuranReadingScope::class)
class LineByLineDataModule {

  @QuranScope
  @Provides
  fun provideDatabase(@ApplicationContext appContext: Context, quranFileManager: QuranFileManager): Database {
    val filePath = quranFileManager.ayahInfoFileDirectory().absolutePath
    val driver = AndroidSqliteDriver(Database.Schema, appContext, name = filePath)
    return Database(driver)
  }
}
