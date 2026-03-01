package com.quran.labs.feature.autoquran.common

import android.content.Context
import android.content.SharedPreferences
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

class RecentQariManager @Inject constructor(
  @ApplicationContext appContext: Context,
) {

  private val prefs: SharedPreferences =
    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getRecentQaris(): List<RecentQari> {
    val json = prefs.getString(KEY_RECENT_QARIS, null) ?: return emptyList()
    return try {
      val array = JSONArray(json)
      (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)
        RecentQari(
          qariId = obj.getInt(FIELD_QARI_ID),
          lastSura = obj.getInt(FIELD_LAST_SURA),
          timestamp = obj.getLong(FIELD_TIMESTAMP)
        )
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to parse recent qaris")
      emptyList()
    }
  }

  fun recordQari(qariId: Int, sura: Int) {
    val current = getRecentQaris().toMutableList()
    current.removeAll { it.qariId == qariId && it.lastSura == sura }
    current.add(0, RecentQari(qariId, sura, System.currentTimeMillis()))
    val trimmed = current.take(MAX_RECENT)
    val array = JSONArray()
    for (entry in trimmed) {
      val obj = JSONObject()
      obj.put(FIELD_QARI_ID, entry.qariId)
      obj.put(FIELD_LAST_SURA, entry.lastSura)
      obj.put(FIELD_TIMESTAMP, entry.timestamp)
      array.put(obj)
    }
    prefs.edit().putString(KEY_RECENT_QARIS, array.toString()).apply()
  }

  companion object {
    private const val PREFS_NAME = "autoquran_recent"
    private const val KEY_RECENT_QARIS = "recent_qaris"
    private const val FIELD_QARI_ID = "qari_id"
    private const val FIELD_LAST_SURA = "last_sura"
    private const val FIELD_TIMESTAMP = "timestamp"
    private const val MAX_RECENT = 5
  }
}
