package com.quran.labs.feature.autoquran.common

import android.content.Context
import android.content.SharedPreferences
import com.quran.mobile.di.qualifier.ApplicationContext
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.zacsweers.metro.Inject
import timber.log.Timber

class RecentQariManager @Inject constructor(
  @ApplicationContext appContext: Context,
) {

  private val prefs: SharedPreferences =
    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val adapter = Moshi.Builder().build().adapter<List<RecentQari>>(
    Types.newParameterizedType(List::class.java, RecentQari::class.java)
  )

  fun getRecentQaris(): List<RecentQari> {
    val json = prefs.getString(KEY_RECENT_QARIS, null) ?: return emptyList()
    return try {
      adapter.fromJson(json) ?: emptyList()
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
    prefs.edit().putString(KEY_RECENT_QARIS, adapter.toJson(trimmed)).apply()
  }

  companion object {
    private const val PREFS_NAME = "autoquran_recent"
    private const val KEY_RECENT_QARIS = "recent_qaris"
    private const val MAX_RECENT = 5
  }
}
