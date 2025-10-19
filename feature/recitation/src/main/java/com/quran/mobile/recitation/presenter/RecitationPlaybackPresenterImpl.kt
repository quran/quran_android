package com.quran.mobile.recitation.presenter

import android.app.Activity
import com.quran.data.di.ActivityScope
import com.quran.recitation.events.RecitationSelection
import com.quran.recitation.presenter.RecitationPlaybackPresenter
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@ActivityScope
class RecitationPlaybackPresenterImpl @Inject constructor(): RecitationPlaybackPresenter {
  override val recitationPlaybackFlow: StateFlow<RecitationSelection> =
    MutableStateFlow(RecitationSelection.None)

  override fun bind(what: Activity) {}
  override fun unbind(what: Activity) {}

  override fun play() {}
  override fun pauseIfPlaying() {}
}
