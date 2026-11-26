package com.quran.labs.androidquran.database

import android.content.Context
import android.database.Cursor
import android.database.DefaultDatabaseErrorHandler
import android.database.MatrixCursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.provider.BaseColumns
import android.util.SparseArray
import androidx.annotation.IntDef
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.quran.common.search.ArabicSearcher
import com.quran.common.search.DefaultSearcher
import com.quran.common.search.RustArabicSearchEngine
import com.quran.common.search.Searcher
import com.quran.common.search.arabic.ArabicMorphologicalQuery
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

  private val matchStart: String
  private val defaultSearcher: Searcher
  private val arabicSearcher: ArabicSearcher

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
    private const val SNIPPET_TARGET_LEN = 220
    private const val SNIPPET_MAX_FULL_LEN = 260
    private const val WORD_BOUNDARY_LOOKAHEAD = 28

    private val databaseMap: MutableMap<String, DatabaseHandler> = HashMap()

    @VisibleForTesting
    internal fun normalizeArabicQuery(query: String): String {
      if (query.isEmpty()) return ""
      val sb = StringBuilder(query.length)
      var prevSpace = true
      for (i in query.indices) {
        val ch = query[i]
        if (ArabicMorphologicalQuery.isArabicIgnorable(ch)) continue
        if (ch.isWhitespace()) {
          if (!prevSpace && sb.isNotEmpty()) {
            sb.append(' ')
            prevSpace = true
          }
          continue
        }
        val folded = ArabicMorphologicalQuery.foldArabicLetter(ch)
        sb.append(if (folded == '\u0629') '\u0647' else folded)
        prevSpace = false
      }
      if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
        sb.setLength(sb.length - 1)
      }
      return sb.toString()
    }

    private fun isPureNumericHyperlink(text: String): Boolean {
      if (text.isEmpty() || text.length > 10) return false
      for (i in text.indices) {
        val ch = text[i]
        if (ch !in '0'..'9') return false
      }
      return true
    }

    private fun cleanTafsirHtml(raw: String): String {
      val len = raw.length
      val sb = StringBuilder(len)
      var i = 0
      while (i < len) {
        val ch = raw[i]
        if (ch == '<') {
          val gt = raw.indexOf('>', i + 1)
          if (gt > i) {
            // Insert space for block/line-break tags so adjacent words stay separated
            val isBreakTag = raw.regionMatches(i + 1, "br", 0, 2, ignoreCase = true) ||
              raw.regionMatches(i + 1, "/p", 0, 2, ignoreCase = true) ||
              raw.regionMatches(i + 1, "/div", 0, 4, ignoreCase = true) ||
              raw.regionMatches(i + 1, "/li", 0, 3, ignoreCase = true)
            if (isBreakTag && sb.isNotEmpty() && !sb[sb.length - 1].isWhitespace()) {
              sb.append(' ')
            }
            i = gt + 1
            continue
          }
        } else if (ch == '&') {
          if (raw.regionMatches(i, "&nbsp;", 0, 6, ignoreCase = true)) {
            sb.append(' ')
            i += 6
            continue
          } else if (raw.regionMatches(i, "&quot;", 0, 6, ignoreCase = true)) {
            sb.append('"')
            i += 6
            continue
          } else if (raw.regionMatches(i, "&#39;", 0, 5)) {
            sb.append('\'')
            i += 5
            continue
          }
        }
        sb.append(ch)
        i++
      }
      return sb.toString()
    }

    private fun appendCollapsed(
      out: StringBuilder,
      text: String,
      from: Int,
      to: Int,
      trimLeadingSpace: Boolean
    ) {
      var i = from
      var lastWasSpace = out.isEmpty() || out[out.length - 1] == ' '
      if (trimLeadingSpace) {
        while (i < to && text[i].isWhitespace()) i++
      }
      while (i < to) {
        val ch = text[i]
        if (ch.isWhitespace()) {
          if (!lastWasSpace) {
            out.append(' ')
            lastWasSpace = true
          }
        } else {
          out.append(ch)
          lastWasSpace = false
        }
        i++
      }
    }

    private fun buildHighlightedSnippet(
      text: String,
      matchStarts: ArrayList<Int>,
      matchEnds: ArrayList<Int>,
      matchStartTag: String,
      matchEndTag: String,
      buffer: StringBuilder
    ): String {
      val len = text.length
      val firstStart = matchStarts[0]
      val firstEnd = matchEnds[0]
      var winStart: Int
      var winEnd: Int
      if (len <= SNIPPET_MAX_FULL_LEN) {
        winStart = 0
        winEnd = len
      } else {
        val matchLen = firstEnd - firstStart
        val targetWindow = maxOf(SNIPPET_TARGET_LEN, matchLen)
        val center = firstStart + matchLen / 2
        val rawStart = (center - targetWindow / 2).coerceIn(0, firstStart)
        var rawEnd = (rawStart + targetWindow).coerceAtMost(len)
        if (rawEnd < firstEnd) {
          rawEnd = firstEnd.coerceAtMost(len)
        }
        winStart = if (rawEnd == len) {
          (rawEnd - targetWindow).coerceIn(0, firstStart)
        } else {
          rawStart
        }
        winEnd = rawEnd

        if (winStart > 0) {
          var forward = winStart
          val maxForward = minOf(firstStart, winStart + WORD_BOUNDARY_LOOKAHEAD)
          while (forward < maxForward && !text[forward].isWhitespace()) {
            forward++
          }
          if (forward < maxForward) {
            while (forward < firstStart && text[forward].isWhitespace()) {
              forward++
            }
            winStart = forward
          } else {
            var backward = winStart
            val minBackward = maxOf(0, winStart - WORD_BOUNDARY_LOOKAHEAD)
            while (backward > minBackward && !text[backward - 1].isWhitespace()) {
              backward--
            }
            winStart = backward
          }
        }

        if (winEnd < len) {
          var backward = winEnd
          val minBackward = maxOf(firstEnd, winEnd - WORD_BOUNDARY_LOOKAHEAD)
          while (backward > minBackward && !text[backward - 1].isWhitespace()) {
            backward--
          }
          if (backward > minBackward) {
            while (backward > firstEnd && text[backward - 1].isWhitespace()) {
              backward--
            }
            winEnd = backward
          } else {
            var forward = winEnd
            val maxForward = minOf(len, winEnd + WORD_BOUNDARY_LOOKAHEAD)
            while (forward < maxForward && !text[forward].isWhitespace()) {
              forward++
            }
            winEnd = forward
          }
        }
      }

      buffer.setLength(0)
      if (winStart > 0) {
        buffer.append("... ")
      }
      var cursor = winStart
      for (k in matchStarts.indices) {
        val ms = matchStarts[k]
        val me = matchEnds[k]
        if (me <= cursor) continue
        if (ms >= winEnd) break
        val clampedStart = maxOf(ms, cursor)
        val clampedEnd = minOf(me, winEnd)
        if (clampedStart > cursor) {
          appendCollapsed(buffer, text, cursor, clampedStart, trimLeadingSpace = (cursor == winStart))
        }
        buffer.append(matchStartTag)
        appendCollapsed(buffer, text, clampedStart, clampedEnd, trimLeadingSpace = false)
        buffer.append(matchEndTag)
        cursor = clampedEnd
      }
      if (cursor < winEnd) {
        appendCollapsed(buffer, text, cursor, winEnd, trimLeadingSpace = (cursor == winStart))
      }
      while (buffer.isNotEmpty() && buffer[buffer.length - 1] == ' ') {
        buffer.setLength(buffer.length - 1)
      }
      if (winEnd < len) {
        buffer.append(" ...")
      }
      return buffer.toString()
    }

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
        if (databaseName == QuranFileConstants.ARABIC_DATABASE) {
          RustArabicSearchEngine.releaseIndex()
        }
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
    matchStart = "<font color=\"" +
        ContextCompat.getColor(context, R.color.translation_highlight) +
        "\">"
    defaultSearcher = DefaultSearcher(matchStart, MATCH_END, ELLIPSES)
    arabicSearcher = ArabicSearcher(defaultSearcher, matchStart, MATCH_END)

    // if there's no Quran base directory, there are no databases
    val base = quranFileUtils.getQuranDatabaseDirectory()
    val baseCanonical = base.canonicalPath
    val basePrefix = if (baseCanonical.endsWith(File.separator)) {
      baseCanonical
    } else {
      baseCanonical + File.separator
    }
    val dbFile = File(base, databaseName)
    val path = dbFile.canonicalPath
    require(!databaseName.contains('/') && !databaseName.contains('\\') && path.startsWith(basePrefix)) {
      "Invalid database name"
    }
    Timber.d("opening database file: %s", dbFile.name)
    database = try {
      SQLiteDatabase.openDatabase(
        path,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        DefaultDatabaseErrorHandler()
      ).also { db ->
        try {
          db.execSQL("PRAGMA cache_size = -512")
        } catch (_: Exception) {
          // keep default cache size if PRAGMA is unsupported
        }
      }
    } catch (sce: SQLiteDatabaseCorruptException) {
      Timber.d("corrupt database: %s", dbFile.name)
      throw sce
    } catch (se: SQLException) {
      Timber.d("database ${dbFile.name} ${if (dbFile.exists()) "exists" else "doesn't exist"}")
      throw se
    }

    schemaVersion = getSchemaVersion()
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

    val whereQuery: String
    val whereArgs: Array<String>

    if (verses.startSura == verses.endingSura) {
      whereQuery =
        "($COL_SURA = CAST(? AS INTEGER) and $COL_AYAH >= CAST(? AS INTEGER) and $COL_AYAH <= CAST(? AS INTEGER))"
      whereArgs = arrayOf(
        verses.startSura.toString(),
        verses.startAyah.toString(),
        verses.endingAyah.toString()
      )
    } else {
      whereQuery =
        "(($COL_SURA = CAST(? AS INTEGER) and $COL_AYAH >= CAST(? AS INTEGER)) or ($COL_SURA = CAST(? AS INTEGER) and $COL_AYAH <= CAST(? AS INTEGER)) or ($COL_SURA > CAST(? AS INTEGER) and $COL_SURA < CAST(? AS INTEGER)))"
      whereArgs = arrayOf(
        verses.startSura.toString(),
        verses.startAyah.toString(),
        verses.endingSura.toString(),
        verses.endingAyah.toString(),
        verses.startSura.toString(),
        verses.endingSura.toString()
      )
    }

    return database?.query(
      table, arrayOf("rowid as _id", COL_SURA, COL_AYAH, COL_TEXT),
      whereQuery, whereArgs, null, null,
      "$COL_SURA,$COL_AYAH"
    )
  }

  fun getVersesByIds(ids: List<Int>): Cursor? {
    if (!validDatabase() || ids.isEmpty()) {
      return null
    }
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

  fun searchRawArabic(query: String): Cursor? {
    return searchInternal(query, VERSE_TABLE, withSnippets = true, isArabicDatabase = true, rawArabic = true)
  }

  fun search(q: String, table: String, withSnippets: Boolean, isArabicDatabase: Boolean): Cursor? {
    return searchInternal(q, table, withSnippets, isArabicDatabase, rawArabic = false)
  }

  /**
   * Scans [table] for comprehensive Arabic matches (full orthographic normalization, proclitics,
   * `لِـ+ال` elisions like `لله`/`للجنة`/`للناس`, Uthmani open-Ta, ranked singular/dual/plural
   * morphology, and multi-token topical matching) without touching [RustArabicSearchEngine].
   * Returns ALL matching rows ranked Tier 1 (exact) -> Tier 2 (singular/possessive) -> Tier 3
   * (dual/plural/topical) with clean highlighted context snippets.
   */
  fun searchArabicNormalized(q: String, table: String = VERSE_TABLE): Cursor? {
    if (!validDatabase()) return null
    val columns = arrayOf(BaseColumns._ID, COL_SURA, COL_AYAH, COL_TEXT)
    val matcher = ArabicMorphologicalQuery.compile(q)
      ?: return MatrixCursor(columns)

    val resultCursor = MatrixCursor(columns)
    val matchStarts = ArrayList<Int>(8)
    val matchEnds = ArrayList<Int>(8)
    val snippetBuilder = StringBuilder(320)
    val tier1Rows = ArrayList<Array<Any>>(64)
    val tier2Rows = ArrayList<Array<Any>>(32)
    val tier3Rows = ArrayList<Array<Any>>(32)
    val isWordLevel = matcher.isWordLevel()

    val sql =
      "SELECT rowid as ${BaseColumns._ID}, $COL_SURA, $COL_AYAH, $COL_TEXT FROM $table ORDER BY $COL_SURA, $COL_AYAH"
    try {
      database?.rawQuery(sql, null)?.use { cursor ->
        while (cursor.moveToNext()) {
          val rawText = cursor.getString(3) ?: continue
          if (rawText.isEmpty() || isPureNumericHyperlink(rawText)) continue
          val cleanText = if (rawText.indexOf('<') >= 0 || rawText.indexOf('&') >= 0) {
            cleanTafsirHtml(rawText)
          } else {
            rawText
          }
          if (cleanText.isEmpty()) continue

          val tier = matcher.matchAndCollectSpans(
            text = cleanText,
            startsOut = matchStarts,
            endsOut = matchEnds,
            allowNonContiguousMultiWord = true,
            allowSubstringFallback = !isWordLevel
          )
          if (tier == ArabicMorphologicalQuery.NO_MATCH || matchStarts.isEmpty()) continue

          val snippet = buildHighlightedSnippet(
            text = cleanText,
            matchStarts = matchStarts,
            matchEnds = matchEnds,
            matchStartTag = matchStart,
            matchEndTag = MATCH_END,
            buffer = snippetBuilder
          )
          val row = arrayOf<Any>(
            cursor.getLong(0),
            cursor.getInt(1),
            cursor.getInt(2),
            snippet
          )
          when (tier) {
            ArabicMorphologicalQuery.TIER_EXACT -> tier1Rows.add(row)
            ArabicMorphologicalQuery.TIER_SINGULAR_MORPH -> tier2Rows.add(row)
            else -> tier3Rows.add(row)
          }
        }
      }

      if (tier1Rows.isEmpty() && tier2Rows.isEmpty() && tier3Rows.isEmpty() && isWordLevel) {
        // Mid-typing prefix / partial-word fallback so live typing in Tafsir never drops to 0 hits
        database?.rawQuery(sql, null)?.use { cursor ->
          while (cursor.moveToNext()) {
            val rawText = cursor.getString(3) ?: continue
            if (rawText.isEmpty() || isPureNumericHyperlink(rawText)) continue
            val cleanText = if (rawText.indexOf('<') >= 0 || rawText.indexOf('&') >= 0) {
              cleanTafsirHtml(rawText)
            } else {
              rawText
            }
            if (cleanText.isEmpty()) continue

            val tier = matcher.matchAndCollectSpans(
              text = cleanText,
              startsOut = matchStarts,
              endsOut = matchEnds,
              allowNonContiguousMultiWord = false,
              allowSubstringFallback = true
            )
            if (tier == ArabicMorphologicalQuery.NO_MATCH || matchStarts.isEmpty()) continue

            val snippet = buildHighlightedSnippet(
              text = cleanText,
              matchStarts = matchStarts,
              matchEnds = matchEnds,
              matchStartTag = matchStart,
              matchEndTag = MATCH_END,
              buffer = snippetBuilder
            )
            resultCursor.addRow(
              arrayOf<Any>(
                cursor.getLong(0),
                cursor.getInt(1),
                cursor.getInt(2),
                snippet
              )
            )
          }
        }
        return resultCursor
      }

      for (row in tier1Rows) resultCursor.addRow(row)
      for (row in tier2Rows) resultCursor.addRow(row)
      for (row in tier3Rows) resultCursor.addRow(row)
    } catch (e: Exception) {
      Timber.e(e)
      resultCursor.close()
      return null
    }
    return resultCursor
  }

  private fun searchInternal(
    q: String,
    table: String,
    withSnippets: Boolean,
    isArabicDatabase: Boolean,
    rawArabic: Boolean
  ): Cursor? {
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
    Timber.d("search query length: %d, fts: %b", searchText.length, useFullTextIndex)

    val columns = arrayOf(BaseColumns._ID, COL_SURA, COL_AYAH, COL_TEXT)
    return try {
      if (rawArabic && isArabicDatabase) {
        arabicSearcher.runQueryRaw(database!!, qtext, searchText, q, columns)
      } else {
        searcher.runQuery(database!!, qtext, searchText, q, withSnippets, columns)
      }
    } catch (e: Exception) {
      Timber.e(e)
      null
    }
  }
}
