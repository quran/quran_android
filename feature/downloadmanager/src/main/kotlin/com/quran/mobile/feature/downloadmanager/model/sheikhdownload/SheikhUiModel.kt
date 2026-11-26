package com.quran.mobile.feature.downloadmanager.model.sheikhdownload

import com.quran.data.model.audio.Qari
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

data class SheikhUiModel(
  val qariItem: Qari,
  val suraUiModel: ImmutableList<EntryForQari>,
  val selections: ImmutableSet<EntryForQari>,
  val dialog: SheikhDownloadDialog
)
