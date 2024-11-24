package com.quran.labs.androidquran.pages.common.madani.upgrade

import android.content.Context
import com.quran.common.upgrade.PreferencesUpgrade
import com.quran.data.dao.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class MadaniPreferencesUpgrade @Inject constructor(private val settings: Settings): PreferencesUpgrade {

  private val scope = MainScope()

  override fun upgrade(context: Context, from: Int, to: Int): Boolean {
    scope.launch(Dispatchers.Main.immediate) {
      if (from <= 3441) {
        settings.setAyahTextSize(settings.translationTextSize())
        settings.setPreferDnsOverHttps(true)
      }
    }
    return true
  }
}
