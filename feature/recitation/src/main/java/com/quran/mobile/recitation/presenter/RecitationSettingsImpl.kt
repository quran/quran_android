package com.quran.mobile.recitation.presenter

import com.quran.data.di.AppScope
import com.quran.recitation.presenter.RecitationSettings
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@ContributesBinding(scope = AppScope::class, boundType = RecitationSettings::class)
class RecitationSettingsImpl @Inject constructor() : RecitationSettings {
  override fun isRecitationEnabled() = false
  override fun toggleAyahVisibility() {}
}
