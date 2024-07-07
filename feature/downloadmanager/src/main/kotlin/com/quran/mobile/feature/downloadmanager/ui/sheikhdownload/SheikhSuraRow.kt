package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

import androidx.compose.runtime.Composable
import com.quran.mobile.feature.downloadmanager.R
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.EntryForQari
import com.quran.page.common.data.QuranNaming

@Composable
fun SheikhSuraRow(
  sheikhSuraUiModel: EntryForQari.SuraForQari,
  isSelected: Boolean,
  quranNaming: QuranNaming,
  onEntryClicked: ((EntryForQari) -> Unit),
  onEntryLongClicked: ((EntryForQari) -> Unit)
) {
  SheikhEntryRow(
    sheikhSuraUiModel,
    isSelected,
    { context, sura -> quranNaming.getSuraName(context, sura) },
    { _ -> "" },
    R.string.audio_manager_surah_download,
    R.string.audio_manager_surah_delete,
    onEntryClicked,
    onEntryLongClicked
  )
}
