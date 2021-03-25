package com.quran.labs.androidquran.di.module.application;

import android.content.Context;

import com.quran.data.dao.BookmarksDao;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import com.quran.labs.androidquran.database.BookmarksDaoImpl;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class DatabaseModule {

  @Provides
  @Singleton
  static BookmarksDBAdapter provideBookmarkDatabaseAdapter(Context context) {
    return new BookmarksDBAdapter(context);
  }

  @Provides
  @Singleton
  static BookmarksDao provideBookamrksDao(BookmarksDaoImpl daoImpl) {
    return daoImpl;
  }
}
