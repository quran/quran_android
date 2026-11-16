package com.quran.mobile.progress.reading

import com.quran.data.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Whether the reader is open right now (the daily reminder stays quiet then). */
@SingleIn(AppScope::class)
class ReaderPresence @Inject constructor() {
  @Volatile var open: Boolean = false
}
