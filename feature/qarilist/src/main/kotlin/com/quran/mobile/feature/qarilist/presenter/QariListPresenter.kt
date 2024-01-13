package com.quran.mobile.feature.qarilist.presenter

import com.quran.data.di.ActivityScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.cache.QariDownloadInfoManager
import com.quran.labs.androidquran.common.audio.extension.isRangeDownloaded
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.mobile.feature.qarilist.R
import com.quran.mobile.feature.qarilist.model.QariUiModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@ActivityScope
class QariListPresenter @Inject constructor(private val qariDownloadInfoManager: QariDownloadInfoManager) {

  fun qariList(start: SuraAyah, end: SuraAyah, qariTranslationLambda: ((Qari) -> QariItem)): Flow<List<QariUiModel>> {
    return qariDownloadInfoManager.downloadQariInfoFilteringNonDownloadedGappedQaris()
      .map { unsortedQariList ->
        val readyToPlay = unsortedQariList.filter { it.isRangeDownloaded(start, end) }
          .toSortedQariItemList(qariTranslationLambda)
        val qarisWithDownloads = unsortedQariList.filter { it.fullyDownloadedSuras.isNotEmpty() }
          .toSortedQariItemList(qariTranslationLambda)
          .filter { it !in readyToPlay }
        val gapless = unsortedQariList.filter { it.qari.isGapless }.toSortedQariItemList(qariTranslationLambda)
        val gapped = unsortedQariList.filter { !it.qari.isGapless }.toSortedQariItemList(qariTranslationLambda)

        readyToPlay.map { QariUiModel(it, R.string.qarilist_ready_to_play) } +
            qarisWithDownloads.map { QariUiModel(it, R.string.qarilist_qaris_with_downloads) } +
            gapless.map { QariUiModel(it, R.string.qarilist_gapless) } +
            gapped.map { QariUiModel(it, R.string.qarilist_gapped) }
      }
  }

  private fun List<QariDownloadInfo>.toSortedQariItemList(lambda: ((Qari) -> QariItem)): List<QariItem> {
    return this.map { lambda(it.qari) }
      .sortedBy { it.name }
  }
}
