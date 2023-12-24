package com.quran.labs.androidquran.di.quran

import com.quran.labs.androidquran.presenter.data.QuranIndexEventLogger
import dagger.Module
import dagger.Provides

@Module
class TestQuranActivityModule {

  @Provides
  fun bindQuranIndexEventLogger(): QuranIndexEventLogger {
    return QuranIndexEventLogger { }
  }
}
