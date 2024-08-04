package com.quran.labs.androidquran.pages.common.madani.upgrade

import androidx.annotation.WorkerThread
import com.quran.common.upgrade.LocalDataUpgrade
import com.quran.data.core.QuranFileManager
import com.quran.data.model.QuranDataStatus
import javax.inject.Inject

class MadaniDataUpgrade @Inject constructor(
  private val fileManager: QuranFileManager
) : LocalDataUpgrade {

  @WorkerThread
  override fun processData(quranDataStatus: QuranDataStatus): QuranDataStatus {
    val updatedDataStatus = if (!quranDataStatus.havePages()) {
      val haveImages = fileManager.upgradeNonAudioFiles(
        quranDataStatus.portraitWidth,
        quranDataStatus.landscapeWidth,
        quranDataStatus.totalPages
      )
      quranDataStatus.copy(
        havePortrait = haveImages || quranDataStatus.havePortrait,
        haveLandscape = haveImages || quranDataStatus.haveLandscape
      )
    } else {
      quranDataStatus
    }

    return updatedDataStatus
  }
}
