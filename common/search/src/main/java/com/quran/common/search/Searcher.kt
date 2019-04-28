package com.quran.common.search

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

interface Searcher {
  fun getQuery(withSnippets: Boolean,
               hasFTS: Boolean,
               table: String,
               columns: String,
               searchColumn: String): String

  fun getLimit(withSnippets: Boolean): String

  fun processSearchText(searchText: String, hasFTS: Boolean): String

  fun runQuery(database: SQLiteDatabase,
               query: String,
               searchText: String,
               originalSearchText: String,
               withSnippets: Boolean,
               columns: Array<String>): Cursor
}
