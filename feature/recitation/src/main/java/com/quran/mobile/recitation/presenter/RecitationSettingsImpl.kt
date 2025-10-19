package com.quran.mobile.recitation.presenter

import com.quran.data.di.AppScope
import com.quran.recitation.presenter.RecitationSettings
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class RecitationSettingsImpl @Inject constructor() : RecitationSettings {
  override fun isRecitationEnabled() = false
  override fun toggleAyahVisibility() {}
}
