package com.quran.labs.androidquran.feature.audio.util

import com.quran.labs.androidquran.common.audio.model.QariItem
import java.io.File

class AudioFileCheckerImpl(private val calculator: HashCalculator,
                           private val audioPathRoot: String) : AudioFileChecker {
  override fun isQariOnFilesystem(qari: QariItem) = directoryForQari(qari).exists()

  override fun doesFileExistForQari(qari: QariItem, file: String) =
    localFileForQari(qari, file).exists()

  override fun doesHashMatchForQariFile(qari: QariItem, file: String, hash: String): Boolean {
    return calculator.calculateHash(localFileForQari(qari, file)) == hash
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
    val directory = directoryForQari(qari)
    return if (qari.isGapless) {
      File(directory, file)
    } else {
      // gapped files are named like 002255.mp3 on the server, but are
      // stored as 2/255.mp3, without the padding on sura and ayah
      val sura = file.substring(0, 3).toInt().toString()
      val ayah = file.substring(3, 6).toInt().toString()
      val extension = file.substringAfterLast(".")
      File(File(directory, sura), "$ayah.$extension")
    }
  }
}
