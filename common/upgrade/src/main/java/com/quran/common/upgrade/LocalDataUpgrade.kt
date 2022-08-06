package com.quran.common.upgrade

import androidx.annotation.WorkerThread
import com.quran.data.model.QuranDataStatus

/**
 * Interface for handling data upgrades (pages, databases, etc) between
 * upgrades of versions of Quran for Android.
 */
interface LocalDataUpgrade {
  @WorkerThread
  fun processData(quranDataStatus: QuranDataStatus): QuranDataStatus { return quranDataStatus }

  @WorkerThread
  fun processPatch(quranDataStatus: QuranDataStatus): QuranDataStatus { return quranDataStatus }
}
