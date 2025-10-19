package com.quran.labs.androidquran.common.audio.repository

import android.content.Context
import android.preference.PreferenceManager
import com.quran.data.di.AppScope
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
class CurrentQariManager @Inject constructor(@ApplicationContext appContext: Context, private val qariUtil: QariUtil) {
  private val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
  private val currentQariFlow =
    MutableStateFlow(prefs.getInt(PREF_DEFAULT_QARI, qariUtil.getDefaultQariId()))
  private val qaris by lazy { qariUtil.getQariList() }

  fun flow(): Flow<Qari> = currentQariFlow
    .map { qariId -> qaris.firstOrNull { it.id == qariId } ?: qaris.first() }

  fun currentQariId(): Int = currentQariFlow.value

  fun currentQari(): Qari = qaris.firstOrNull { it.id == currentQariId() } ?: qaris.first()

  fun setCurrentQari(qariId: Int) {
    prefs.edit().putInt(PREF_DEFAULT_QARI, qariId).apply()
    currentQariFlow.value = qariId
  }

  companion object {
    const val PREF_DEFAULT_QARI = "defaultQari"
  }
}
