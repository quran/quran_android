package com.quran.labs.androidquran.data

import com.quran.labs.androidquran.dao.translation.Translation
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BundledTranslations {
  private const val CEBUANO_ID = 9501

  private const val CEBUANO_LANGUAGE_CODE = "ceb"

  const val CEBUANO_DATABASE_FILE_NAME = "quran.ceb.rwwad.db"
  const val CEBUANO_DATABASE_ASSET = "databases/$CEBUANO_DATABASE_FILE_NAME.zip"

  val cebuano: Translation = Translation(
    id = CEBUANO_ID,
    minimumVersion = 5,
    currentVersion = 1,
    displayName = "Cebuano Translation (Bisayan)",
    downloadType = "translation",
    fileName = CEBUANO_DATABASE_FILE_NAME,
    fileUrl = "https://quranenc.com/downloads/sqlite/bisayan_rwwad.zip",
    saveTo = "databases",
    languageCode = CEBUANO_LANGUAGE_CODE,
    translator = "Rowwad Translation Center"
  )

  fun mergeServerTranslations(serverTranslations: List<Translation>): List<Translation> {
    val alreadyCovered = serverTranslations.any {
      it.id == cebuano.id ||
          it.fileName == cebuano.fileName ||
          it.languageCode == CEBUANO_LANGUAGE_CODE
    }
    return if (alreadyCovered) serverTranslations else serverTranslations + cebuano
  }

  suspend fun installTranslations(quranFileUtils: QuranFileUtils, quranSettings: QuranSettings) {
    if (quranSettings.bundledTranslationsInstalled()) {
      return
    }
    withContext(Dispatchers.IO) {
      val databaseDir = quranFileUtils.getQuranDatabaseDirectory()
      quranFileUtils.copyFromAssetsRelative(
        CEBUANO_DATABASE_ASSET,
        CEBUANO_DATABASE_ASSET.substringAfterLast('/'),
        databaseDir
      )
      if (File(databaseDir, CEBUANO_DATABASE_FILE_NAME).exists()) {
        quranSettings.setBundledTranslationsInstalled(true)
      }
    }
  }
}
