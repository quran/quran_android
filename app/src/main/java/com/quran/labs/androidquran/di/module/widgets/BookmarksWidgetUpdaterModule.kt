package com.quran.labs.androidquran.di.module.widgets

import com.quran.labs.androidquran.widget.BookmarksWidgetUpdater
import com.quran.labs.androidquran.widget.BookmarksWidgetUpdaterImpl
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds

@BindingContainer
interface BookmarksWidgetUpdaterModule {

  @Binds val BookmarksWidgetUpdaterImpl.bind: BookmarksWidgetUpdater
}
