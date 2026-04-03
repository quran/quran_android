package com.quran.labs.androidquran.fakes

import com.quran.labs.androidquran.util.AudioFileUtils
import java.io.File

class FakeAudioFileUtils : AudioFileUtils {
  var haveAyaPositionFileResult: Boolean = true
  var ayaPositionFileUrlValue: String = ""
  var quranAyahDatabaseDirectoryValue: File = File("/tmp/quran_test")
  var gaplessDatabaseRootUrlValue: String = ""

  override fun haveAyaPositionFile(): Boolean = haveAyaPositionFileResult
  override val ayaPositionFileUrl: String get() = ayaPositionFileUrlValue
  override val quranAyahDatabaseDirectory: File get() = quranAyahDatabaseDirectoryValue
  override val gaplessDatabaseRootUrl: String get() = gaplessDatabaseRootUrlValue
}
