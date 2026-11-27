package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quran.mobile.feature.downloadmanager.R
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.EntryForQari
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhUiModel
import com.quran.page.common.data.QuranNaming
import kotlinx.collections.immutable.ImmutableSet

@Composable
fun SheikhSuraInfoList(
  sheikhUiModel: SheikhUiModel,
  currentSelection: ImmutableSet<EntryForQari>,
  quranNaming: QuranNaming,
  onEntryClicked: ((EntryForQari) -> Unit),
  onSelectionStarted: ((EntryForQari) -> Unit)
) {
  LazyColumn {
    items(
      items = sheikhUiModel.suraUiModel,
      key = { uiModel ->
        when (uiModel) {
          is EntryForQari.DatabaseForQari -> -1
          is EntryForQari.SuraForQari -> uiModel.sura
        }
      }
    ) { uiModel ->
      when (uiModel) {
        is EntryForQari.SuraForQari -> {
          val isSuraSelected = uiModel in currentSelection

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
        }
        is EntryForQari.DatabaseForQari -> {
          val isSelected = uiModel in currentSelection
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
    }
    item(key = "bottom_spacer") {
      Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
  }
}
