package com.quran.labs.androidquran.di.module.application

import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.TranslationsDao
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.database.TranslationsDaoImpl
import com.quran.mobile.bookmark.model.BookmarksDaoImpl
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
object DatabaseModule {

  @Provides
  @SingleIn(AppScope::class)
  fun provideBookamrksDao(daoImpl: BookmarksDaoImpl): BookmarksDao {
    return daoImpl
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideTranslationsDao(daoImpl: TranslationsDaoImpl): TranslationsDao {
    return daoImpl
  }
}
