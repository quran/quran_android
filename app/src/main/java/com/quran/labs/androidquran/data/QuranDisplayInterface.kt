package com.quran.labs.androidquran.data

import android.content.Context
import com.quran.data.model.SuraAyah

interface QuranDisplayInterface {
  fun getNotificationTitle(
    context: Context,
    minVerse: SuraAyah,
    maxVerse: SuraAyah,
    isGapless: Boolean
  ): String
}
