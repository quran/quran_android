package com.quran.common.search

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class DefaultSearcher(private val matchStart: String,
                      private val matchEnd: String,
                      private val ellipses: String) : Searcher {

  override fun getQuery(withSnippets: Boolean,
                        hasFTS: Boolean,
                        table: String,
                        columns: String,
                        searchColumn: String): String {

    val operator = if (hasFTS) {
      " MATCH "
    } else {
      " LIKE "
    }

    val whatToSelect = if (hasFTS && withSnippets) {
      "snippet(" + table + ", '" +
          matchStart + "', '" + matchEnd +
          "', '" + ellipses + "', -1, 64)"
    } else {
      searchColumn
    }

    return "select $columns, $whatToSelect FROM $table WHERE $searchColumn $operator ?"
  }

  override fun getLimit(withSnippets: Boolean): String {
    return if (withSnippets) { "" } else { "LIMIT 10" }
  }

  override fun processSearchText(searchText: String, hasFTS: Boolean): String {
    return if (hasFTS) {
      // MATCH query
      "$searchText*"
    } else {
      // LIKE query
      "%$searchText%"
    }
  }

  override fun runQuery(database: SQLiteDatabase,
                        query: String,
                        searchText: String,
                        originalSearchText: String,
                        withSnippets: Boolean,
                        columns: Array<String>): Cursor {
    return database.rawQuery(query, arrayOf(searchText))
  }
}
