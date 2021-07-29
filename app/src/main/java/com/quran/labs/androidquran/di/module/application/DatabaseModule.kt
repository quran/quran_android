package com.quran.labs.androidquran.di.module.application

import android.content.Context
import com.quran.data.dao.BookmarksDao
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.labs.androidquran.database.BookmarksDaoImpl
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object DatabaseModule {

  @Provides
  @Singleton
  fun provideBookmarkDatabaseAdapter(context: Context): BookmarksDBAdapter {
    return BookmarksDBAdapter(context)
  }

  @Provides
  @Singleton
  fun provideBookamrksDao(daoImpl: BookmarksDaoImpl): BookmarksDao {
    return daoImpl
  }
}
