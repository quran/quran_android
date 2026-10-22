package com.quran.mobile.feature.memorize.di

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.data.di.QuranReadingPageScope
import com.quran.data.di.QuranReadingScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.memorize.audio.AudioModeCallState
import com.quran.mobile.feature.memorize.audio.AudioSourceFactory
import com.quran.mobile.feature.memorize.audio.CallState
import com.quran.mobile.feature.memorize.audio.MicAudioSource
import com.quran.mobile.feature.memorize.engine.sherpa.SherpaRecitationEngine
import com.quran.mobile.feature.memorize.presenter.MemorizeHighlightsPresenter
import com.quran.mobile.feature.memorize.presenter.MemorizePlaybackPresenter
import com.quran.mobile.feature.memorize.presenter.MemorizePopupPresenter
import com.quran.mobile.feature.memorize.presenter.MemorizeRecitationPresenter
import com.quran.mobile.feature.memorize.presenter.MemorizeRecitationSettings
import com.quran.mobile.feature.memorize.prompt.HintVoice
import com.quran.mobile.feature.memorize.prompt.PromptPlayer
import com.quran.mobile.feature.memorize.session.RecitationEngineFactory
import com.quran.recitation.presenter.RecitationHighlightsPresenter
import com.quran.recitation.presenter.RecitationPlaybackPresenter
import com.quran.recitation.presenter.RecitationPopupPresenter
import com.quran.recitation.presenter.RecitationPresenter
import com.quran.recitation.presenter.RecitationSettings
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** Plugs memorization into the app's recitation slot (instead of :feature:recitation's stubs). */
@BindingContainer
@ContributesTo(QuranReadingScope::class)
interface MemorizeReadingBindings {
  @Binds val MemorizeRecitationPresenter.bind: RecitationPresenter
  @Binds val MemorizePlaybackPresenter.bind: RecitationPlaybackPresenter
}

@BindingContainer
@ContributesTo(QuranReadingPageScope::class)
interface MemorizePageBindings {
  @Binds val MemorizeHighlightsPresenter.bind: RecitationHighlightsPresenter
  @Binds val MemorizePopupPresenter.bind: RecitationPopupPresenter
}

@BindingContainer
@ContributesTo(AppScope::class)
interface MemorizeAppBindings {
  @Binds val MemorizeRecitationSettings.bind: RecitationSettings
  @Binds val PromptPlayer.bind: HintVoice
}

@BindingContainer
@ContributesTo(AppScope::class)
class MemorizeEngineModule {
  @Provides
  fun provideEngineFactory(): RecitationEngineFactory = RecitationEngineFactory { models ->
    SherpaRecitationEngine(models.modelFile, models.tokensFile)
  }

  @Provides
  fun provideAudioSources(): AudioSourceFactory = AudioSourceFactory { onFrame, onError ->
    MicAudioSource(onFrame, onError)
  }

  @Provides
  fun provideCallState(@ApplicationContext context: Context): CallState = AudioModeCallState(context)
}
