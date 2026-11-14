package com.quran.mobile.progress.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.progress.db.ProgressDatabase
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class)
class ProgressDataModule {

  @SingleIn(AppScope::class)
  @Provides
  fun provideProgressDatabase(@ApplicationContext context: Context): ProgressDatabase {
    val driver = AndroidSqliteDriver(schema = ProgressDatabase.Schema, context = context, name = "progress.db")
    return ProgressDatabase(driver)
  }
}
