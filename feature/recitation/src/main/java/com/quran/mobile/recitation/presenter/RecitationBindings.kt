package com.quran.mobile.recitation.presenter

import com.quran.data.di.AppScope
import com.quran.data.di.QuranReadingPageScope
import com.quran.data.di.QuranReadingScope
import com.quran.recitation.presenter.*
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo

@BindingContainer
@ContributesTo(QuranReadingScope::class)
interface RecitationBindings {
  @Binds val RecitationPresenterImpl.bind: RecitationPresenter
  @Binds val RecitationPlaybackPresenterImpl.bind: RecitationPlaybackPresenter
}

@BindingContainer
@ContributesTo(QuranReadingPageScope::class)
interface RecitationPageBindings {
  @Binds val RecitationHighlightsPresenterImpl.bind: RecitationHighlightsPresenter
  @Binds val RecitationPopupPresenterImpl.bind: RecitationPopupPresenter
}

@BindingContainer
@ContributesTo(AppScope::class)
interface RecitationAppBindings {
  @Binds val RecitationSettingsImpl.bind: RecitationSettings
}
