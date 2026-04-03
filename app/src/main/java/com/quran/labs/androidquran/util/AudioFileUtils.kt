package com.quran.labs.androidquran.util

import java.io.File

interface AudioFileUtils {
  fun haveAyaPositionFile(): Boolean
  val ayaPositionFileUrl: String
  val quranAyahDatabaseDirectory: File
  val gaplessDatabaseRootUrl: String
}
