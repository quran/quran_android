package com.quran.labs.androidquran.data

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.common.search.ArabicSearcher
import com.quran.common.search.DefaultSearcher
import com.quran.common.search.RustArabicSearchEngine
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.translation.model.LocalTranslation
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QuranDataProviderSearchTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `search closes non-matching translation cursors before returning match`() {
    val createdCursors = mutableMapOf<String, MatrixCursor>()
    val provider = createProvider { _, databaseName, _ ->
      val cursor = MatrixCursor(arrayOf("_id", "sura", "ayah", "text"))
      if (databaseName == "en_match.db") {
        cursor.addRow(arrayOf<Any>(1, 1, 1, "In the name of Allah"))
      }
      createdCursors[databaseName] = cursor
      cursor
    }

    val translations = listOf(
      LocalTranslation(id = 1, filename = "en_empty_1.db", languageCode = "en"),
      LocalTranslation(id = 2, filename = "en_empty_2.db", languageCode = "en"),
      LocalTranslation(id = 3, filename = "en_match.db", languageCode = "en"),
      LocalTranslation(id = 4, filename = "en_unreached.db", languageCode = "en")
    )

    val result = provider.search("Allah", translations)

    assertThat(result).isSameInstanceAs(createdCursors["en_match.db"])
    assertThat(result!!.isClosed).isFalse()
    assertThat(createdCursors["en_empty_1.db"]!!.isClosed).isTrue()
    assertThat(createdCursors["en_empty_2.db"]!!.isClosed).isTrue()
    assertThat(createdCursors.containsKey("en_unreached.db")).isFalse()
    result.close()
  }

  @Test
  fun `search closes all cursors when no translation matches`() {
    val createdCursors = mutableListOf<MatrixCursor>()
    val provider = createProvider { _, _, _ ->
      MatrixCursor(arrayOf("_id", "sura", "ayah", "text")).also { createdCursors.add(it) }
    }

    val translations = listOf(
      LocalTranslation(id = 1, filename = "en_1.db", languageCode = "en"),
      LocalTranslation(id = 2, filename = "en_2.db", languageCode = "en")
    )

    val result = provider.search("nonexistent", translations)

    assertThat(result).isNull()
    assertThat(createdCursors).hasSize(2)
    assertThat(createdCursors.all { it.isClosed }).isTrue()
  }

  @Test
  fun `arabicSearcher runQuery highlights matches reusing compiled matcher and escapes replacement`() {
    val dbFile = File(context.cacheDir, "arabic_search_test.db")
    dbFile.delete()
    val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      db.execSQL("CREATE TABLE verses(sura INTEGER, ayah INTEGER, text TEXT)")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (1, 1, 'بسم الله الرحمن الرحيم')")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (1, 2, 'الحمد لله رب العالمين')")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (2, 1, 'ألم ذلك الكتاب')")

      val searcher = ArabicSearcher(
        defaultSearcher = DefaultSearcher("<font color=\"$1\">", "</font>", "..."),
        matchStart = "<font color=\"$1\">",
        matchEnd = "</font>"
      )
      val columns = arrayOf("_id", "sura", "ayah", "text")
      val sql = searcher.getQuery(true, false, "verses", "rowid as _id, sura, ayah", "text")
      val likeArg = searcher.processSearchText("الله", false)

      val cursor = searcher.runQuery(db, sql, likeArg, "الله", true, columns)
      try {
        assertThat(cursor.count).isEqualTo(1)
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(3))
          .isEqualTo("بسم <font color=\"$1\">الله</font> الرحمن الرحيم")
      } finally {
        cursor.close()
      }
    } finally {
      db.close()
      dbFile.delete()
    }
  }

  @Test
  fun `arabicSearcher multi match and suggestions cap`() {
    val dbFile = File(context.cacheDir, "multi_search.db")
    dbFile.parentFile?.mkdirs()
    dbFile.delete()
    val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      db.execSQL("CREATE TABLE verses(sura INTEGER, ayah INTEGER, text TEXT)")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (2, 255, 'الله لا إله إلا هو الحي القيوم والله عليم')")
      for (i in 1..10) {
        db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (3, $i, 'إن الله مع الصابرين $i')")
      }

      val searcher = ArabicSearcher(
        defaultSearcher = DefaultSearcher("<font color=\"-13388315\">", "</font>", "..."),
        matchStart = "<font color=\"-13388315\">",
        matchEnd = "</font>"
      )
      val columns = arrayOf("_id", "sura", "ayah", "text")
      val sql = searcher.getQuery(true, false, "verses", "rowid as _id, sura, ayah", "text")
      val likeArg = searcher.processSearchText("الله", false)

      val fullCursor = searcher.runQuery(db, sql, likeArg, "الله", true, columns)
      try {
        assertThat(fullCursor.count).isEqualTo(11)
        assertThat(fullCursor.moveToFirst()).isTrue()
        assertThat(fullCursor.getInt(1)).isEqualTo(2)
        assertThat(fullCursor.getInt(2)).isEqualTo(255)
        assertThat(fullCursor.getString(3)).isEqualTo(
          "<font color=\"-13388315\">الله</font> لا إله إلا هو الحي القيوم و<font color=\"-13388315\">الله</font> عليم"
        )
        assertThat(fullCursor.moveToNext()).isTrue()
        assertThat(fullCursor.getInt(1)).isEqualTo(3)
        assertThat(fullCursor.getInt(2)).isEqualTo(1)
        assertThat(fullCursor.getString(3)).isEqualTo(
          "إن <font color=\"-13388315\">الله</font> مع الصابرين 1"
        )
      } finally {
        fullCursor.close()
      }

      val suggestionsCursor = searcher.runQuery(db, sql, likeArg, "الله", false, columns)
      try {
        assertThat(suggestionsCursor.count).isEqualTo(6)
        assertThat(suggestionsCursor.moveToFirst()).isTrue()
        assertThat(suggestionsCursor.getString(3)).doesNotContain("<font")
      } finally {
        suggestionsCursor.close()
      }
    } finally {
      db.close()
      dbFile.delete()
    }
  }

  private fun createProvider(
    tafsirSearchHandler: ((String, String) -> Cursor?)? = null,
    searchHandler: (String, String, Boolean) -> Cursor?
  ): QuranDataProvider {
    val provider = object : QuranDataProvider() {
      override fun search(query: String, databaseName: String, wantSnippets: Boolean): Cursor? {
        return searchHandler(query, databaseName, wantSnippets)
      }

      override fun searchTafsirDatabase(query: String, databaseName: String): Cursor? {
        return tafsirSearchHandler?.invoke(query, databaseName)
          ?: super.searchTafsirDatabase(query, databaseName)
      }
    }
    provider.attachInfo(context, ProviderInfo().apply { authority = QuranDataProvider.SEARCH_URI.authority!! })
    provider.quranFileUtils = FakeQuranFileUtils.create(context)
    provider.translationsDBAdapter = FakeTranslationsDBAdapter()
    provider.quranInfo = QuranInfo(MadaniDataSource())
    provider.quranDisplayData = QuranDisplayData(provider.quranInfo)
    return provider
  }

  @Test
  fun `searchTafsir returns null when no translations are installed`() {
    val provider = createProvider { _, _, _ -> null }
    assertThat(provider.searchTafsir("الرحمن", emptyList())).isNull()
    assertThat(provider.getType(QuranDataProvider.SEARCH_TAFSIR_URI))
      .isEqualTo(QuranDataProvider.VERSES_MIME_TYPE)
  }

  @Test
  fun `searchTafsir prioritizes Arabic DBs on Arabic query, closes inner cursors, and survives SQLiteException`() {
    val queriedOrder = mutableListOf<Pair<String, String>>()
    val createdCursors = mutableListOf<MatrixCursor>()
    val provider = createProvider(
      tafsirSearchHandler = { sanitizedQuery, databaseName ->
        queriedOrder.add(databaseName to sanitizedQuery)
        if (databaseName == "broken_fts.db") {
          throw SQLiteException("malformed MATCH expression")
        }
        MatrixCursor(arrayOf("_id", "sura", "ayah", "text")).apply {
          if (databaseName == "ar_muyassar.db") {
            addRow(arrayOf<Any>(1L, 1, 1, "تفسير <font color=\"-1\">الرحمن</font>"))
          }
          createdCursors.add(this)
        }
      },
      searchHandler = { _, _, _ -> error("Standard search must not be called") }
    )

    val translations = listOf(
      LocalTranslation(id = 1, filename = "en_sahih.db", name = "Sahih", translator = "Sahih", languageCode = "en"),
      LocalTranslation(id = 2, filename = "broken_fts.db", name = "Broken", translator = "Broken", languageCode = "ar"),
      LocalTranslation(id = 3, filename = "ar_muyassar.db", name = "التفسير الميسر", translator = "الميسر", languageCode = "ar")
    )

    provider.searchTafsir("\"الرحمن*\"", translations)!!.use { result ->
      assertThat(result.count).isEqualTo(1)
      assertThat(result.moveToFirst()).isTrue()
      assertThat(result.getInt(1)).isEqualTo(1)
      assertThat(result.getInt(2)).isEqualTo(1)
      assertThat(result.getString(3)).contains("الرحمن")
      assertThat(result.getString(result.getColumnIndexOrThrow("translator_name"))).isEqualTo("الميسر")
      assertThat(result.getString(result.getColumnIndexOrThrow("language_code"))).isEqualTo("ar")
    }

    assertThat(queriedOrder).containsExactly(
      "broken_fts.db" to "الرحمن",
      "ar_muyassar.db" to "الرحمن",
      "en_sahih.db" to "الرحمن"
    ).inOrder()
    assertThat(createdCursors.all { it.isClosed }).isTrue()
  }

  @Test
  fun `searchTafsir catches lazy SQLiteException thrown during moveToNext and closes cursor`() {
    val backingCursor = MatrixCursor(arrayOf("_id", "sura", "ayah", "text"))
    val lazyThrowingCursor = object : CursorWrapper(backingCursor) {
      override fun moveToNext(): Boolean {
        throw SQLiteException("malformed MATCH expression: [-term*]")
      }
    }
    val provider = createProvider(
      tafsirSearchHandler = { _, _ -> lazyThrowingCursor },
      searchHandler = { _, _, _ -> error("Standard search must not be called") }
    )
    val translations = listOf(
      LocalTranslation(id = 1, filename = "lazy_broken.db", name = "Broken", translator = "Broken", languageCode = "en")
    )

    provider.searchTafsir("mercy", translations)!!.use { result ->
      assertThat(result.count).isEqualTo(0)
    }
    assertThat(lazyThrowingCursor.isClosed).isTrue()
  }

  @Test
  fun `searchTafsir FTS4 handles operator queries`() {
    val quranFileUtils = FakeQuranFileUtils.create(context)
    val dbDir = quranFileUtils.getQuranDatabaseDirectory()
    dbDir.mkdirs()
    val dbName = "fts4_reg.db"
    val dbFile = File(dbDir, dbName)
    dbFile.delete()
    DatabaseHandler.clearDatabaseHandlerIfExists(dbName)

    val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      db.execSQL("CREATE TABLE properties(property TEXT, value INTEGER)")
      db.execSQL("INSERT INTO properties(property, value) VALUES ('schema_version', 2)")
      db.execSQL("CREATE VIRTUAL TABLE verses USING fts4(sura INTEGER, ayah INTEGER, text TEXT)")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (1, 1, 'term mercy foo')")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (1, 2, 'beta bravo mercy')")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (2, 1, 'a b c')")
      db.close()

      val provider = createProvider(
        tafsirSearchHandler = null,
        searchHandler = { _, _, _ -> error("Standard search must not be called") }
      )
      val translations = listOf(
        LocalTranslation(id = 99L, filename = dbName, name = "Test Tafsir", translator = "Test", languageCode = "en")
      )

      assertThat(RustArabicSearchEngine.isLoaded()).isFalse()

      provider.searchTafsir("-term", translations)!!.use { c ->
        assertThat(c.count).isEqualTo(1)
        assertThat(c.moveToFirst()).isTrue()
        assertThat(c.getInt(1)).isEqualTo(1)
        assertThat(c.getInt(2)).isEqualTo(1)
      }

      provider.searchTafsir("OR b", translations)!!.use { c ->
        assertThat(c.count).isEqualTo(2)
      }

      provider.searchTafsir("a OR", translations)!!.use { c ->
        assertThat(c.count).isEqualTo(1)
        assertThat(c.moveToFirst()).isTrue()
        assertThat(c.getInt(1)).isEqualTo(2)
      }

      provider.searchTafsir("NOT mercy", translations)!!.use { c ->
        assertThat(c.count).isEqualTo(2)
      }

      provider.searchTafsir("OR", translations)!!.use { c ->
        assertThat(c.count).isEqualTo(0)
      }

      provider.searchTafsir("---", translations)!!.use { c ->
        assertThat(c.count).isEqualTo(0)
      }

      assertThat(RustArabicSearchEngine.isLoaded()).isFalse()
    } finally {
      if (db.isOpen) {
        db.close()
      }
      DatabaseHandler.clearDatabaseHandlerIfExists(dbName)
      dbFile.delete()
    }
  }

  @Test
  fun `searchTafsir unlimited normalized Arabic`() {
    val quranFileUtils = FakeQuranFileUtils.create(context)
    val dbDir = quranFileUtils.getQuranDatabaseDirectory().apply { mkdirs() }
    val db1Name = "ar1.db"
    val db2Name = "ar2.db"
    val db1File = File(dbDir, db1Name).apply { delete() }
    val db2File = File(dbDir, db2Name).apply { delete() }
    DatabaseHandler.clearDatabaseHandlerIfExists(db1Name)
    DatabaseHandler.clearDatabaseHandlerIfExists(db2Name)

    fun createArabicFts3Tafsir(file: File, rows: List<Triple<Int, Int, String>>) {
      SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
        db.execSQL("CREATE TABLE properties(property TEXT, value INTEGER)")
        db.execSQL("INSERT INTO properties(property, value) VALUES ('schema_version', 2)")
        db.execSQL("CREATE VIRTUAL TABLE verses USING fts3(sura INTEGER, ayah INTEGER, text TEXT)")
        db.beginTransaction()
        try {
          val stmt = db.compileStatement("INSERT INTO verses(sura, ayah, text) VALUES (?, ?, ?)")
          for ((sura, ayah, text) in rows) {
            stmt.clearBindings()
            stmt.bindLong(1, sura.toLong())
            stmt.bindLong(2, ayah.toLong())
            stmt.bindString(3, text)
            stmt.executeInsert()
          }
          db.setTransactionSuccessful()
        } finally {
          db.endTransaction()
        }
      }
    }

    try {
      val db1Rows = buildList {
        add(Triple(2, 25, "وَأَخْبِرْ -أَيُّهَا الرَّسُولُ- الَّذِينَ آمَنُوا بِاللَّهِ وَبِكَ وَأَقَامُوا الصَّلَاةَ وَالْإِيمَانِ لِمُوسَىٰ"))
        for (ayah in 1..179) {
          add(Triple(3, ayah, "تفسير الآية رقم $ayah وفيه ذكر بِاللَّهِ العلي العظيم"))
        }
      }
      val db2Rows = (1..40).map { ayah ->
        Triple(4, ayah, "تفسير السعدي للآية $ayah: إن اللَّهَ غفور رحيم")
      }
      createArabicFts3Tafsir(db1File, db1Rows)
      createArabicFts3Tafsir(db2File, db2Rows)

      val provider = createProvider(
        tafsirSearchHandler = null,
        searchHandler = { _, _, _ -> error("Standard search must not be called") }
      )
      val translations = listOf(
        LocalTranslation(id = 10L, filename = db1Name, name = "التفسير الميسر", translator = "الميسر", languageCode = "ar"),
        LocalTranslation(id = 20L, filename = db2Name, name = "تفسير السعدي", translator = "السعدي", languageCode = "ar")
      )

      assertThat(RustArabicSearchEngine.isLoaded()).isFalse()

      // Unlimited recall across BOTH tafsirs (180 + 40 = 220 > old 150 cap!)
      provider.searchTafsir("الله", translations)!!.use { c ->
        assertThat(c.count).isEqualTo(220)
        val translationIdIdx = c.getColumnIndexOrThrow("translation_id")
        var db1Count = 0
        var db2Count = 0
        while (c.moveToNext()) {
          when (c.getLong(translationIdIdx)) {
            10L -> db1Count++
            20L -> db2Count++
          }
        }
        assertThat(db1Count).isEqualTo(180)
        assertThat(db2Count).isEqualTo(40)
      }

      // Diacritic + Alef + Ta-Marbuta + Alef-Maqsura normalization on FTS3 tafsir
      for (normalizedQuery in listOf("الايمان", "الصلاة", "الصلاه", "ايها", "موسى", "موسي", "ايها الرسول")) {
        provider.searchTafsir(normalizedQuery, translations)!!.use { c ->
          assertThat(c.count).isEqualTo(1)
          assertThat(c.moveToFirst()).isTrue()
          assertThat(c.getInt(1)).isEqualTo(2)
          assertThat(c.getInt(2)).isEqualTo(25)
          assertThat(c.getString(3)).contains("<font color=")
          assertThat(c.getString(3)).contains("</font>")
        }
      }

      assertThat(RustArabicSearchEngine.isLoaded()).isFalse()
    } finally {
      DatabaseHandler.clearDatabaseHandlerIfExists(db1Name)
      DatabaseHandler.clearDatabaseHandlerIfExists(db2Name)
      db1File.delete()
      db2File.delete()
    }
  }
}
