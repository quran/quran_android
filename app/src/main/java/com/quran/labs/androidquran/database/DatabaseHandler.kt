package com.quran.labs.androidquran.database

import android.content.Context
import android.database.Cursor
import android.database.DefaultDatabaseErrorHandler
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.provider.BaseColumns
import android.util.SparseArray
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import com.quran.common.search.ArabicSearcher
import com.quran.common.search.DefaultSearcher
import com.quran.common.search.Searcher
import com.quran.data.model.QuranText
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranFileConstants
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.TranslationUtil
import timber.log.Timber
import java.io.File

class DatabaseHandler private constructor(
  context: Context,
  databaseName: String,
  quranFileUtils: QuranFileUtils
) {
  private var schemaVersion = 1
  private var database: SQLiteDatabase? = null

  private val defaultSearcher: Searcher
  private val arabicSearcher: Searcher

  companion object {
    private const val COL_SURA = "sura"
    private const val COL_AYAH = "ayah"
    private const val COL_TEXT = "text"

    private const val VERSE_TABLE = "verses"
    const val ARABIC_TEXT_TABLE = "arabic_text"
    const val SHARE_TEXT_TABLE = "share_text"
    private const val PROPERTIES_TABLE = "properties"

    private const val COL_PROPERTY = "property"
    private const val COL_VALUE = "value"
    private const val MATCH_END = "</font>"
    private const val ELLIPSES = "<b>...</b>"

    private val databaseMap: MutableMap<String, DatabaseHandler> = HashMap()

    @JvmStatic
    @Synchronized
    fun getDatabaseHandler(
      context: Context, databaseName: String, quranFileUtils: QuranFileUtils
    ): DatabaseHandler {
      var handler = databaseMap[databaseName]
      if (handler == null) {
        handler = DatabaseHandler(context.applicationContext, databaseName, quranFileUtils)
        databaseMap[databaseName] = handler
      }
      return handler
    }

    @JvmStatic
    @Synchronized
    fun clearDatabaseHandlerIfExists(databaseName: String) {
      try {
        val handler = databaseMap.remove(databaseName)
        if (handler != null) {
          handler.database?.close()
          databaseMap.remove(databaseName)
        }
      } catch (e: Exception) {
        Timber.e(e)
      }
    }
  }

  init {
    // initialize the searchers first
    val matchString = "<font color=\"" +
        ContextCompat.getColor(context, R.color.translation_highlight) +
        "\">"
    defaultSearcher = DefaultSearcher(matchString, MATCH_END, ELLIPSES)
    arabicSearcher = ArabicSearcher(defaultSearcher, matchString, MATCH_END)

    // if there's no Quran base directory, there are no databases
    val base = quranFileUtils.getQuranDatabaseDirectory(context)
    base?.let {
      val path = base + File.separator + databaseName
      Timber.d("opening database file: %s", path)
      database = try {
        SQLiteDatabase.openDatabase(path, null,
          SQLiteDatabase.NO_LOCALIZED_COLLATORS, DefaultDatabaseErrorHandler())
      } catch (sce: SQLiteDatabaseCorruptException) {
        Timber.d("corrupt database: %s", databaseName)
        throw sce
      } catch (se: SQLException) {
        Timber.d("database at $path ${if (File(path).exists()) "exists " else "doesn 't exist"}")
        throw se
      }

      schemaVersion = getSchemaVersion()
    }
  }

  @Retention(AnnotationRetention.SOURCE)
  @IntDef(TextType.ARABIC, TextType.TRANSLATION)
  annotation class TextType {
    companion object {
      const val ARABIC = 0
      const val TRANSLATION = 1
    }
  }

  fun validDatabase(): Boolean = database?.isOpen ?: false

  private fun getProperty(column: String): Int {
    var value = 1
    if (!validDatabase()) return value

    var cursor: Cursor? = null
    return try {
      cursor = database?.query(PROPERTIES_TABLE, arrayOf(COL_VALUE),
        "$COL_PROPERTY= ?", arrayOf(column), null, null, null)
      if (cursor != null && cursor.moveToFirst()) {
        value = cursor.getInt(0)
      }
      value
    } catch (se: SQLException) {
      value
    } finally {
      DatabaseUtils.closeCursor(cursor)
    }
  }

  fun getSchemaVersion(): Int = getProperty("schema_version")

  fun getTextVersion(): Int = getProperty("text_version")

  private fun getVerses(sura: Int, minAyah: Int, maxAyah: Int, table: String): Cursor? {
    return getVerses(sura, minAyah, sura, maxAyah, table)
  }

  /**
   * @param minSura start sura
   * @param minAyah start ayah
   * @param maxSura end sura
   * @param maxAyah end ayah
   * @param table the table
   * @return a Cursor with the data
   */
  @Deprecated(
    """use {@link #getVerses(VerseRange, int)} instead
   
    """
  )
  fun getVerses(
    minSura: Int, minAyah: Int, maxSura: Int, maxAyah: Int, table: String
  ): Cursor? {
    // pass -1 for verses since this is used internally only and the field isn't needed.
    return getVersesInternal(VerseRange(minSura, minAyah, maxSura, maxAyah, -1), table)
  }

  fun getVerses(verses: VerseRange, @TextType textType: Int): List<QuranText> {
    var cursor: Cursor? = null
    val results: MutableList<QuranText> = ArrayList()
    val toLookup: MutableSet<Int> = HashSet()

    val table = if (textType == TextType.ARABIC) ARABIC_TEXT_TABLE else VERSE_TABLE
    try {
      cursor = getVersesInternal(verses, table)
      while (cursor != null && cursor.moveToNext()) {
        val sura = cursor.getInt(1)
        val ayah = cursor.getInt(2)
        val text = cursor.getString(3)

        val quranText = QuranText(sura, ayah, text, null)
        results.add(quranText)

        val hyperlinkId = TranslationUtil.getHyperlinkAyahId(quranText)
        if (hyperlinkId != null) {
          toLookup.add(hyperlinkId)
        }
      }
    } finally {
      DatabaseUtils.closeCursor(cursor)
    }

    var didWrite = false
    if (toLookup.isNotEmpty()) {
      val toExpandBuilder = StringBuilder()
      for (id in toLookup) {
        if (didWrite) {
          toExpandBuilder.append(",")
        } else {
          didWrite = true
        }
        toExpandBuilder.append(id)
      }
      return expandHyperlinks(table, results, toExpandBuilder.toString())
    }
    return results
  }

  private fun expandHyperlinks(
    table: String, data: List<QuranText>, rowIds: String
  ): List<QuranText> {
    val expansions = SparseArray<String>()

    var cursor: Cursor? = null
    try {
      cursor = database?.query(table, arrayOf("rowid as _id", COL_TEXT),
        "rowid in ($rowIds)", null, null, null, "rowid")
      while (cursor != null && cursor.moveToNext()) {
        val id = cursor.getInt(0)
        val text = cursor.getString(1)
        expansions.put(id, text)
      }
    } finally {
      DatabaseUtils.closeCursor(cursor)
    }

    val result: MutableList<QuranText> = ArrayList()
    for (ayah in data) {
      val linkId = TranslationUtil.getHyperlinkAyahId(ayah)
      if (linkId == null) {
        result.add(ayah)
      } else {
        val expandedText = expansions[linkId]
        result.add(QuranText(ayah.sura, ayah.ayah, ayah.text, expandedText))
      }
    }
    return result
  }

  private fun getVersesInternal(verses: VerseRange, table: String): Cursor? {
    if (!validDatabase()) return null

    val whereQuery = StringBuilder()
    whereQuery.append("(")

    if (verses.startSura == verses.endingSura) {
      whereQuery.append(COL_SURA)
        .append("=").append(verses.startSura)
        .append(" and ").append(COL_AYAH)
        .append(">=").append(verses.startAyah)
        .append(" and ").append(COL_AYAH)
        .append("<=").append(verses.endingAyah)
    } else {
      // (sura = minSura and ayah >= minAyah)
      whereQuery.append("(").append(COL_SURA).append("=")
        .append(verses.startSura).append(" and ")
        .append(COL_AYAH).append(">=").append(verses.startAyah).append(")")

      whereQuery.append(" or ")

      // (sura = maxSura and ayah <= maxAyah)
      whereQuery.append("(").append(COL_SURA).append("=")
        .append(verses.endingSura).append(" and ")
        .append(COL_AYAH).append("<=").append(verses.endingAyah).append(")")

      whereQuery.append(" or ")

      // (sura > minSura and sura < maxSura)
      whereQuery.append("(").append(COL_SURA).append(">")
        .append(verses.startSura).append(" and ")
        .append(COL_SURA).append("<")
        .append(verses.endingSura).append(")")
    }

    whereQuery.append(")")

    return database?.query(
      table, arrayOf("rowid as _id", COL_SURA, COL_AYAH, COL_TEXT),
      whereQuery.toString(), null, null, null,
      "$COL_SURA,$COL_AYAH"
    )
  }

  fun getVersesByIds(ids: List<Int>): Cursor? {
    val builder = StringBuilder()
    for (i in ids.indices) {
      if (i > 0) {
        builder.append(",")
      }
      builder.append(ids[i])
    }

    Timber.d("querying verses by ids for tags...")
    val sql = "SELECT rowid as _id, " + COL_SURA + ", " + COL_AYAH + ", " + COL_TEXT +
        " FROM " + QuranFileConstants.ARABIC_SHARE_TABLE +
        " WHERE rowid in(" + builder.toString() + ")"
    return database?.rawQuery(sql, null)
  }

  fun search(query: String, withSnippets: Boolean, isArabicDatabase: Boolean): Cursor? {
    return search(query, VERSE_TABLE, withSnippets, isArabicDatabase)
  }

  fun search(q: String, table: String, withSnippets: Boolean, isArabicDatabase: Boolean): Cursor? {
    if (!validDatabase()) return null

    var searchText = q
    var pos = 0
    var found = 0
    var done = false
    while (!done) {
      val quote = searchText.indexOf("\"", pos)
      if (quote > -1) {
        found++
        pos = quote + 1
      } else {
        done = true
      }
    }

    if (found % 2 != 0) {
      searchText = searchText.replace("\"".toRegex(), "")
    }

    val searcher: Searcher = if (isArabicDatabase) arabicSearcher else defaultSearcher

    val useFullTextIndex = schemaVersion > 1 && !isArabicDatabase
    val qtext = searcher.getQuery(withSnippets, useFullTextIndex, table,
      "rowid as " + BaseColumns._ID + ", " + COL_SURA + ", " + COL_AYAH, COL_TEXT
    ) +
        " " + searcher.getLimit(withSnippets)
    searchText = searcher.processSearchText(searchText, useFullTextIndex)
    Timber.d("search query: $qtext, query: $searchText")

    val columns = arrayOf(BaseColumns._ID, COL_SURA, COL_AYAH, COL_TEXT)
    return try {
      searcher.runQuery(database!!, qtext, searchText, q, withSnippets, columns)
    } catch (e: Exception) {
      Timber.e(e)
      null
    }
  }
}
