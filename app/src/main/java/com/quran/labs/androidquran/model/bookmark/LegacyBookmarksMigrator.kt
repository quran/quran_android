package com.quran.labs.androidquran.model.bookmark

import com.quran.data.di.AppScope
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.bookmark.migration.LegacyBookmarkMigrationNormalizer
import com.quran.mobile.bookmark.migration.LegacyBookmarksDataSource
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@SingleIn(AppScope::class)
class LegacyBookmarksMigrator @Inject constructor(
  private val quranSettings: QuranSettings,
  private val legacyBookmarksDataSource: Provider<LegacyBookmarksDataSource>,
  private val normalizer: Provider<LegacyBookmarkMigrationNormalizer>,
  private val mobileSyncImporter: Provider<MobileSyncImporter>
) {
  internal constructor(
    quranSettings: QuranSettings,
    legacyBookmarksDataSource: LegacyBookmarksDataSource,
    normalizer: LegacyBookmarkMigrationNormalizer,
    mobileSyncImporter: MobileSyncImporter
  ) : this(
    quranSettings = quranSettings,
    legacyBookmarksDataSource = Provider { legacyBookmarksDataSource },
    normalizer = Provider { normalizer },
    mobileSyncImporter = Provider { mobileSyncImporter }
  )

  private val migrationMutex = Mutex()

  suspend fun migrateIfNeeded(): Boolean {
    if (quranSettings.haveMigratedLegacyBookmarksToMobileSync()) return true

    return migrationMutex.withLock {
      if (quranSettings.haveMigratedLegacyBookmarksToMobileSync()) {
        true
      } else {
        migrate()
      }
    }
  }

  private suspend fun migrate(): Boolean {
    return withContext(Dispatchers.IO) {
      try {
        val snapshot = legacyBookmarksDataSource().snapshot()
        if (!snapshot.isEmpty()) {
          val migrationData = normalizer().normalize(snapshot)
          if (!migrationData.isEmpty()) {
            mobileSyncImporter().importData(migrationData, deleteExisting = false)
          }
        }

        quranSettings.setMigratedLegacyBookmarksToMobileSync()
        true
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Timber.e(exception, "Unable to migrate legacy bookmarks to mobile-sync.")
        false
      }
    }
  }
}
