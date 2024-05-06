package com.quran.data.core

import androidx.annotation.WorkerThread
import java.io.File

interface QuranFileManager {
  fun quranImagesDirectory(): File
  fun ayahInfoFileDirectory(): File
  fun audioFileDirectory(): String?

  fun recitationSessionsDirectory(): String
  fun recitationRecordingsDirectory(): String

  @WorkerThread
  fun isVersion(widthParam: String, version: Int): Boolean

  @WorkerThread
  fun copyFromAssetsRelative(assetsPath: String, filename: String, destination: String)

  @WorkerThread
  fun copyFromAssetsRelativeRecursive(assetsPath: String, directory: String, destination: String)

  @WorkerThread
  fun removeOldArabicDatabase(): Boolean

  @WorkerThread
  fun hasArabicSearchDatabase(): Boolean

  @WorkerThread
  fun writeVersionFile(widthParam: String, version: Int)

  @WorkerThread
  fun removeFilesForWidth(width: Int, directoryLambda: ((String) -> String) = { it })

  @WorkerThread
  fun writeNoMediaFileRelative(widthParam: String)

  @WorkerThread
  fun upgradeNonAudioFiles(portraitWidth: String, landscapeWidth: String, totalPages: Int): Boolean
}
