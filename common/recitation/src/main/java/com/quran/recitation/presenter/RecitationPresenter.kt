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

}
