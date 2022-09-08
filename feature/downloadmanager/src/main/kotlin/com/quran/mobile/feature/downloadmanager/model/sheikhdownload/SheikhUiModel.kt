package com.quran.mobile.feature.downloadmanager.model.sheikhdownload

import com.quran.data.model.audio.Qari

data class SheikhUiModel(
  val qariItem: Qari,
  val suraUiModel: List<SuraForQari>,
  val selections: List<SuraForQari>,
  val dialog: SheikhDownloadDialog
)
