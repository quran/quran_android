package com.quran.labs.androidquran.database

import com.quran.labs.androidquran.feature.audio.VersionableDatabaseChecker
import dev.zacsweers.metro.Inject

class AudioDatabaseVersionChecker @Inject constructor() : VersionableDatabaseChecker {
  override fun getVersionForDatabase(path: String): Int {
    return SuraTimingDatabaseHandler.getDatabaseHandler(path).getVersion()
  }
}
