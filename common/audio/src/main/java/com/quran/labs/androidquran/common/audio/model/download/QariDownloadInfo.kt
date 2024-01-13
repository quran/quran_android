package com.quran.labs.androidquran.common.audio.model.download

import com.quran.data.model.audio.Qari

sealed class QariDownloadInfo {
  abstract val qari: Qari
  abstract val fullyDownloadedSuras: List<Int>

  data class GaplessQariDownloadInfo(
    override val qari: Qari,
    override val fullyDownloadedSuras: List<Int>,
    val partiallyDownloadedSuras: List<Int>
  ) : QariDownloadInfo()

  data class GappedQariDownloadInfo(
    override val qari: Qari,
    override val fullyDownloadedSuras: List<Int>,
    val partiallyDownloadedSuras: List<PartiallyDownloadedSura>
  ) : QariDownloadInfo()
}
