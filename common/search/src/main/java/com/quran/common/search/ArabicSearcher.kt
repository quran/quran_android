package com.quran.common.search

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.quran.common.search.arabic.ArabicCharacterHelper
import com.quran.common.search.arabic.ArabicCompiledQuery
import java.util.regex.Pattern

class ArabicSearcher(private val defaultSearcher: Searcher,
                     private val matchStart: String,
                     private val matchEnd: String) : Searcher {
  private val arabicRegex = "[$arabicRegexChars]".toRegex()

  override fun getQuery(withSnippets: Boolean,
                        hasFTS: Boolean,
                        table: String,
                        columns: String,
                        searchColumn: String): String {
    return defaultSearcher.getQuery(false, hasFTS, table, columns, searchColumn)
  }

  override fun getLimit(withSnippets: Boolean): String {
    // this used to be something like:
    // if (withSnippets) { "" } else { "LIMIT 250" }
    // but for Arabic, this is problematic because sometimes, results are only in the
    // end of the mushaf, and people can't find them if we only look through most of
    // sura Baqarah.
    //
    // the plan, in the future, in sha' Allah, is to make suggestions and results
    // the same thing, since the query under the hood is already the same anyway.
    return ""
  }

  override fun processSearchText(searchText: String, hasFTS: Boolean): String {
    val escaped = if (defaultSearcher is DefaultSearcher) {
      defaultSearcher.escapeLikeWildcards(searchText)
    } else {
      searchText
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    }
    val normalized = escaped.replace(arabicRegex, "_")
    return if (defaultSearcher is DefaultSearcher) {
      "%$normalized%"
    } else {
      defaultSearcher.processSearchText(normalized, false)
    }
  }

  override fun runQuery(database: SQLiteDatabase,
                        query: String,
                        searchText: String,
                        originalSearchText: String,
                        withSnippets: Boolean,
                        columns: Array<String>): Cursor {
    return executeQuery(
      database = database,
      query = query,
      searchText = searchText,
      originalSearchText = originalSearchText,
      highlightMatches = withSnippets,
      maxResults = if (withSnippets) Int.MAX_VALUE else SUGGESTIONS_LIMIT,
      columns = columns
    )
  }

  /**
   * Executes a full Arabic search returning raw matched verse text without `<font>` HTML wrapping,
   * allowing UI layers such as `SearchActivity` to highlight only visible rows lazily.
   */
  fun runQueryRaw(database: SQLiteDatabase,
                  query: String,
                  searchText: String,
                  originalSearchText: String,
                  columns: Array<String>): Cursor {
    return executeQuery(
      database = database,
      query = query,
      searchText = searchText,
      originalSearchText = originalSearchText,
      highlightMatches = false,
      maxResults = Int.MAX_VALUE,
      columns = columns
    )
  }

  private fun executeQuery(
    database: SQLiteDatabase,
    query: String,
    searchText: String,
    originalSearchText: String,
    highlightMatches: Boolean,
    maxResults: Int,
    columns: Array<String>
  ): Cursor {
    val initialCapacity = if (maxResults == SUGGESTIONS_LIMIT) SUGGESTIONS_LIMIT else INITIAL_RESULTS_CAPACITY
    val resultsBuilder = SearchResultsCursor.Builder(columns, initialCapacity)
    val compiledQuery = ArabicCompiledQuery.compile(originalSearchText)

    if (compiledQuery != null) {
      val highlightBuffer = if (highlightMatches) StringBuilder(256) else null
      database.rawQuery(query, arrayOf(searchText)).use { cursor ->
        while (cursor.moveToNext()) {
          val text = cursor.getString(3) ?: continue
          val matchText = if (highlightBuffer != null) {
            compiledQuery.highlightTo(text, matchStart, matchEnd, highlightBuffer) ?: continue
          } else {
            if (compiledQuery.findFirst(text, 0) < 0) continue
            text
          }
          resultsBuilder.addRow(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), matchText)
          if (resultsBuilder.size == maxResults) {
            break
          }
        }
      }
      return resultsBuilder.build()
    }

    // Fallback for empty/surrogate queries to maintain exact Pattern parity.
    val regexp = ArabicCharacterHelper.generateRegex(originalSearchText)
    val pattern = Pattern.compile(regexp)
    val matcher = pattern.matcher("")
    val highlightBuffer = if (highlightMatches) StringBuilder(256) else null

    database.rawQuery(query, arrayOf(searchText)).use { cursor ->
      while (cursor.moveToNext()) {
        val text = cursor.getString(3) ?: continue
        matcher.reset(text)
        if (matcher.find()) {
          val matchText: String = if (highlightBuffer != null) {
            highlightBuffer.setLength(0)
            var lastEnd = 0
            do {
              val start = matcher.start()
              val end = matcher.end()
              highlightBuffer.append(text, lastEnd, start)
              highlightBuffer.append(matchStart)
              highlightBuffer.append(text, start, end)
              highlightBuffer.append(matchEnd)
              lastEnd = end
            } while (matcher.find())
            highlightBuffer.append(text, lastEnd, text.length)
            highlightBuffer.toString()
          } else {
            text
          }

          resultsBuilder.addRow(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), matchText)
          if (resultsBuilder.size == maxResults) {
            break
          }
        }
      }
    }

    return resultsBuilder.build()
  }

  companion object {
    private const val arabicRegexChars = "\u0627\u0623\u0621\u062a\u0629\u0647\u0648\u0649\u064a\u0626"
    private const val INITIAL_RESULTS_CAPACITY = 256
    private const val SUGGESTIONS_LIMIT = 6
  }
}
