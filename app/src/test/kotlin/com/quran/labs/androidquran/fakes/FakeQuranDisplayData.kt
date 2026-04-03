package com.quran.labs.androidquran.fakes

import android.content.Context
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.data.QuranDisplayInterface

class FakeQuranDisplayData : QuranDisplayInterface {
  var notificationTitleResult: String = "Test Notification"

  override fun getNotificationTitle(
    context: Context,
    minVerse: SuraAyah,
    maxVerse: SuraAyah,
    isGapless: Boolean
  ): String = notificationTitleResult
}
