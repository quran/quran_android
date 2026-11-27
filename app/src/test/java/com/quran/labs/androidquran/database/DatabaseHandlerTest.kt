package com.quran.labs.androidquran.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class DatabaseHandlerTest {

  private lateinit var appContext: Application
  private val createdFiles = mutableListOf<File>()

  @Before
  fun setUp() {
    appContext = ApplicationProvider.getApplicationContext()
  }

  @After
  fun tearDown() {
    DatabaseHandler.clearDatabaseHandlerIfExists("test_verses.db")
    SuraTimingDatabaseHandler.clearDatabaseHandlerIfExists(
      File(appContext.cacheDir, "timings_test.db").absolutePath
    )
    createdFiles.forEach { it.delete() }
    createdFiles.clear()
  }

  @Test
  fun `getDatabaseHandler rejects path traversal database names`() {
    val quranFileUtils = FakeQuranFileUtils.create(appContext)

    var thrown: IllegalArgumentException? = null
    try {
      DatabaseHandler.getDatabaseHandler(appContext, "../outside.db", quranFileUtils)
    } catch (e: IllegalArgumentException) {
      thrown = e
    }

    assertThat(thrown).isNotNull()
  }

  @Test
  fun `getVersesByIds returns null when ids list is empty`() {
    val quranFileUtils = FakeQuranFileUtils.create(appContext)
    val dbDir = quranFileUtils.getQuranDatabaseDirectory()
    dbDir.mkdirs()
    val dbFile = File(dbDir, "test_verses.db")
    createdFiles += dbFile

    SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
      db.execSQL("CREATE TABLE properties (property TEXT, value INTEGER)")
      db.execSQL("INSERT INTO properties VALUES ('schema_version', 1)")
      db.execSQL("CREATE TABLE arabic_text (sura INTEGER, ayah INTEGER, text TEXT)")
    }

    val handler = DatabaseHandler.getDatabaseHandler(appContext, "test_verses.db", quranFileUtils)
    assertThat(handler.validDatabase()).isTrue()
    assertThat(handler.getVersesByIds(emptyList())).isNull()
  }

  @Test
  fun `suraTimingDatabaseHandler reads properties with parameterized query`() {
    val timingDbFile = File(appContext.cacheDir, "timings_test.db")
    createdFiles += timingDbFile
    SQLiteDatabase.openOrCreateDatabase(timingDbFile, null).use { db ->
      db.execSQL("CREATE TABLE properties (property TEXT, value INTEGER)")
      db.execSQL("INSERT INTO properties VALUES ('version', 7)")
      db.execSQL("INSERT INTO properties VALUES ('schema_version', 2)")
    }

    val handler = SuraTimingDatabaseHandler.getDatabaseHandler(timingDbFile.absolutePath)
    assertThat(handler.getVersion()).isEqualTo(7)
    assertThat(handler.getSchemaVersion()).isEqualTo(2)
  }
}
