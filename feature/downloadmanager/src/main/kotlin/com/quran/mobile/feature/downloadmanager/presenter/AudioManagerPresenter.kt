package com.quran.mobile.feature.downloadmanager.presenter

import com.quran.data.di.ActivityScope
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.cache.QariDownloadInfoManager
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.mobile.feature.downloadmanager.model.DownloadedSheikhUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ActivityScope
class AudioManagerPresenter @Inject constructor(
  private val qariDownloadInfoManager: QariDownloadInfoManager
) {

  fun downloadedShuyookh(
    lambda: ((Qari) -> QariItem)
  ): Flow<ImmutableList<DownloadedSheikhUiModel>> {
    return qariDownloadInfoManager.downloadQariInfoFilteringNonDownloadedGappedQaris()
      .map { qariDownloadInfoList ->
        qariDownloadInfoList
          .map { qariDownloadInfo ->
            DownloadedSheikhUiModel(
              qariItem = lambda(qariDownloadInfo.qari),
              downloadedSuras = qariDownloadInfo.fullyDownloadedSuras.size
            )
          }
          .sortedBy { it.qariItem.name }
      }
      .map { it.toImmutableList() }
  }
}
