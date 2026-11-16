package com.quran.mobile.di

import android.content.Context

/** Opens a recorded Memorize session (Progress's Sessions plays it back there). */
interface RecordedSessionLauncher {
  fun open(context: Context, recordingId: String)
}
