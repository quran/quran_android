package com.quran.mobile.bookmark.sync

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

interface LocalDataChangeNotifier {
  fun localDataUpdated()
}

internal fun LocalDataChangeNotifier.notifyLocalDataChanged() {
  runCatching { localDataUpdated() }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class NoOpLocalDataChangeNotifier @Inject constructor() : LocalDataChangeNotifier {
  override fun localDataUpdated() = Unit
}
