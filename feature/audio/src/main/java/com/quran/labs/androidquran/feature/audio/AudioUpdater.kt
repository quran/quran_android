package com.quran.labs.androidquran.feature.audio

import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.feature.audio.api.AudioSetUpdate
import com.quran.labs.androidquran.feature.audio.dao.LocalUpdate
import com.quran.labs.androidquran.feature.audio.util.AudioFileChecker

object AudioUpdater {
  fun computeUpdates(updates: List<AudioSetUpdate>,
                     qaris: List<QariItem>,
                     audioFileChecker: AudioFileChecker,
                     databaseChecker: VersionableDatabaseChecker,
  ): List<LocalUpdate> {
    return updates.map { it to findCorrespondingQari(it, qaris) }
        .filter { (_, qari) -> qari != null && audioFileChecker.isQariOnFilesystem(qari) }
        .map { (audioSetUpdate, qariItem) ->
          makeLocalUpdate(audioFileChecker, audioSetUpdate, databaseChecker, qariItem!!)
        }
        .filter { it.files.isNotEmpty() || it.needsDatabaseUpgrade }
  }

  private fun findCorrespondingQari(
    audioSetUpdate: AudioSetUpdate,
    qaris: List<QariItem>
  ): QariItem? {
    return qaris.firstOrNull { it.url == audioSetUpdate.path }
  }

  private fun makeLocalUpdate(audioFileChecker: AudioFileChecker,
                              audioSetUpdate: AudioSetUpdate,
                              databaseChecker: VersionableDatabaseChecker,
                              qari: QariItem
  ): LocalUpdate {
    val existingFiles =
      audioSetUpdate.files.filter { audioFileChecker.doesFileExistForQari(qari, it.filename) }
    val filesToUpdate = existingFiles
          .filter { !audioFileChecker.doesHashMatchForQariFile(qari, it.filename, it.md5sum) }
          .map { it.filename }

    val needsDatabaseUpgrade = if (audioSetUpdate.databaseVersion == null) {
      false
    } else {
      val databasePath = audioFileChecker.getDatabasePathForQari(qari)
      // gapless qaris should always have a database path
      databasePath != null && audioFileChecker.doesDatabaseExist(databasePath) &&
          (databaseChecker.getVersionForDatabase(databasePath) != audioSetUpdate.databaseVersion)
    }
    return LocalUpdate(qari, filesToUpdate, needsDatabaseUpgrade)
  }
}
