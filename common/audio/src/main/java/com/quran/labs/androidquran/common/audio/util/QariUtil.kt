package com.quran.labs.androidquran.common.audio.util

import android.content.Context
import com.quran.labs.androidquran.common.audio.model.AudioConfiguration
import com.quran.labs.androidquran.common.audio.model.QariItem
import javax.inject.Inject

class QariUtil @Inject constructor() {

  /**
   * Get a list of all available qaris as [QariItem]s
   *
   * @param context the current context
   * @param audioConfiguration the audio configuration
   * @return a list of [QariItem] representing the qaris to show.
   */
  fun getQariList(context: Context, audioConfiguration: AudioConfiguration): List<QariItem> {
    val resources = context.resources
    val shuyookh = resources.getStringArray(audioConfiguration.quranReadersName)
    val paths = resources.getStringArray(audioConfiguration.quranReadersPath)
    val urls = resources.getStringArray(audioConfiguration.quranReadersUrls)
    val databases = resources.getStringArray(audioConfiguration.quranReadersDatabaseNames)
    val hasGaplessEquivalent = resources.getIntArray(audioConfiguration.quranReadersHaveGaplessEquivalents)

    return shuyookh.mapIndexed { i, _ ->
       QariItem(
        id = i,
        name = shuyookh[i],
        url = urls[i],
        path = paths[i],
        hasGaplessAlternative = hasGaplessEquivalent[i] == 0,
        db = databases[i]
      )
    }
  }
}
