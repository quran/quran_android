package com.quran.mobile.feature.downloadmanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.mobile.feature.downloadmanager.model.DownloadedSheikhUiModel

@Composable
fun ShuyookhList(
  shuyookh: List<DownloadedSheikhUiModel>,
  onQariItemClicked: ((QariItem) -> Unit)
) {
  Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
    shuyookh.forEach {
      SheikhDownloadSummary(it, onQariItemClicked)
    }
  }
}
