package com.quran.mobile.recitation.presenter

import android.app.Activity
import com.quran.data.di.ActivityScope
import com.quran.data.di.QuranReadingScope
import com.quran.data.model.SuraAyah
import com.quran.recitation.presenter.RecitationPresenter
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@ActivityScope
@ContributesBinding(scope = QuranReadingScope::class, boundType = RecitationPresenter::class)
class RecitationPresenterImpl @Inject constructor(): RecitationPresenter {
  override fun bind(what: Activity) {}
  override fun unbind(what: Activity) {}

  override fun isRecitationEnabled(): Boolean = false
  override fun isRecitationEnabledFlow(): StateFlow<Boolean> = MutableStateFlow(false)
  override fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {}

  override fun startOrStopRecitation(startAyah: () -> SuraAyah, showModeSelection: Boolean) {}
  override fun endSession() {}
}
