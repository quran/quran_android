package com.quran.labs.androidquran.feature.audio.util

import com.quran.labs.androidquran.common.audio.model.QariItem
import java.io.File

class AudioFileCheckerImpl(private val calculator: HashCalculator,
                           private val audioPathRoot: String) : AudioFileChecker {
  override fun isQariOnFilesystem(qari: QariItem) = directoryForQari(qari).exists()

  override fun doesFileExistForQari(qari: QariItem, file: String) =
    fileUrlForFile(qari, file).exists()

  override fun doesHashMatchForQariFile(qari: QariItem, file: String, hash: String): Boolean {
    return calculator.calculateHash(fileUrlForFile(qari, file)) == hash
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

  private fun fileUrlForFile(qari: QariItem, file: String): File {
    val directory = directoryForQari(qari)
    return if (qari.isGapless) {
      File(directory, file)
    } else {
      // sura name is padded to 3 digits - convert to int
      // and back to String to drop leading zeroes
      val sura = file.substring(0, 3).toInt().toString()
      File(File(directory, sura), file)
    }
  }
}
