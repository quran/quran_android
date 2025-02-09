package com.quran.mobile.common.sync.auth

import androidx.datastore.preferences.core.stringPreferencesKey

object AuthConstants {
  val authPreference = stringPreferencesKey(Keys.AUTH_STATE_PREFERENCE_KEY)

  private object Keys {
    const val AUTH_STATE_PREFERENCE_KEY = "authState"
  }
}
