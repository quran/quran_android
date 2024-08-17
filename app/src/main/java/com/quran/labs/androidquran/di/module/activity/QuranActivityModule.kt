package com.quran.labs.androidquran.di.module.activity

import com.quran.data.di.QuranActivityScope
import com.quran.labs.androidquran.presenter.data.QuranIndexEventLogger
import com.quran.labs.androidquran.presenter.data.QuranIndexEventLoggerImpl
import dagger.Binds
import dagger.Module

@Module
interface QuranActivityModule {

  @QuranActivityScope
  @Binds
  fun bindQuranIndexEventLogger(impl: QuranIndexEventLoggerImpl): QuranIndexEventLogger
}
