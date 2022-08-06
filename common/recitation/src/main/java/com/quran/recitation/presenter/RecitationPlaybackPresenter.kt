package com.quran.recitation.presenter

import android.app.Activity
import com.quran.recitation.events.RecitationSelection
import kotlinx.coroutines.flow.StateFlow

interface RecitationPlaybackPresenter : Presenter<Activity> {

  val recitationPlaybackFlow: StateFlow<RecitationSelection>

  override fun bind(what: Activity)
  override fun unbind(what: Activity)

  fun play()
  fun pauseIfPlaying()

}
