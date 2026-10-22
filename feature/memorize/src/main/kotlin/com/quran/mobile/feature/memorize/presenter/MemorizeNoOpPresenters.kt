package com.quran.mobile.feature.memorize.presenter

import android.app.Activity
import com.quran.data.di.ActivityScope
import com.quran.data.di.QuranPageScope
import com.quran.recitation.events.RecitationSelection
import com.quran.recitation.presenter.RecitationPlaybackPresenter
import com.quran.recitation.presenter.RecitationPopupPresenter
import com.quran.recitation.presenter.RecitationPopupPresenter.PopupContainer
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Memorization doesn't record, so there is nothing to play back. */
@ActivityScope
class MemorizePlaybackPresenter @Inject constructor() : RecitationPlaybackPresenter {
  override val recitationPlaybackFlow: StateFlow<RecitationSelection> = MutableStateFlow(RecitationSelection.None)
  override fun bind(what: Activity) {}
  override fun unbind(what: Activity) {}
  override fun play() {}
  override fun pauseIfPlaying() {}
}

@QuranPageScope
class MemorizePopupPresenter @Inject constructor() : RecitationPopupPresenter {
  override fun bind(what: PopupContainer) {}
  override fun unbind(what: PopupContainer) {}
}
