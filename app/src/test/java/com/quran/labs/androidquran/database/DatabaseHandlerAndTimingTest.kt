package com.quran.labs.androidquran.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.common.search.RustArabicSearchEngine
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
    DatabaseHandler.clearDatabaseHandlerIfExists("ar_norm.db")
    DatabaseHandler.clearDatabaseHandlerIfExists("ar_comp.db")
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
  fun `searchArabicNormalized folds diacritics`() {
    val quranFileUtils = FakeQuranFileUtils.create(context)
    val dbDir = quranFileUtils.getQuranDatabaseDirectory().apply { mkdirs() }
    val dbFile = File(dbDir, "ar_norm.db")
    dbFile.delete()
    DatabaseHandler.clearDatabaseHandlerIfExists("ar_norm.db")

    val longPrefix = "مقدمة طويلة في التفسير ".repeat(12)
    val longSuffix = " وخاتمة طويلة في بيان المعاني".repeat(12)
    val longVerse = "$longPrefix وَأَخْبِرْ -أَيُّهَا الرَّسُولُ- الَّذِينَ آمَنُوا بِاللَّهِ وَأَقَامُوا الصَّلَاةَ وَالْإِيمَانِ وَالزَّكَاةَ لِمُوسَىٰ $longSuffix"

    val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      db.execSQL("CREATE TABLE properties(property TEXT, value INTEGER)")
      db.execSQL("INSERT INTO properties VALUES ('schema_version', 2)")
      db.execSQL("CREATE VIRTUAL TABLE verses USING fts3 (sura INTEGER, ayah INTEGER, text TEXT)")
      val stmt = db.compileStatement("INSERT INTO verses(sura, ayah, text) VALUES (?, ?, ?)")
      stmt.bindLong(1, 2L)
      stmt.bindLong(2, 25L)
      stmt.bindString(3, longVerse)
      stmt.executeInsert()
    } finally {
      db.close()
    }

    try {
      val handler = DatabaseHandler.getDatabaseHandler(context, "ar_norm.db", quranFileUtils)
      assertThat(RustArabicSearchEngine.isLoaded()).isFalse()

      // Long tafsir extracts clean ~220-char snippet centered on match with ellipses and highlights full diacritized word
      handler.searchArabicNormalized("الله")!!.use { c ->
        assertThat(c.count).isEqualTo(1)
        assertThat(c.moveToFirst()).isTrue()
        assertThat(c.getInt(1)).isEqualTo(2)
        assertThat(c.getInt(2)).isEqualTo(25)
        val snippet = c.getString(3)
        assertThat(snippet).startsWith("... ")
        assertThat(snippet).endsWith(" ...")
        assertThat(snippet).contains("اللَّهِ</font>")
        assertThat(snippet.length).isLessThan(350)
      }

      // Multi-token non-contiguous Arabic query ("الصلاة الزكاة") matches and highlights both words
      handler.searchArabicNormalized("الصلاة الزكاة")!!.use { c ->
        assertThat(c.count).isEqualTo(1)
        assertThat(c.moveToFirst()).isTrue()
        val snippet = c.getString(3)
        assertThat(snippet).contains("الصَّلَاةَ</font>")
        assertThat(snippet).contains("الزَّكَاةَ</font>")
      }

      // Diacritic/Alef/Ta-Marbuta/Ya variants all match
      for (q in listOf("الايمان", "ايها", "الصلاه", "موسى", "موسي", "امنوا", "ايها الرسول")) {
        handler.searchArabicNormalized(q)!!.use { c ->
          assertThat(c.count).isEqualTo(1)
        }
      }

      assertThat(RustArabicSearchEngine.isLoaded()).isFalse()
    } finally {
      DatabaseHandler.clearDatabaseHandlerIfExists("ar_norm.db")
      dbFile.delete()
    }
  }

  @Test
  fun `searchArabicNormalized comprehensive allah and jannah recall with ranking and zero noise`() {
    val quranFileUtils = FakeQuranFileUtils.create(context)
    val dbDir = quranFileUtils.getQuranDatabaseDirectory().apply { mkdirs() }
    val dbFile = File(dbDir, "ar_comp.db").apply { delete() }
    DatabaseHandler.clearDatabaseHandlerIfExists("ar_comp.db")

    SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
      db.execSQL("CREATE TABLE properties(property TEXT, value INTEGER)")
      db.execSQL("INSERT INTO properties VALUES ('schema_version', 2)")
      db.execSQL("CREATE VIRTUAL TABLE verses USING fts3 (sura INTEGER, ayah INTEGER, text TEXT)")
      val rows = listOf(
        Triple(1, 2, "الثناء على اللَّهِ والحمد لِلَّهِ رب العالمين"),
        Triple(2, 25, "وبشر الذين آمنوا بأن لهم جَنَّاتٍ تجري من تحتها الأنهار"),
        Triple(2, 35, "اسكن أنت وزوجك الجَنَّةَ"),
        Triple(2, 45, "53"), // numeric hyperlink row like real quran.ar.muyassar.db
        Triple(3, 133, "وسارعوا إلى مغفرة وجَنَّةٍ عرضها السماوات"),
        Triple(6, 100, "وجعلوا لِلَّهِ شركاء الجِنَّ"),
        Triple(18, 33, "كلتا الجَنَّتَيْنِ آتت أكلها"),
        Triple(29, 64, "إنما هذه الحياة الدنيا لَهْوٌ واللَّهْوُ زائل"),
        Triple(53, 32, "وإذ أنتم أَجِنَّةٌ في بطون أمهاتكم"),
        Triple(53, 25, "فَلِلَّهِ الآخرة والأولى")
      )
      val stmt = db.compileStatement("INSERT INTO verses(sura, ayah, text) VALUES (?, ?, ?)")
      for ((s, a, t) in rows) {
        stmt.clearBindings()
        stmt.bindLong(1, s.toLong())
        stmt.bindLong(2, a.toLong())
        stmt.bindString(3, t)
        stmt.executeInsert()
      }
    }

    try {
      val handler = DatabaseHandler.getDatabaseHandler(context, "ar_comp.db", quranFileUtils)

      // "الله" matches لله / للَّه / فلله and rejects اللهو (29:64) and numeric hyperlink rows!
      handler.searchArabicNormalized("الله")!!.use { c ->
        val pairs = mutableListOf<String>()
        while (c.moveToNext()) {
          pairs.add("${c.getInt(1)}:${c.getInt(2)}")
          assertThat(c.getString(3)).contains("<font color=")
        }
        assertThat(pairs).containsExactly("1:2", "6:100", "53:25").inOrder()
      }

      // "الجنة" ranks Tier 1 (الجنة 2:35) -> Tier 2 (جنة 3:133) -> Tier 3 (جنات 2:25, الجنتين 18:33)
      // and rejects الجن (6:100) and أجنة (53:32)!
      handler.searchArabicNormalized("الجنة")!!.use { c ->
        val pairs = mutableListOf<String>()
        while (c.moveToNext()) {
          pairs.add("${c.getInt(1)}:${c.getInt(2)}")
          assertThat(c.getString(3)).contains("<font color=")
        }
        assertThat(pairs).containsExactly("2:35", "3:133", "2:25", "18:33").inOrder()
      }
    } finally {
      DatabaseHandler.clearDatabaseHandlerIfExists("ar_comp.db")
      dbFile.delete()
    }
  }

  @Test
  fun `searchArabicNormalized supports universal muslim inflections and broken plurals in Tafsir`() {
    val quranFileUtils = FakeQuranFileUtils.create(context)
    val dbDir = quranFileUtils.getQuranDatabaseDirectory().apply { mkdirs() }
    val dbFile = File(dbDir, "ar_comp.db").apply { delete() }
    DatabaseHandler.clearDatabaseHandlerIfExists("ar_comp.db")

    SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
      db.execSQL("CREATE TABLE properties(property TEXT, value INTEGER)")
      db.execSQL("INSERT INTO properties VALUES ('schema_version', 2)")
      db.execSQL("CREATE VIRTUAL TABLE verses USING fts3 (sura INTEGER, ayah INTEGER, text TEXT)")
      val rows = listOf(
        Triple(2, 7, "ختم الله على قُلُوبِهِمْ فلم يعودوا يؤمنون"),
        Triple(2, 128, "واجعل من ذريتنا أمة مُسْلِمَةً لك"),
        Triple(3, 67, "كان إبراهيم حنيفاً مُسْلِماً"),
        Triple(16, 89, "وبشرى لِلْمُسْلِمِينَ الموحدين"),
        Triple(33, 35, "إن المُسْلِمِينَ والمُسْلِمَاتِ أعد الله لهم مغفرة"),
        Triple(3, 112, "ويقتلون الأَنْبِيَاءَ والنَّبِيِّينَ بغير حق"),
        Triple(5, 64, "غُلَّتْ أَيْدِيهِمْ ولعنوا بما قالوا"),
        Triple(12, 5, "فيكيدوا لك كَيْداً عظيماً")
      )
      val stmt = db.compileStatement("INSERT INTO verses(sura, ayah, text) VALUES (?, ?, ?)")
      for ((s, a, t) in rows) {
        stmt.clearBindings()
        stmt.bindLong(1, s.toLong())
        stmt.bindLong(2, a.toLong())
        stmt.bindString(3, t)
        stmt.executeInsert()
      }
    }

    try {
      val handler = DatabaseHandler.getDatabaseHandler(context, "ar_comp.db", quranFileUtils)

      handler.searchArabicNormalized("مسلم")!!.use { c ->
        val pairs = mutableListOf<String>()
        while (c.moveToNext()) {
          pairs.add("${c.getInt(1)}:${c.getInt(2)}")
          assertThat(c.getString(3)).contains("<font color=")
        }
        // Tier 2 (مسلمة 2:128, مسلما 3:67) -> Tier 3 (للمسلمين 16:89, المسلمين والمسلمات 33:35)
        assertThat(pairs).containsExactly("2:128", "3:67", "16:89", "33:35").inOrder()
      }

      handler.searchArabicNormalized("قلب")!!.use { c ->
        assertThat(c.count).isEqualTo(1)
        assertThat(c.moveToFirst()).isTrue()
        assertThat(c.getString(3)).contains("قُلُوبِهِمْ</font>")
      }

      handler.searchArabicNormalized("نبي")!!.use { c ->
        assertThat(c.count).isEqualTo(1)
        assertThat(c.moveToFirst()).isTrue()
        val snippet = c.getString(3)
        assertThat(snippet).contains("الأَنْبِيَاءَ</font>")
        assertThat(snippet).contains("النَّبِيِّينَ</font>")
      }

      // اليد matches أيديهم (5:64) and NEVER كيدا (12:5)
      handler.searchArabicNormalized("اليد")!!.use { c ->
        assertThat(c.count).isEqualTo(1)
        assertThat(c.moveToFirst()).isTrue()
        assertThat(c.getInt(1)).isEqualTo(5)
        assertThat(c.getInt(2)).isEqualTo(64)
      }
    } finally {
      DatabaseHandler.clearDatabaseHandlerIfExists("ar_comp.db")
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
