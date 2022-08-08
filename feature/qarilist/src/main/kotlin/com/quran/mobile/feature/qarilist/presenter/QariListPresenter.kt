package com.quran.mobile.feature.qarilist.presenter

import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.cache.QariDownloadInfoManager
import com.quran.labs.androidquran.common.audio.extension.isRangeDownloaded
import com.quran.labs.androidquran.common.audio.model.QariDownloadInfo
import com.quran.mobile.feature.qarilist.R
import com.quran.mobile.feature.qarilist.model.QariUiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class QariListPresenter @Inject constructor(private val qariDownloadInfoManager: QariDownloadInfoManager) {

  fun qariList(start: SuraAyah, end: SuraAyah): Flow<List<QariUiModel>> {
    return qariDownloadInfoManager.downloadedQariInfo().map { list ->
        list.filter { qariDownloadInfo ->
          val qariItem = qariDownloadInfo.qariItem
          val gappedItem = qariDownloadInfo as? QariDownloadInfo.GappedQariDownloadInfo
          qariItem.isGapless ||
              (qariItem.hasGaplessAlternative && qariDownloadInfo.fullyDownloadedSuras.isEmpty() &&
                  (gappedItem?.partiallyDownloadedSuras?.isEmpty() ?: false)
              )
        }
      }
      .map { unsortedQariList ->
        val qariList = unsortedQariList.sortedBy { it.qariItem.name }
        val readyToPlay = qariList.filter { it.isRangeDownloaded(start, end) }
        val qarisWithDownloads = qariList.filter { it.fullyDownloadedSuras.isNotEmpty() }
          .filter { it !in readyToPlay }
        val gapless = qariList.filter { it.qariItem.isGapless }
        val gapped = qariList.filter { !it.qariItem.isGapless }

        readyToPlay.map { QariUiModel(it.qariItem, R.string.qarilist_ready_to_play) } +
            qarisWithDownloads.map { QariUiModel(it.qariItem, R.string.qarilist_qaris_with_downloads) } +
            gapless.map { QariUiModel(it.qariItem, R.string.qarilist_gapless) } +
            gapped.map { QariUiModel(it.qariItem, R.string.qarilist_gapped) }
      }
  }
}
