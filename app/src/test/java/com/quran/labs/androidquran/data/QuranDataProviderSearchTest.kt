package com.quran.labs.androidquran.data

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.common.search.ArabicSearcher
import com.quran.common.search.DefaultSearcher
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.base.TestApplication
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
    searchHandler: (String, String, Boolean) -> Cursor?
  ): QuranDataProvider {
    val provider = object : QuranDataProvider() {
      override fun search(query: String, databaseName: String, wantSnippets: Boolean): Cursor? {
        return searchHandler(query, databaseName, wantSnippets)
      }
    }
    provider.attachInfo(context, ProviderInfo().apply { authority = "com.quran.test" })
    provider.quranFileUtils = FakeQuranFileUtils.create(context)
    provider.translationsDBAdapter = FakeTranslationsDBAdapter()
    provider.quranInfo = QuranInfo(MadaniDataSource())
    provider.quranDisplayData = QuranDisplayData(provider.quranInfo)
    return provider
  }
}
