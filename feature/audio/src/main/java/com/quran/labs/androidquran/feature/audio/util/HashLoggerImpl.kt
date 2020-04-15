package com.quran.labs.androidquran.feature.audio.util

import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.CustomEvent

object HashLoggerImpl : HashLogger {
  override fun logEvent(numberOfFiles: Int) {
    Answers.getInstance()
        .logCustom(
            CustomEvent("audioHashesHelpedPreventUpdates")
                .putCustomAttribute("matchedHashes", numberOfFiles)
        )
  }
}
