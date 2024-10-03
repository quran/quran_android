package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quran.mobile.feature.downloadmanager.R
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.EntryForQari
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhUiModel
import com.quran.page.common.data.QuranNaming

@Composable
fun SheikhSuraInfoList(
  sheikhUiModel: SheikhUiModel,
  currentSelection: List<EntryForQari>,
  quranNaming: QuranNaming,
  onEntryClicked: ((EntryForQari) -> Unit),
  onSelectionStarted: ((EntryForQari) -> Unit)
) {
  Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
    sheikhUiModel.suraUiModel.forEach { uiModel ->
      if (uiModel is EntryForQari.SuraForQari) {
        val isSuraSelected = currentSelection
          .filterIsInstance<EntryForQari.SuraForQari>()
          .any { it.sura == uiModel.sura }

        SheikhSuraRow(
          sheikhSuraUiModel = uiModel,
          isSelected = isSuraSelected,
          quranNaming = quranNaming,
          onEntryClicked = { clickedSura -> onEntryClicked(clickedSura) },
          onEntryLongClicked = { clickedSura ->
            if (currentSelection.isEmpty()) {
              onSelectionStarted(clickedSura)
            }
          }
        )
      } else if (uiModel is EntryForQari.DatabaseForQari) {
        val isSelected = currentSelection.any { it is EntryForQari.DatabaseForQari }
        SheikhEntryRow(
          sheikhEntryUiModel = uiModel,
          isSelected = isSelected,
          suraNaming = { _, _ -> "" },
          databaseNaming = { context -> context.getString(R.string.audio_manager_database) },
          R.string.audio_manager_database_download,
          R.string.audio_manager_database_delete,
          onEntryClicked,
          onSelectionStarted
        )
      }
    }
    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
  }
}
