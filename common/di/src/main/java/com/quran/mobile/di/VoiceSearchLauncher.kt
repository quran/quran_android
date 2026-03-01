package com.quran.mobile.di

import android.content.Context
import android.content.Intent

interface VoiceSearchLauncher {
  val isEnabled: Boolean
  fun createLaunchIntent(context: Context): Intent
}
