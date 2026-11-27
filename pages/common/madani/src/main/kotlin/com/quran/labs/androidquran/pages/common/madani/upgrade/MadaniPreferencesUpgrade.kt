package com.quran.labs.androidquran.pages.common.madani.upgrade

import android.content.Context
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.quran.common.upgrade.PreferencesUpgrade
import com.quran.data.dao.Settings
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MadaniPreferencesUpgrade @Inject constructor(private val settings: Settings): PreferencesUpgrade {

  override fun upgrade(context: Context, from: Int, to: Int): Boolean {
    if (from > 3460 && from > 3441) {
      return true
    }

    val appContext = context.applicationContext
    CoroutineScope(Dispatchers.Main.immediate).launch {
      if (from <= 3460) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        if (prefs.getBoolean("useArabicNames", false)) {
          val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags("ar-EG")
          AppCompatDelegate.setApplicationLocales(appLocale)
        }
      }

      if (from <= 3441) {
        settings.setAyahTextSize(settings.translationTextSize())
      }
    }
    return true
  }
}
