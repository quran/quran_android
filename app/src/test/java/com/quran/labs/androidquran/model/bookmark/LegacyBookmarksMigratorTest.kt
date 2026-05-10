package com.quran.labs.androidquran.model.bookmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.Bookmark
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportResult
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.bookmark.migration.LegacyBookmarkMigrationNormalizer
import com.quran.mobile.bookmark.migration.LegacyBookmarksDataSource
import com.quran.mobile.bookmark.migration.LegacyBookmarksSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class LegacyBookmarksMigratorTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private lateinit var quranSettings: QuranSettings
  private lateinit var dataSource: FakeLegacyBookmarksDataSource
  private lateinit var importer: FakeMobileSyncImporter
  private lateinit var migrator: LegacyBookmarksMigrator

  @Before
  fun setUp() {
    QuranSettings.setInstance(null)
    quranSettings = QuranSettings.getInstance(context)
    resetMigrationFlag()
    QuranSettings.setInstance(null)
    quranSettings = QuranSettings.getInstance(context)
    dataSource = FakeLegacyBookmarksDataSource()
    importer = FakeMobileSyncImporter()
    migrator = LegacyBookmarksMigrator(
      quranSettings = quranSettings,
      legacyBookmarksDataSource = dataSource,
      normalizer = LegacyBookmarkMigrationNormalizer(context, QuranInfo(MadaniDataSource())),
      mobileSyncImporter = importer
    )
  }

  @Test
  fun `successful migration imports data and marks flag`() = runTest {
    dataSource.snapshot = snapshotWithBookmark()

    val migrated = migrator.migrateIfNeeded()

    assertThat(migrated).isTrue()
    assertThat(quranSettings.haveMigratedLegacyBookmarksToMobileSync()).isTrue()
    assertThat(importer.importedData?.bookmarks).hasSize(1)
    assertThat(importer.deleteExisting).isFalse()
  }

  @Test
  fun `concurrent migrations import data once`() = runTest {
    dataSource.snapshot = snapshotWithBookmark()
    importer.delayBeforeReturn = true

    val results = awaitAll(
      async { migrator.migrateIfNeeded() },
      async { migrator.migrateIfNeeded() }
    )

    assertThat(results).containsExactly(true, true)
    assertThat(quranSettings.haveMigratedLegacyBookmarksToMobileSync()).isTrue()
    assertThat(importer.importCount).isEqualTo(1)
  }

  @Test
  fun `snapshot failures are caught and leave migration pending`() = runTest {
    dataSource.exception = RuntimeException("corrupt legacy database")

    val migrated = migrator.migrateIfNeeded()

    assertThat(migrated).isFalse()
    assertThat(quranSettings.haveMigratedLegacyBookmarksToMobileSync()).isFalse()
    assertThat(importer.importedData).isNull()
  }

  @Test
  fun `import failures are caught and leave migration pending`() = runTest {
    dataSource.snapshot = snapshotWithBookmark()
    importer.exception = IllegalStateException("Cannot import into a non-empty database.")

    val migrated = migrator.migrateIfNeeded()

    assertThat(migrated).isFalse()
    assertThat(quranSettings.haveMigratedLegacyBookmarksToMobileSync()).isFalse()
  }

  @Test
  fun `cancellation is rethrown and leaves migration pending`() = runTest {
    dataSource.snapshot = snapshotWithBookmark()
    importer.exception = CancellationException("cancelled")

    try {
      migrator.migrateIfNeeded()
      fail("Expected CancellationException")
    } catch (_: CancellationException) {
      assertThat(quranSettings.haveMigratedLegacyBookmarksToMobileSync()).isFalse()
    }
  }

  private fun resetMigrationFlag() {
    context
      .getSharedPreferences(PER_INSTALLATION_PREFS, Context.MODE_PRIVATE)
      .edit()
      .remove(Constants.PREF_MOBILE_SYNC_LEGACY_BOOKMARKS_MIGRATED)
      .commit()
  }

  private fun snapshotWithBookmark(): LegacyBookmarksSnapshot {
    return LegacyBookmarksSnapshot(
      tags = emptyList(),
      bookmarks = listOf(Bookmark(1L, 2, 255, page = 50, timestamp = 1000L)),
      recentPages = emptyList()
    )
  }

  private class FakeLegacyBookmarksDataSource : LegacyBookmarksDataSource {
    var snapshot: LegacyBookmarksSnapshot = LegacyBookmarksSnapshot(
      tags = emptyList(),
      bookmarks = emptyList(),
      recentPages = emptyList()
    )
    var exception: RuntimeException? = null

    override fun snapshot(): LegacyBookmarksSnapshot {
      exception?.let { throw it }
      return snapshot
    }
  }

  private class FakeMobileSyncImporter : MobileSyncImporter {
    var importedData: MobileSyncImportData? = null
    var exception: Exception? = null
    var delayBeforeReturn: Boolean = false
    var importCount: Int = 0
    var deleteExisting: Boolean? = null

    override suspend fun importData(
      data: MobileSyncImportData,
      deleteExisting: Boolean
    ): MobileSyncImportResult {
      exception?.let { throw it }
      importCount++
      this.deleteExisting = deleteExisting
      if (delayBeforeReturn) {
        delay(1)
      }
      importedData = data
      return MobileSyncImportResult(
        bookmarksImported = data.bookmarks.size,
        collectionsImported = data.collections.size,
        collectionBookmarksImported = data.collectionBookmarks.size,
        readingSessionsImported = data.readingSessions.size,
        readingBookmarkImported = data.readingBookmark != null
      )
    }
  }

  companion object {
    private const val PER_INSTALLATION_PREFS = "com.quran.labs.androidquran.per_installation"
  }
}
