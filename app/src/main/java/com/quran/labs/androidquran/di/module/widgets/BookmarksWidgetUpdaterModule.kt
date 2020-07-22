package com.quran.labs.androidquran.di.module.widgets

import com.quran.labs.androidquran.widget.BookmarksWidgetUpdater
import com.quran.labs.androidquran.widget.BookmarksWidgetUpdaterImpl
import dagger.Binds
import dagger.Module

@Module
interface BookmarksWidgetUpdaterModule {

  @Binds
  fun bindBookmarksWidgetUpdater(impl: BookmarksWidgetUpdaterImpl): BookmarksWidgetUpdater
}
