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
import com.quran.mobile.feature.memorize.check.DoubleCheckModel
import com.quran.mobile.feature.memorize.check.DoubleCheckSource
import com.quran.mobile.feature.memorize.check.PieceRecognizerFactory
import com.quran.mobile.feature.memorize.check.RemotePieceRecognizer
import com.quran.mobile.feature.memorize.data.LineSource
import com.quran.mobile.feature.memorize.data.PageLines
import com.quran.mobile.feature.memorize.engine.sherpa.SherpaRecitationEngine
import com.quran.mobile.feature.memorize.presenter.MemorizeHighlightsPresenter
import com.quran.mobile.feature.memorize.presenter.MemorizePlaybackPresenter
import com.quran.mobile.feature.memorize.presenter.MemorizePopupPresenter
import com.quran.mobile.feature.memorize.presenter.MemorizeRecitationPresenter
import com.quran.mobile.feature.memorize.presenter.MemorizeRecitationSettings
import com.quran.mobile.feature.memorize.prompt.HintVoice
import com.quran.mobile.feature.memorize.prompt.HintVoiceDownloads
import com.quran.mobile.feature.memorize.prompt.PromptPlayer
import com.quran.mobile.feature.memorize.prompt.UpcomingSuras
import com.quran.mobile.feature.memorize.recording.Recorders
import com.quran.mobile.feature.memorize.recording.RecordingStore
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
  @Binds val PageLines.bind: LineSource
  @Binds val HintVoiceDownloads.bind: UpcomingSuras
  @Binds val RecordingStore.bind: Recorders
  @Binds val DoubleCheckModel.bind: DoubleCheckSource
}

@BindingContainer
@ContributesTo(AppScope::class)
class MemorizeEngineModule {
  @Provides
  fun provideEngineFactory(): RecitationEngineFactory = RecitationEngineFactory { models ->
    SherpaRecitationEngine(models.modelFile, models.tokensFile)
  }

  /** The double-check's model runs in a process of its own (see DoubleCheckService). */
  @Provides
  fun providePieceRecognizerFactory(@ApplicationContext context: Context): PieceRecognizerFactory =
    PieceRecognizerFactory { model, tokens -> RemotePieceRecognizer(context, model, tokens) }

  @Provides
  fun provideAudioSources(): AudioSourceFactory = AudioSourceFactory { onFrame, onError ->
    MicAudioSource(onFrame, onError)
  }

  @Provides
  fun provideCallState(@ApplicationContext context: Context): CallState = AudioModeCallState(context)
}
