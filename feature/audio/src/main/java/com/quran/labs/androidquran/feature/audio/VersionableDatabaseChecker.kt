package com.quran.labs.androidquran.feature.audio

interface VersionableDatabaseChecker {
  fun getVersionForDatabase(path: String): Int
}
