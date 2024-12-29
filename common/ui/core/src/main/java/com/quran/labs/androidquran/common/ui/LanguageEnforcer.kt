package com.quran.labs.androidquran.common.ui

import android.content.Context

interface LanguageEnforcer {
  fun refreshLocale(context: Context, force: Boolean)
}
