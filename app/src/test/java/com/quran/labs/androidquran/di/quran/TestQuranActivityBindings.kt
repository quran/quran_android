package com.quran.labs.androidquran.di.quran

import com.quran.data.di.QuranActivityLevelScope
import com.quran.labs.androidquran.di.module.activity.QuranActivityBindingContainer
import com.quran.labs.androidquran.presenter.data.QuranIndexEventLogger
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(QuranActivityLevelScope::class, replaces = [QuranActivityBindingContainer::class])
@BindingContainer
object TestQuranActivityBindings {

  @Provides
  fun bindQuranIndexEventLogger(): QuranIndexEventLogger = QuranIndexEventLogger { }
}
