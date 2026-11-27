package com.quran.labs.androidquran.hybrid.contract

import android.content.Context
import android.content.SharedPreferences
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

@SingleIn(AppScope::class)
class RecentSearchRepository internal constructor(
  private val prefs: SharedPreferences,
  private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {

  @Inject
  constructor(@ApplicationContext context: Context) : this(
    prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    maxEntries = DEFAULT_MAX_ENTRIES
  )

  private val mutex = Mutex()
  private val _recentQueriesFlow = MutableStateFlow(loadQueries())
  val recentQueriesFlow: StateFlow<List<String>> = _recentQueriesFlow.asStateFlow()

  suspend fun recordQuery(query: String) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    mutex.withLock {
      val updated = loadQueries().toMutableList()
      updated.removeAll { it == trimmed }
      updated.add(0, trimmed)
      while (updated.size > maxEntries) {
        updated.removeAt(updated.lastIndex)
      }
      saveQueries(updated)
      _recentQueriesFlow.value = updated.toList()
    }
  }

  suspend fun removeQuery(query: String) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    mutex.withLock {
      val updated = loadQueries().toMutableList()
      if (updated.removeAll { it == trimmed }) {
        saveQueries(updated)
        _recentQueriesFlow.value = updated.toList()
      }
    }
  }

  suspend fun clearAll() {
    mutex.withLock {
      prefs.edit().remove(KEY_RECENT_QUERIES_JSON).apply()
      _recentQueriesFlow.value = emptyList()
    }
  }

  private fun loadQueries(): List<String> {
    return runCatching {
      val raw = prefs.getString(KEY_RECENT_QUERIES_JSON, null) ?: return emptyList()
      val jsonArray = JSONArray(raw)
      buildList {
        for (i in 0 until jsonArray.length()) {
          val item = (jsonArray.opt(i) as? String)?.trim().orEmpty()
          if (item.isNotEmpty() && !contains(item)) {
            add(item)
          }
        }
      }.take(maxEntries)
    }.getOrDefault(emptyList())
  }

  private fun saveQueries(queries: List<String>) {
    val jsonArray = JSONArray()
    for (q in queries) {
      jsonArray.put(q)
    }
    prefs.edit().putString(KEY_RECENT_QUERIES_JSON, jsonArray.toString()).apply()
  }

  companion object {
    const val DEFAULT_MAX_ENTRIES: Int = 8
    private const val PREFS_NAME = "hybrid_recent_search_prefs"
    private const val KEY_RECENT_QUERIES_JSON = "recent_queries_json"
  }
}
