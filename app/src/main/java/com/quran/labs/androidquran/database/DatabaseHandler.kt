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
        if (isArabicIgnorable(ch)) continue
        if (ch.isWhitespace()) {
          if (!prevSpace && sb.isNotEmpty()) {
            sb.append(' ')
            prevSpace = true
          }
          continue
        }
        sb.append(foldArabicChar(ch))
        prevSpace = false
      }
      if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
        sb.setLength(sb.length - 1)
      }
      return sb.toString()
    }

    private fun isArabicIgnorable(ch: Char): Boolean {
      val code = ch.code
      return (code in 0x064B..0x065F) ||
        code == 0x0670 || // Superscript Alef (dagger alif)
        code == 0x0640 || // Tatweel / Kashida
        (code in 0x0610..0x061A) || // Arabic honorifics/signs
        (code in 0x06D6..0x06ED) || // Quranic annotation marks
        code == 0x061C || // Arabic Letter Mark
        code == 0x200C || // ZWNJ
        code == 0x200D    // ZWJ
    }

    private fun foldArabicChar(ch: Char): Char {
      return when (ch) {
        '\u0622', '\u0623', '\u0625', '\u0671', '\u0672', '\u0673' -> '\u0627' // آ أ إ ٱ ٲ ٳ -> ا
        '\u0629' -> '\u0647' // ة -> ه
        '\u0649', '\u06CC', '\u0626' -> '\u064A' // ى ی ئ -> ي
        '\u0624' -> '\u0648' // ؤ -> و
        else -> ch
      }
    }

    private fun normalizeIntoBuffer(
      text: String,
      normBuf: CharArray,
      origStartBuf: IntArray,
      origEndBuf: IntArray
    ): Int {
      var normLen = 0
      var prevWasSpace = true
      val len = text.length
      var i = 0
      while (i < len) {
        val ch = text[i]
        if (isArabicIgnorable(ch)) {
          if (normLen > 0 && !prevWasSpace) {
            origEndBuf[normLen - 1] = i + 1
          }
          i++
          continue
        }
        if (ch.isWhitespace()) {
          if (!prevWasSpace && normLen > 0) {
            normBuf[normLen] = ' '
            origStartBuf[normLen] = i
            origEndBuf[normLen] = i + 1
            normLen++
            prevWasSpace = true
          } else if (normLen > 0 && prevWasSpace) {
            origEndBuf[normLen - 1] = i + 1
          }
          i++
          continue
        }
        normBuf[normLen] = foldArabicChar(ch)
        origStartBuf[normLen] = i
        origEndBuf[normLen] = i + 1
        normLen++
        prevWasSpace = false
        i++
      }
      if (normLen > 0 && normBuf[normLen - 1] == ' ') {
        normLen--
      }
      return normLen
    }

    private fun indexOfChars(
      haystack: CharArray,
      haystackLen: Int,
      needle: CharArray,
      fromIndex: Int
    ): Int {
      val needleLen = needle.size
      if (needleLen == 0) return -1
      val maxStart = haystackLen - needleLen
      if (fromIndex > maxStart) return -1
      val first = needle[0]
      var i = if (fromIndex < 0) 0 else fromIndex
      while (i <= maxStart) {
        if (haystack[i] == first) {
          var j = 1
          while (j < needleLen && haystack[i + j] == needle[j]) {
            j++
          }
          if (j == needleLen) return i
        }
        i++
      }
      return -1
    }

    private fun sortAndMergeRanges(starts: ArrayList<Int>, ends: ArrayList<Int>) {
      val n = starts.size
      if (n <= 1) return
      // Insertion sort (typically very few matches per verse)
      for (i in 1 until n) {
        val keyS = starts[i]
        val keyE = ends[i]
        var j = i - 1
        while (j >= 0 && (starts[j] > keyS || (starts[j] == keyS && ends[j] > keyE))) {
          starts[j + 1] = starts[j]
          ends[j + 1] = ends[j]
          j--
        }
        starts[j + 1] = keyS
        ends[j + 1] = keyE
      }
      var write = 0
      for (i in 1 until n) {
        if (starts[i] <= ends[write]) {
          if (ends[i] > ends[write]) {
            ends[write] = ends[i]
          }
        } else {
          write++
          starts[write] = starts[i]
          ends[write] = ends[i]
        }
      }
      while (starts.size > write + 1) {
        starts.removeAt(starts.size - 1)
        ends.removeAt(ends.size - 1)
      }
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
   * Scans [table] for normalized Arabic matches (diacritic-insensitive, Alef/Hamza/Ta-Marbuta/Ya
   * folded, supporting exact phrase and multi-token matching) without touching
   * [RustArabicSearchEngine]. Returns ALL matching rows with clean highlighted context snippets.
   */
  fun searchArabicNormalized(q: String, table: String = VERSE_TABLE): Cursor? {
    if (!validDatabase()) return null
    val columns = arrayOf(BaseColumns._ID, COL_SURA, COL_AYAH, COL_TEXT)
    val normalizedQuery = normalizeArabicQuery(q)
    if (normalizedQuery.isEmpty()) {
      return MatrixCursor(columns)
    }

    val phraseChars = normalizedQuery.toCharArray()
    val tokens = normalizedQuery.split(' ').filter { it.isNotEmpty() }.distinct()
    val tokenChars = Array(tokens.size) { tokens[it].toCharArray() }
    val resultCursor = MatrixCursor(columns)

    var normBuf = CharArray(512)
    var origStartBuf = IntArray(512)
    var origEndBuf = IntArray(512)
    val matchStarts = ArrayList<Int>(8)
    val matchEnds = ArrayList<Int>(8)
    val snippetBuilder = StringBuilder(320)

    val sql =
      "SELECT rowid as ${BaseColumns._ID}, $COL_SURA, $COL_AYAH, $COL_TEXT FROM $table ORDER BY $COL_SURA, $COL_AYAH"
    try {
      database?.rawQuery(sql, null)?.use { cursor ->
        while (cursor.moveToNext()) {
          val rawText = cursor.getString(3) ?: continue
          if (rawText.isEmpty()) continue
          val cleanText = if (rawText.indexOf('<') >= 0 || rawText.indexOf('&') >= 0) {
            cleanTafsirHtml(rawText)
          } else {
            rawText
          }
          if (cleanText.isEmpty()) continue

          val cleanLen = cleanText.length
          if (normBuf.size < cleanLen) {
            val newSize = maxOf(cleanLen, normBuf.size * 2)
            normBuf = CharArray(newSize)
            origStartBuf = IntArray(newSize)
            origEndBuf = IntArray(newSize)
          }

          val normLen = normalizeIntoBuffer(cleanText, normBuf, origStartBuf, origEndBuf)
          if (normLen == 0) continue

          val phraseIdx = indexOfChars(normBuf, normLen, phraseChars, 0)
          val usePhrase = phraseIdx >= 0
          if (!usePhrase) {
            if (tokenChars.size <= 1) continue
            var allFound = true
            for (t in tokenChars) {
              if (indexOfChars(normBuf, normLen, t, 0) < 0) {
                allFound = false
                break
              }
            }
            if (!allFound) continue
          }

          matchStarts.clear()
          matchEnds.clear()
          if (usePhrase) {
            var pos = phraseIdx
            val pLen = phraseChars.size
            while (pos >= 0) {
              matchStarts.add(origStartBuf[pos])
              matchEnds.add(origEndBuf[pos + pLen - 1])
              pos = indexOfChars(normBuf, normLen, phraseChars, pos + pLen)
            }
          } else {
            for (t in tokenChars) {
              val tLen = t.size
              var pos = indexOfChars(normBuf, normLen, t, 0)
              while (pos >= 0) {
                matchStarts.add(origStartBuf[pos])
                matchEnds.add(origEndBuf[pos + tLen - 1])
                pos = indexOfChars(normBuf, normLen, t, pos + tLen)
              }
            }
            sortAndMergeRanges(matchStarts, matchEnds)
          }

          if (matchStarts.isEmpty()) continue

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
