package com.quran.labs.androidquran.di.module.activity

import com.quran.data.di.QuranReadingScope
import com.quran.data.di.QuranScope
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds

@ContributesTo(QuranReadingScope::class)
@Module
interface QuranReadingCircuitModule {
  @Multibinds fun presenterFactories(): Set<Presenter.Factory>
  @Multibinds fun viewFactories(): Set<Ui.Factory>

  companion object {
    @QuranScope
    @Provides
    fun provideCircuit(
      presenterFactories: @JvmSuppressWildcards Set<Presenter.Factory>,
      uiFactories: @JvmSuppressWildcards Set<Ui.Factory>,
    ): Circuit {
      return Circuit.Builder()
        .addPresenterFactories(presenterFactories)
        .addUiFactories(uiFactories)
        .build()
    }
  }
}
