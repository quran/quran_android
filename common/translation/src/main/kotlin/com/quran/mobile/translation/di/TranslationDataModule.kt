package com.quran.mobile.translation.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.translation.data.TranslationsDatabase
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class)
class TranslationDataModule {

  @SingleIn(AppScope::class)
  @Provides
  fun provideTranslationDatabase(@ApplicationContext context: Context): TranslationsDatabase {
    return TranslationsDatabase(
      AndroidSqliteDriver(
        schema = TranslationsDatabase.Schema,
        context = context,
        name = "translations.db"
      )
    )
  }
}
