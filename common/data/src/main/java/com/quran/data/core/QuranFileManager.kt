package com.quran.data.core

import androidx.annotation.WorkerThread

interface QuranFileManager {
  @WorkerThread
  fun isVersion(widthParam: String, version: Int): Boolean

  @WorkerThread
  fun copyFromAssetsRelative(assetsPath: String, filename: String, destination: String)

  @WorkerThread
  fun hasArabicSearchDatabase(): Boolean

  @WorkerThread
  fun writeVersionFile(widthParam: String, version: Int)

  @WorkerThread
  fun removeFilesForWidth(width: Int, directoryLambda: ((String) -> String) = { it })

  @WorkerThread
  fun writeNoMediaFileRelative(widthParam: String)
}
