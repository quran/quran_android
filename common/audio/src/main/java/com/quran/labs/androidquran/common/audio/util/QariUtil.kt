package com.quran.labs.androidquran.common.audio.util

import android.content.Context
import com.quran.data.model.audio.Qari
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.common.audio.model.QariItem
import dev.zacsweers.metro.Inject

@Inject
class QariUtil(private val pageProvider: PageProvider) {

  /**
   * Get a list of all available qaris as [Qari].
   *
   * Unlike the method with the context parameter, this version does not have
   * the actual qari name. It only has the resource id for the qari.
   */
  fun getQariList(): List<Qari> {
    return pageProvider.getQaris()
  }

  /**
   * Get the default qari id when no qari is selected.
    */
  fun getDefaultQariId(): Int {
    return pageProvider.getDefaultQariId()
  }

  /**
   * Get the local id of the qari for a quran.com recitation id.
   *
   * @return the qari id, or null when this recitation is not one of ours.
   */
  fun qariIdForQuranComId(quranComId: Int): Int? =
    getQariList().firstOrNull { it.quranComId == quranComId }?.id

  /**
   * Get a list of all available qaris as [QariItem]s
   *
   * @param context the current context
   * @return a list of [QariItem] representing the qaris to show.
   */
  fun getQariList(context: Context): List<QariItem> {
    return getQariList().map { item -> QariItem.fromQari(context, item) }
  }
}
