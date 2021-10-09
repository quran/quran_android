package com.quran.labs.androidquran.di.module.application

import com.quran.data.dao.BookmarksDao
import com.quran.labs.androidquran.database.BookmarksDaoImpl
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object DatabaseModule {

  @Provides
  @Singleton
  fun provideBookamrksDao(daoImpl: BookmarksDaoImpl): BookmarksDao {
    return daoImpl
  }
}
