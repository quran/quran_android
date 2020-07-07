package com.quran.labs.androidquran.di.module.widgets

import com.quran.labs.androidquran.BookmarksWidgetUpdater
import com.quran.labs.androidquran.BookmarksWidgetUpdaterImpl
import dagger.Binds
import dagger.Module

@Module
interface BookmarksWidgetUpdaterModule {

  @Binds
  fun bindBookmarksWidgetUpdater(impl: BookmarksWidgetUpdaterImpl): BookmarksWidgetUpdater
}
