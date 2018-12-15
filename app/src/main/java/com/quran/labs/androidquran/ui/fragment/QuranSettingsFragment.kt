package com.quran.labs.androidquran.ui.fragment

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.preference.PreferenceScreen
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.QuranAdvancedPreferenceActivity
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.QuranPreferenceActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.pageselect.PageSelectActivity
import com.quran.labs.androidquran.ui.AudioManagerActivity
import com.quran.labs.androidquran.ui.TranslationManagerActivity
import javax.inject.Inject

class QuranSettingsFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
  @Inject lateinit var pageTypes :
      Map<@JvmSuppressWildcards String, @JvmSuppressWildcards PageProvider>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.quran_preferences)

    val context = activity
    val appContext = context.applicationContext

    // field injection
    (appContext as QuranApplication).applicationComponent.inject(this)

    // handle translation manager click
    val translationPref = findPreference(Constants.PREF_TRANSLATION_MANAGER)
    translationPref.setOnPreferenceClickListener {
      startActivity(Intent(activity, TranslationManagerActivity::class.java))
      true
    }

    // handle audio manager click
    val audioManagerPref = findPreference(Constants.PREF_AUDIO_MANAGER)
    audioManagerPref.setOnPreferenceClickListener {
      startActivity(Intent(activity, AudioManagerActivity::class.java))
      true
    }

    val pageChangePref = findPreference(Constants.PREF_PAGE_TYPE)
    if (pageTypes.size < 2) {
      val readingPrefs = findPreference(Constants.PREF_READING_CATEGORY)
      (readingPrefs as PreferenceGroup).removePreference(pageChangePref)
    }
  }

  override fun onResume() {
    super.onResume()
    preferenceScreen.sharedPreferences
        .registerOnSharedPreferenceChangeListener(this)
  }

  override fun onPause() {
    preferenceScreen.sharedPreferences
        .unregisterOnSharedPreferenceChangeListener(this)
    super.onPause()
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences,
                                         key: String) {
    if (key == Constants.PREF_USE_ARABIC_NAMES) {
      val context = activity
      if (context is QuranPreferenceActivity) {
        context.restartActivity()
      }
    }
  }

  override fun onPreferenceTreeClick(preferenceScreen: PreferenceScreen, preference: Preference): Boolean {
    val key = preference.key
    if ("key_prefs_advanced" == key) {
      val intent = Intent(activity, QuranAdvancedPreferenceActivity::class.java)
      startActivity(intent)
      return true
    } else if (Constants.PREF_PAGE_TYPE == key) {
      val intent = Intent(activity, PageSelectActivity::class.java)
      startActivity(intent)
      return true
    }

    return super.onPreferenceTreeClick(preferenceScreen, preference)
  }
}
