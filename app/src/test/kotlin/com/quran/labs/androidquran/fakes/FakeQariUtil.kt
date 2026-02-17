package com.quran.labs.androidquran.fakes

import android.content.Context
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem

/**
 * Fake implementation of QariUtil for testing.
 *
 * Provides configurable Qari data for testing audio-related functionality.
 * Used by AudioUtilsTest.
 */
class FakeQariUtil {

  private var qariList: List<Qari> = emptyList()
  private var defaultQariId: Int = 0

  fun setQariList(qaris: List<Qari>) {
    qariList = qaris
  }

  fun setDefaultQariId(id: Int) {
    defaultQariId = id
  }

  fun getQariList(): List<Qari> {
    return qariList
  }

  fun getDefaultQariId(): Int {
    return defaultQariId
  }

  fun getQariList(context: Context): List<QariItem> {
    return qariList.map { qari -> QariItem.fromQari(context, qari) }
  }
}
