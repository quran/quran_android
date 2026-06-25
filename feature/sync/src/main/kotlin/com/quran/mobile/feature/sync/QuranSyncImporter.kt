package com.quran.mobile.feature.sync

import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportResult
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.bookmark.importdata.MobileSyncImporterImpl
import com.quran.mobile.bookmark.importdata.toMobileSyncImportResult
import com.quran.mobile.bookmark.importdata.toPersistenceImportData
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(
  AppScope::class,
  replaces = [MobileSyncImporterImpl::class]
)
class QuranSyncImporter @Inject constructor(
  private val syncManager: QuranSyncManager
) : MobileSyncImporter {

  override suspend fun importData(
    data: MobileSyncImportData,
    deleteExisting: Boolean
  ): MobileSyncImportResult {
    return syncManager.quranDataService
      .importData(data.toPersistenceImportData(), deleteExisting = deleteExisting)
      .toMobileSyncImportResult()
  }
}
