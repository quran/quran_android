package com.quran.mobile.feature.sync

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportResult
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.bookmark.importdata.MobileSyncImporterImpl
import com.quran.mobile.bookmark.importdata.toMobileSyncImportResult
import com.quran.mobile.bookmark.importdata.toPersistenceImportData
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(
  AppScope::class,
  replaces = [MobileSyncImporterImpl::class]
)
@Inject
class QuranSyncImporter(
  private val syncManager: QuranSyncManager,
  @param:ApplicationContext private val appContext: Context
) : MobileSyncImporter {

  override suspend fun importData(
    data: MobileSyncImportData,
    deleteExisting: Boolean
  ): MobileSyncImportResult {
    return syncManager.quranDataService
      .importData(data.toPersistenceImportData(appContext), deleteExisting = deleteExisting)
      .toMobileSyncImportResult()
  }
}
