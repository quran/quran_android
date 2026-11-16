package com.quran.mobile.feature.memorize.session

import com.quran.mobile.progress.session.SessionRecord

/** Where a finished or stopped session's results go (Progress keeps them, if the user lets it). */
fun interface SessionResults {
  fun record(record: SessionRecord)
}
