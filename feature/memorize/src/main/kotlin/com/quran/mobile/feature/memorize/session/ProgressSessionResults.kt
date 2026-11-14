package com.quran.mobile.feature.memorize.session

import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.mobile.progress.session.SessionLog
import com.quran.mobile.progress.session.SessionRecord
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

/** Keeps session results in Progress's [SessionLog]. */
@ContributesBinding(AppScope::class)
class ProgressSessionResults @Inject constructor(
  private val sessionLog: SessionLog,
  private val scope: AppCoroutineScope,
) : SessionResults {
  override fun record(record: SessionRecord) {
    scope.launch { sessionLog.record(record) }
  }
}
