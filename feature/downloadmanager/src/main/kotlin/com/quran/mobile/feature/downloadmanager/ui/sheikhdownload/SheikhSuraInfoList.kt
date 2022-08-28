package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhUiModel
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraForQari
import com.quran.page.common.data.QuranNaming

@Composable
fun SheikhSuraInfoList(
  sheikhUiModel: SheikhUiModel,
  currentSelection: List<SuraForQari>,
  quranNaming: QuranNaming,
  onSelectionInfoChanged: ((List<SuraForQari>) -> Unit),
  onSuraActionRequested: ((SuraForQari) -> Unit)
) {
  Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
    sheikhUiModel.suraUiModel.forEach { suraUiModel ->
      val isSuraSelected = currentSelection.any { it.sura == suraUiModel.sura }
      SheikhSuraRow(
        sheikhSuraUiModel = suraUiModel,
        isSelected = isSuraSelected,
        quranNaming = quranNaming,
        onSuraClicked = { clickedSura ->
          if (currentSelection.isEmpty()) {
            onSuraActionRequested(clickedSura)
          } else if (isSuraSelected){
            val updatedList = currentSelection.filter { it.sura != clickedSura.sura }
            onSelectionInfoChanged(updatedList)
          } else {
            onSelectionInfoChanged(currentSelection + clickedSura)
          }
        },
        onSuraLongClicked = { clickedSura ->
          if (currentSelection.isEmpty()) {
            onSelectionInfoChanged(currentSelection + clickedSura)
          }
        }
      )
    }
  }
}
