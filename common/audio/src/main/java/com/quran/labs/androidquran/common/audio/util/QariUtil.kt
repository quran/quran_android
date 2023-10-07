package com.quran.labs.androidquran.common.audio.util

import android.content.Context
import com.quran.data.model.audio.Qari
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.common.audio.model.QariItem
import javax.inject.Inject

class QariUtil @Inject constructor(private val pageProvider: PageProvider) {

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
   * Get a list of all available qaris as [QariItem]s
   *
   * @param context the current context
   * @return a list of [QariItem] representing the qaris to show.
   */
  fun getQariList(context: Context): List<QariItem> {
    return getQariList().map { item ->
       QariItem(
        id = item.id,
        name = context.getString(item.nameResource),
        url = item.url,
        path = item.path,
        hasGaplessAlternative = item.hasGaplessAlternative,
        db = item.db
      )
    }
  }
}
