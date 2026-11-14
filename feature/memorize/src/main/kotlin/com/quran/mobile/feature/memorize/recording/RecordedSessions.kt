package com.quran.mobile.feature.memorize.recording

import android.content.Context
import android.content.Intent
import com.quran.data.di.AppScope
import com.quran.mobile.di.RecordedSessionLauncher
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Opens a recorded session in Recording sessions (from Progress's Sessions). */
@ContributesIntoSet(AppScope::class)
@Inject
class RecordedSessions : RecordedSessionLauncher {
  override fun open(context: Context, recordingId: String) {
    context.startActivity(
      Intent(context, RecordingsActivity::class.java).putExtra(RecordingsActivity.EXTRA_SESSION, recordingId)
    )
  }
}
