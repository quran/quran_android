package com.quran.mobile.translation.di

import android.content.Context
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.translation.data.TranslationsDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
class TranslationDataModule {

  @Singleton
  @Provides
  fun provideTranslationDatabase(@ApplicationContext context: Context): TranslationsDatabase {
    return TranslationsDatabase(
      AndroidSqliteDriver(
        schema = TranslationsDatabase.Schema.synchronous(),
        context = context,
        name = "translations.db"
      )
    )
  }
}
