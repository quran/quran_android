package com.quran.mobile.feature.downloadmanager.fakes

import com.quran.data.core.QuranFileManager
import com.quran.data.model.audio.Qari
import java.io.File

class FakeQuranFileManager : QuranFileManager {
  var audioDirectory: String? = null

  override fun audioFileDirectory(): String? = audioDirectory

  override fun quranImagesDirectory(): File = error("Not implemented")
  override fun ayahInfoFileDirectory(): File = error("Not implemented")
  override fun databaseDirectory(): File = error("Not implemented")
  override fun recitationSessionsDirectory(): String = error("Not implemented")
  override fun recitationRecordingsDirectory(): String = error("Not implemented")
  override fun urlForDatabase(qari: Qari): String = error("Not implemented")
  override fun isVersion(widthParam: String, version: Int): Boolean = error("Not implemented")
  override fun copyFromAssetsRelative(assetsPath: String, filename: String, destination: String) = error("Not implemented")
  override fun copyFromAssetsRelative(assetsPath: String, filename: String, destination: File) = error("Not implemented")
  override fun copyFromAssetsRelativeRecursive(assetsPath: String, directory: String, destination: String) = error("Not implemented")
  override fun removeOldArabicDatabase(): Boolean = error("Not implemented")
  override fun hasArabicSearchDatabase(): Boolean = error("Not implemented")
  override fun writeVersionFile(widthParam: String, version: Int) = error("Not implemented")
  override fun removeFilesForWidth(width: Int, directoryLambda: (String) -> String) = error("Not implemented")
  override fun writeNoMediaFileRelative(widthParam: String) = error("Not implemented")
  override fun upgradeNonAudioFiles(portraitWidth: String, landscapeWidth: String, totalPages: Int): Boolean = error("Not implemented")
}
