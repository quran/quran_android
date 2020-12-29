package com.quran.data.upgrade

import androidx.annotation.WorkerThread
import com.quran.data.model.QuranDataStatus

fun interface LocalDataUpgrade {
  @WorkerThread
  fun processData(quranDataStatus: QuranDataStatus): QuranDataStatus
}
