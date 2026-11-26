package com.quran.labs.androidquran.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class DatabaseHandlerAndTimingTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @After
  fun tearDown() {
    DatabaseHandler.clearDatabaseHandlerIfExists("translation_test.db")
    DatabaseHandler.clearDatabaseHandlerIfExists("fts3.db")
  }

  @Test
  fun `databaseHandler queries single and multi-sura ranges with parameterized bind args`() {
    val quranFileUtils = FakeQuranFileUtils.create(context)
    val dbDir = quranFileUtils.getQuranDatabaseDirectory().apply { mkdirs() }
    val dbFile = File(dbDir, "translation_test.db")
    dbFile.delete()

    val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      db.execSQL("CREATE TABLE properties(property TEXT, value INTEGER)")
      db.execSQL("INSERT INTO properties VALUES ('schema_version', 2)")
      db.execSQL("CREATE TABLE verses(sura INTEGER, ayah INTEGER, text TEXT)")
      db.execSQL("INSERT INTO verses VALUES (1, 6, '1:6')")
      db.execSQL("INSERT INTO verses VALUES (1, 7, '1:7')")
      db.execSQL("INSERT INTO verses VALUES (2, 1, '2:1')")
      db.execSQL("INSERT INTO verses VALUES (2, 2, '2:2')")
      db.execSQL("INSERT INTO verses VALUES (3, 1, '3:1')")
      db.execSQL("INSERT INTO verses VALUES (3, 2, '3:2')")
    } finally {
      db.close()
    }

    try {
      val handler = DatabaseHandler.getDatabaseHandler(context, "translation_test.db", quranFileUtils)
      assertThat(handler.validDatabase()).isTrue()

      val singleSura = handler.getVerses(
        VerseRange(1, 6, 1, 7, 2),
        DatabaseHandler.TextType.TRANSLATION
      )
      assertThat(singleSura.map { "${it.sura}:${it.ayah}" })
        .containsExactly("1:6", "1:7")
        .inOrder()

      val multiSura = handler.getVerses(
        VerseRange(1, 7, 3, 1, 4),
        DatabaseHandler.TextType.TRANSLATION
      )
      assertThat(multiSura.map { "${it.sura}:${it.ayah}" })
        .containsExactly("1:7", "2:1", "2:2", "3:1")
        .inOrder()
    } finally {
      DatabaseHandler.clearDatabaseHandlerIfExists("translation_test.db")
      dbFile.delete()
    }
  }

  @Test
  fun `fts3 lookup casts integer bind args`() {
    val quranFileUtils = FakeQuranFileUtils.create(context)
    val dbDir = quranFileUtils.getQuranDatabaseDirectory().apply { mkdirs() }
    val dbFile = File(dbDir, "fts3.db")
    dbFile.delete()
    DatabaseHandler.clearDatabaseHandlerIfExists("fts3.db")

    val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      db.execSQL("CREATE TABLE properties(property TEXT, value INTEGER)")
      db.execSQL("INSERT INTO properties VALUES ('schema_version', 2)")
      db.execSQL("CREATE VIRTUAL TABLE verses USING fts3 (sura INTEGER, ayah INTEGER, text TEXT)")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (1, 7, 'صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ')")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (2, 25, 'وأَخْبِرْ -أيها الرسول- الذين آمنوا بالله وعملوا الصالحات')")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (2, 26, 'إن الله لا يستحيي أن يضرب مثلا')")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (3, 1, 'الم')")
    } finally {
      db.close()
    }

    try {
      val handler = DatabaseHandler.getDatabaseHandler(context, "fts3.db", quranFileUtils)
      assertThat(handler.validDatabase()).isTrue()

      val singleVerse = handler.getVerses(
        VerseRange(2, 25, 2, 25, 1),
        DatabaseHandler.TextType.TRANSLATION
      )
      assertThat(singleVerse).hasSize(1)
      assertThat(singleVerse.single().sura).isEqualTo(2)
      assertThat(singleVerse.single().ayah).isEqualTo(25)
      assertThat(singleVerse.single().text).startsWith("وأَخْبِرْ")

      val multiSura = handler.getVerses(
        VerseRange(1, 7, 3, 1, 4),
        DatabaseHandler.TextType.TRANSLATION
      )
      assertThat(multiSura.map { "${it.sura}:${it.ayah}" })
        .containsExactly("1:7", "2:25", "2:26", "3:1")
        .inOrder()
    } finally {
      DatabaseHandler.clearDatabaseHandlerIfExists("fts3.db")
      dbFile.delete()
    }
  }

  @Test
  fun `suraTimingDatabaseHandler caches schemaVersion and queries timings with bind args`() {
    val dbFile = File(context.cacheDir, "timing_test.db")
    dbFile.delete()

    val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      db.execSQL("CREATE TABLE properties(property TEXT, value INTEGER)")
      db.execSQL("INSERT INTO properties VALUES ('version', 3)")
      db.execSQL("INSERT INTO properties VALUES ('schema_version', 2)")
      db.execSQL("CREATE TABLE timings(sura INTEGER, ayah INTEGER, time INTEGER, words TEXT)")
      db.execSQL("INSERT INTO timings VALUES (1, 1, 100, '1:0:50')")
      db.execSQL("INSERT INTO timings VALUES (1, 2, 200, '1:50:150')")
      db.execSQL("INSERT INTO timings VALUES (2, 1, 300, '1:0:90')")
    } finally {
      db.close()
    }

    val path = dbFile.absolutePath
    try {
      val handler = SuraTimingDatabaseHandler.getDatabaseHandler(path)
      assertThat(handler.getVersion()).isEqualTo(3)
      assertThat(handler.getSchemaVersion()).isEqualTo(2)
      // Cached second call returns same schemaVersion
      assertThat(handler.getSchemaVersion()).isEqualTo(2)

      val cursor = handler.getAyahTimings(1)
      assertThat(cursor).isNotNull()
      try {
        assertThat(cursor!!.count).isEqualTo(2)
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getInt(1)).isEqualTo(1)
        assertThat(cursor.getLong(2)).isEqualTo(100L)
        assertThat(cursor.getString(3)).isEqualTo("1:0:50")
        assertThat(cursor.moveToNext()).isTrue()
        assertThat(cursor.getInt(1)).isEqualTo(2)
      } finally {
        cursor?.close()
      }
    } finally {
      SuraTimingDatabaseHandler.clearDatabaseHandlerIfExists(path)
      dbFile.delete()
    }
  }
}
