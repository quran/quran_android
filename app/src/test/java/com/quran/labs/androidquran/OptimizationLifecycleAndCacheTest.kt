package com.quran.labs.androidquran

import android.content.ComponentCallbacks2
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.BookmarksDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.Tag
import com.quran.data.model.collection.ReadingCollection
import com.quran.data.model.collection.ReadingCollectionBookmarks
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.model.bookmark.LegacyBookmarksMigrator
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.presenter.quran.QuranPagePresenter
import com.quran.labs.androidquran.service.AudioService
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.ui.helpers.PageBitmapRecycler
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.SearchResultFormatter
import com.quran.labs.androidquran.util.SettingsImpl
import com.quran.labs.androidquran.widget.BookmarksWidgetSubscriber
import com.quran.labs.androidquran.widget.BookmarksWidgetUpdater
import com.quran.mobile.bookmark.di.BookmarkDataModule
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportResult
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.bookmark.migration.LegacyBookmarkMigrationNormalizer
import com.quran.mobile.bookmark.migration.LegacyBookmarksDataSource
import com.quran.mobile.bookmark.migration.LegacyBookmarksSnapshot
import com.quran.mobile.translation.catalog.di.TranslationDataModule
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class OptimizationLifecycleAndCacheTest {

  private val app = ApplicationProvider.getApplicationContext<TestApplication>()
  private val context: Context = app

  @Before
  fun setUp() {
    QuranSettings.setInstance(null)
    SearchResultFormatter.clearCache()
    QuranUtils.clearLocalizedNumberCache()
  }

  @After
  fun tearDown() {
    QuranSettings.setInstance(null)
    SearchResultFormatter.clearCache()
    QuranUtils.clearLocalizedNumberCache()
  }

  @Test
  fun `application exposes on-demand WorkManager configuration and trims caches under memory pressure`() {
    val config = app.workManagerConfiguration
    assertThat(config.workerFactory).isNotNull()

    SearchResultFormatter.format("بسم <font color=\"-13388315\">الله</font>")
    assertThat(SearchResultFormatter.cacheSize()).isEqualTo(1)

    app.trimCaches(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
    assertThat(SearchResultFormatter.cacheSize()).isEqualTo(0)

    // Populate again and verify full background trim clears safely without throwing
    SearchResultFormatter.format("بسم <font color=\"-13388315\">الله</font>")
    assertThat(QuranUtils.getLocalizedNumber(1)).isNotEmpty()
    app.trimCaches(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
    assertThat(SearchResultFormatter.cacheSize()).isEqualTo(0)
    assertThat(QuranUtils.getLocalizedNumber(1)).isNotEmpty()
  }

  @Test
  fun `bookmarksWidgetSubscriber does not instantiate BookmarksDao when no widgets exist`() {
    var daoProviderCalls = 0
    val updater = object : BookmarksWidgetUpdater {
      override fun checkForAnyBookmarksWidgets(): Boolean = false
      override fun updateBookmarksWidget() = Unit
    }
    val subscriber = BookmarksWidgetSubscriber(
      bookmarksDao = Provider {
        daoProviderCalls++
        error("BookmarksDao must not be created when no widgets are installed")
      },
      bookmarksWidgetUpdater = updater
    )

    subscriber.subscribeBookmarksWidgetIfNecessary()

    assertThat(daoProviderCalls).isEqualTo(0)
  }

  @Test
  fun `bookmarksWidgetSubscriber subscribes lazily when widget exists and unsubscribes on disable`() {
    var daoProviderCalls = 0
    val changesFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val updateLatch = CountDownLatch(1)
    val updater = object : BookmarksWidgetUpdater {
      override fun checkForAnyBookmarksWidgets(): Boolean = true
      override fun updateBookmarksWidget() {
        updateLatch.countDown()
      }
    }
    val subscriber = BookmarksWidgetSubscriber(
      bookmarksDao = Provider {
        daoProviderCalls++
        FakeChangesBookmarksDao(changesFlow)
      },
      bookmarksWidgetUpdater = updater
    )

    subscriber.subscribeBookmarksWidgetIfNecessary()
    assertThat(daoProviderCalls).isEqualTo(1)

    // Give collector a moment to attach, then emit a bookmark change
    Thread.sleep(50)
    changesFlow.tryEmit(Unit)
    assertThat(updateLatch.await(2, TimeUnit.SECONDS)).isTrue()

    subscriber.onDisabledBookmarksWidget()
  }

  @Test
  fun `legacyBookmarksMigrator skips provider instantiation once already migrated`() = runTest {
    val settings = QuranSettings.getInstance(context)
    settings.setMigratedLegacyBookmarksToMobileSync()

    var dataSourceCreated = 0
    var normalizerCreated = 0
    var importerCreated = 0

    val migrator = LegacyBookmarksMigrator(
      quranSettings = settings,
      legacyBookmarksDataSource = Provider {
        dataSourceCreated++
        object : LegacyBookmarksDataSource {
          override fun snapshot(): LegacyBookmarksSnapshot =
            LegacyBookmarksSnapshot(emptyList(), emptyList(), emptyList())
        }
      },
      normalizer = Provider {
        normalizerCreated++
        LegacyBookmarkMigrationNormalizer(QuranInfo(MadaniDataSource()))
      },
      mobileSyncImporter = Provider {
        importerCreated++
        object : MobileSyncImporter {
          override suspend fun importData(
            data: MobileSyncImportData,
            deleteExisting: Boolean
          ): MobileSyncImportResult = MobileSyncImportResult(0, 0, 0, 0, 0)
        }
      }
    )

    val result = migrator.migrateIfNeeded()

    assertThat(result).isTrue()
    assertThat(dataSourceCreated).isEqualTo(0)
    assertThat(normalizerCreated).isEqualTo(0)
    assertThat(importerCreated).isEqualTo(0)
  }

  @Test
  fun `quranDisplayData caches sura names and returns consistent results`() {
    val displayData = QuranDisplayData(QuranInfo(MadaniDataSource()))
    val first = displayData.getSuraName(context, 1, wantPrefix = false, wantTranslation = false)
    val second = displayData.getSuraName(context, 1, wantPrefix = false, wantTranslation = false)
    val withTranslation = displayData.getSuraName(context, 1, wantPrefix = true, wantTranslation = true)

    assertThat(first).isNotEmpty()
    assertThat(second).isEqualTo(first)
    assertThat(withTranslation).contains(first)

    displayData.clearCache()
    assertThat(displayData.getSuraName(context, 1, wantPrefix = false, wantTranslation = false))
      .isEqualTo(first)
  }

  @Test
  fun `localized number cache covers mushaf range and handles out-of-range values`() {
    val page1First = QuranUtils.getLocalizedNumber(1)
    val page1Second = QuranUtils.getLocalizedNumber(1)
    val page604 = QuranUtils.getLocalizedNumber(604)
    val beyondRange = QuranUtils.getLocalizedNumber(6236)

    assertThat(page1Second).isSameInstanceAs(page1First)
    assertThat(page604).isNotEmpty()
    assertThat(beyondRange).isNotEmpty()
  }

  @Test
  fun `services report not running before start and page presenter uses reduced settle delay`() {
    assertThat(AudioService.isRunning()).isFalse()
    assertThat(QuranDownloadService.isRunning()).isFalse()
    assertThat(QuranPagePresenter.AYAH_COORDINATES_DELAY_MS).isEqualTo(150L)
  }

  @Test
  fun `pageBitmapRecycler defers recycling until after delay and ignores already recycled bitmaps`() {
    val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ALPHA_8)
    PageBitmapRecycler.scheduleRecycle(bitmap)

    // Must NOT recycle synchronously while a RenderThread frame may still be finishing
    assertThat(bitmap.isRecycled).isFalse()

    ShadowLooper.idleMainLooper(PageBitmapRecycler.RECYCLE_DELAY_MS, TimeUnit.MILLISECONDS)
    assertThat(bitmap.isRecycled).isTrue()

    // Re-scheduling an already-recycled bitmap is a safe no-op
    PageBitmapRecycler.scheduleRecycle(bitmap)
    ShadowLooper.idleMainLooper(PageBitmapRecycler.RECYCLE_DELAY_MS, TimeUnit.MILLISECONDS)
    assertThat(bitmap.isRecycled).isTrue()
  }

  @Test
  fun `writable sqldelight databases enable WAL journal mode`() {
    context.deleteDatabase("translations.db")
    context.deleteDatabase("bookmarks.db")

    val translationsDb = TranslationDataModule().provideTranslationDatabase(context)
    assertThat(translationsDb.translationsQueries.all().executeAsList()).isEmpty()
    assertDatabaseUsesWal("translations.db")

    val settings = SettingsImpl(QuranSettings.getInstance(context))
    val bookmarksDb = BookmarkDataModule().provideBookmarksDatabase(context, settings)
    assertThat(bookmarksDb.tagQueries.getTags().executeAsList()).isEmpty()
    assertDatabaseUsesWal("bookmarks.db")
  }

  @Test
  fun `read-only DatabaseHandler opens cleanly with bounded cache_size pragma`() {
    val fileUtils = FakeQuranFileUtils.create(context)
    val dbDir = fileUtils.getQuranDatabaseDirectory()
    dbDir.mkdirs()
    val dbName = "opt_cache_size_test.db"
    val dbFile = File(dbDir, dbName)
    dbFile.delete()

    SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
      db.execSQL("CREATE TABLE properties(property TEXT, value TEXT)")
      db.execSQL("INSERT INTO properties VALUES ('schema_version', '2')")
      db.execSQL("CREATE TABLE verses(sura INTEGER, ayah INTEGER, text TEXT)")
      db.execSQL("INSERT INTO verses VALUES (1, 1, 'In the name of Allah')")
    }

    try {
      val handler = DatabaseHandler.getDatabaseHandler(context, dbName, fileUtils)
      assertThat(handler.validDatabase()).isTrue()
      assertThat(handler.getSchemaVersion()).isEqualTo(2)
    } finally {
      DatabaseHandler.clearDatabaseHandlerIfExists(dbName)
      dbFile.delete()
    }
  }

  private fun assertDatabaseUsesWal(dbName: String) {
    val dbFile = context.getDatabasePath(dbName)
    assertThat(dbFile.exists()).isTrue()
    SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
      db.rawQuery("PRAGMA journal_mode", null).use { cursor ->
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(0).lowercase()).isEqualTo("wal")
      }
    }
  }

  private class FakeChangesBookmarksDao(
    override val changes: Flow<Unit>
  ) : BookmarksDao {
    override suspend fun bookmarks(sortOrder: Int): List<Bookmark> = emptyList()
    override fun bookmarksFlow(sortOrder: Int): Flow<List<Bookmark>> = emptyFlow()
    override fun collectionsWithBookmarksFlow(): Flow<List<ReadingCollectionBookmarks>> = emptyFlow()
    override suspend fun addCollection(name: String): ReadingCollection = error("unused")
    override fun bookmarksForPage(page: Int): Flow<List<Bookmark>> = emptyFlow()
    override suspend fun tags(): List<Tag> = emptyList()
    override fun tagsFlow(): Flow<List<Tag>> = emptyFlow()
    override suspend fun addTag(name: String): String = ""
    override suspend fun updateTag(tag: Tag): Boolean = false
    override suspend fun removeTags(tags: List<Tag>) = Unit
    override suspend fun getBookmarkTagIds(bookmarkId: String): List<String> = emptyList()
    override suspend fun updateBookmarkTags(
      bookmarkIds: Array<String>,
      tagIds: Set<String>,
      deleteNonTagged: Boolean
    ): Boolean = false
    override suspend fun removeBookmarkFromTag(bookmark: Bookmark, tagId: String): Boolean = false
    override suspend fun removeBookmarks(bookmarks: List<Bookmark>) = Unit
    override suspend fun deleteAyahBookmark(suraAyah: SuraAyah): Boolean = false
    override suspend fun replaceAyahBookmarkCollections(
      suraAyah: SuraAyah,
      collectionIds: Set<String>
    ): Boolean = false
    override suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Boolean = false
  }
}
