package com.quran.labs.androidquran.common.audio.model

sealed class QariDownloadInfo {
  abstract val qariItem: QariItem
  abstract val fullyDownloadedSuras: List<Int>

  data class GaplessQariDownloadInfo(
    override val qariItem: QariItem,
    override val fullyDownloadedSuras: List<Int>,
    val partiallyDownloadedSuras: List<Int>
  ) : QariDownloadInfo()

  data class GappedQariDownloadInfo(
    override val qariItem: QariItem,
    override val fullyDownloadedSuras: List<Int>,
    val partiallyDownloadedSuras: List<PartiallyDownloadedSura>
  ) : QariDownloadInfo()
}
