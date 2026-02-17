package com.quran.labs.androidquran.fakes

import android.content.Context
import android.graphics.Bitmap
import com.quran.data.core.QuranFileManager
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.Response
import okhttp3.OkHttpClient
import java.io.File

/**
 * Fake implementation of QuranFileUtils for testing.
 *
 * Provides configurable responses for file existence checks and other utility methods.
 * Used by AudioUtilsTest, TranslationManagerPresenterTest, AudioPresenterTest.
 */
class FakeQuranFileUtils : QuranFileManager {

  var hasAyaPositionFileResult: Boolean = false
  var hasTranslationResult: Boolean = false
  private val translationFiles = mutableSetOf<String>()

  fun setHasAyaPositionFile(value: Boolean) {
    hasAyaPositionFileResult = value
  }

  fun setHasTranslation(filename: String, value: Boolean) {
    if (value) {
      translationFiles.add(filename)
    } else {
      translationFiles.remove(filename)
    }
  }

  fun haveAyaPositionFile(): Boolean {
    return hasAyaPositionFileResult
  }

  fun hasTranslation(fileName: String): Boolean {
    return translationFiles.contains(fileName)
  }

  // QuranFileManager interface implementations (minimal/no-op)

  override fun isVersion(widthParam: String, version: Int): Boolean = false

  override fun quranImagesDirectory(): File = File("")

  override fun ayahInfoFileDirectory(): File = File("")

  override fun removeFilesForWidth(width: Int, directoryLambda: (String) -> String) {}

  override fun writeVersionFile(widthParam: String, version: Int) {}

  override fun writeNoMediaFileRelative(widthParam: String) {}

  override fun copyFromAssetsRelative(assetsPath: String, filename: String, destination: String) {}

  override fun copyFromAssetsRelative(assetsPath: String, filename: String, destination: File) {}

  override fun copyFromAssetsRelativeRecursive(
    assetsPath: String,
    directory: String,
    destination: String
  ) {}

  override fun removeOldArabicDatabase(): Boolean = false

  override fun audioFileDirectory(): String? = null

  override fun databaseDirectory(): File = File("")

  override fun urlForDatabase(qari: Qari): String = ""

  override fun recitationSessionsDirectory(): String = ""

  override fun recitationRecordingsDirectory(): String = ""

  override fun hasArabicSearchDatabase(): Boolean = false

  override fun upgradeNonAudioFiles(
    portraitWidth: String,
    landscapeWidth: String,
    totalPages: Int
  ): Boolean = false
}
