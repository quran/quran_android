package com.quran.labs.androidquran.di.module.activity

import com.quran.labs.androidquran.presenter.data.QuranIndexEventLogger
import com.quran.labs.androidquran.presenter.data.QuranIndexEventLoggerImpl
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds

@BindingContainer
interface QuranActivityBindingContainer {

  @Binds
  val QuranIndexEventLoggerImpl.bind: QuranIndexEventLogger
}
