package com.quran.labs.androidquran.data

import com.quran.labs.androidquran.dao.translation.Translation
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Translations bundled inside the APK.
 *
 * Translation and tafsir data normally comes from the server (see
 * TranslationManagerPresenter), but some translations are bundled and installed
 * locally on first run. Their ids are set well above the maximum id currently
 * assigned by the server so that they can never collide with a server entry.
 */
object BundledTranslations {
  private const val BUNDLED_TRANSLATION_ID_BASE = 9500

  private const val CEBUANO_LANGUAGE_CODE = "ceb"

  const val CEBUANO_DATABASE_FILE_NAME = "quran.ceb.rwwad.db"
  const val CEBUANO_DATABASE_ASSET = "databases/$CEBUANO_DATABASE_FILE_NAME.zip"

  val cebuano: Translation = Translation(
    id = BUNDLED_TRANSLATION_ID_BASE + 1,
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

  /**
   * Returns the server list with bundled translations added, skipping any entry
   * that is already provided by the server.
   */
  fun mergeServerTranslations(serverTranslations: List<Translation>): List<Translation> {
    val alreadyCovered = serverTranslations.any {
      it.id == cebuano.id ||
          it.fileName == cebuano.fileName ||
          it.languageCode == CEBUANO_LANGUAGE_CODE
    }
    return if (alreadyCovered) serverTranslations else serverTranslations + cebuano
  }

  /**
   * Installs bundled translations into the app's database directory on first
   * run. Skipped once done (tracked in per-installation settings) so that a
   * translation the user removed is not restored behind their back.
   */
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
