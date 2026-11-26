package com.quran.labs.androidquran.feature.audio.util

import com.quran.labs.androidquran.common.audio.model.QariItem
import java.io.File

class AudioFileCheckerImpl(private val calculator: HashCalculator,
                           private val audioPathRoot: String) : AudioFileChecker {
  override fun isQariOnFilesystem(qari: QariItem) = directoryForQari(qari).exists()

  override fun doesFileExistForQari(qari: QariItem, file: String) =
    safeLocalFileForQari(qari, file)?.exists() == true

  override fun doesHashMatchForQariFile(qari: QariItem, file: String, hash: String): Boolean {
    val localFile = safeLocalFileForQari(qari, file) ?: return false
    return calculator.calculateHash(localFile) == hash
  }

  override fun getDatabasePathForQari(qari: QariItem): String? {
    return if (qari.isGapless) {
      audioPathRoot + qari.path + File.separator + qari.databaseName + ".db"
    } else {
      null
    }
  }

  override fun doesDatabaseExist(path: String) = File(path).exists()

  private fun directoryForQari(qari: QariItem) = File(audioPathRoot, qari.path)

  fun localFileForQari(qari: QariItem, file: String): File {
    return safeLocalFileForQari(qari, file)
      ?: throw IllegalArgumentException("Invalid audio filename: $file")
  }

  private fun safeLocalFileForQari(qari: QariItem, file: String): File? {
    if (file.isBlank() ||
      file == "." ||
      file == ".." ||
      file.contains('/') ||
      file.contains('\\') ||
      file.contains("..")
    ) {
      return null
    }

    val directory = directoryForQari(qari)
    val resolved = if (qari.isGapless) {
      File(directory, file)
    } else {
      if (!GAPPED_FILENAME_REGEX.matches(file)) {
        return null
      }
      // gapped files are named like 002255.mp3 on the server, but are
      // stored as 2/255.mp3, without the padding on sura and ayah
      val sura = file.substring(0, 3).toInt().toString()
      val ayah = file.substring(3, 6).toInt().toString()
      val extension = file.substringAfterLast(".")
      File(File(directory, sura), "$ayah.$extension")
    }

    return if (isWithinDirectory(directory, resolved)) resolved else null
  }

  private fun isWithinDirectory(directory: File, target: File): Boolean {
    val directoryCanonical = directory.canonicalPath
    val directoryPrefix = if (directoryCanonical.endsWith(File.separator)) {
      directoryCanonical
    } else {
      directoryCanonical + File.separator
    }
    return target.canonicalPath.startsWith(directoryPrefix)
  }

  private companion object {
    val GAPPED_FILENAME_REGEX = Regex("^\\d{6}\\.[A-Za-z0-9]+$")
  }
}
