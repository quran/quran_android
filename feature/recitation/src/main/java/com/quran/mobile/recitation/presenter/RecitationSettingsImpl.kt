package com.quran.mobile.recitation.presenter

import com.quran.recitation.presenter.RecitationSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecitationSettingsImpl @Inject constructor() : RecitationSettings {
  override fun isRecitationEnabled() = false
  override fun toggleAyahVisibility() {}
}
