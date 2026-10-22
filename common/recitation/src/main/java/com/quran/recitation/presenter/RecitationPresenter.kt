package com.quran.recitation.presenter

import android.app.Activity
import com.quran.data.model.SuraAyah
import kotlinx.coroutines.flow.StateFlow

interface RecitationPresenter : Presenter<Activity> {

  override fun bind(what: Activity)
  override fun unbind(what: Activity)

  fun isRecitationEnabled(): Boolean
  fun isRecitationEnabledFlow(): StateFlow<Boolean>
  fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray)

  fun startOrStopRecitation(startAyah: () -> SuraAyah, showModeSelection: Boolean = false)
  fun endSession()

  /** Whether sessions produce a transcript the user can open. */
  fun supportsTranscript(): Boolean = true

  /** Whether sessions are recorded and can be played back. */
  fun supportsPlayback(): Boolean = true

  /** Whether the user can ask for a hint (the next word) during a session. */
  fun supportsHints(): Boolean = false

  /** The user asked for the next word. */
  fun onHintRequested() {}

  /**
   * Called by the reading screen: the pages on screen (one, or two side by side) and how to start a
   * session there, for sessions started from the presenter's own controls.
   */
  fun setReadingScreen(pagesOnScreen: () -> List<Int>, start: (showModeSelection: Boolean) -> Unit) {}

}
