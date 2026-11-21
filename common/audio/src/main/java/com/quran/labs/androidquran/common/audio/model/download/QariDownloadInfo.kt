package com.quran.labs.androidquran.common.audio.model.download

import com.quran.data.model.audio.Qari

sealed class QariDownloadInfo {
  abstract val qari: Qari
  /** Suras fully downloaded in formats eligible for playback on this device. */
  abstract val fullyDownloadedSuras: List<Int>

  /** Suras with completed audio files in any format. In-progress .part files are excluded. */
  abstract val surasWithLocalFiles: List<Int>

  data class GaplessQariDownloadInfo(
    override val qari: Qari,
    override val fullyDownloadedSuras: List<Int>,
    val partiallyDownloadedSuras: List<Int>,
    override val surasWithLocalFiles: List<Int> = fullyDownloadedSuras
  ) : QariDownloadInfo()

  data class GappedQariDownloadInfo(
    override val qari: Qari,
    override val fullyDownloadedSuras: List<Int>,
    val partiallyDownloadedSuras: List<PartiallyDownloadedSura>,
    override val surasWithLocalFiles: List<Int> =
      (fullyDownloadedSuras + partiallyDownloadedSuras.map { it.sura }).distinct()
  ) : QariDownloadInfo()
}
