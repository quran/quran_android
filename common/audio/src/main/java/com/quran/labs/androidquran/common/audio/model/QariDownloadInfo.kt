package com.quran.labs.androidquran.common.audio.model

data class QariDownloadInfo(
  val qariItem: QariItem,
  val fullyDownloadedSuras: List<Int>,
  val partiallyDownloadedSuras: List<Int>
)
