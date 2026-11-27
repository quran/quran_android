package com.quran.mobile.feature.downloadmanager.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.mobile.feature.downloadmanager.model.DownloadedSheikhUiModel
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ShuyookhList(
  shuyookh: ImmutableList<DownloadedSheikhUiModel>,
  onQariItemClicked: ((QariItem) -> Unit)
) {
  LazyColumn {
    items(items = shuyookh, key = { it.qariItem.id }) { sheikh ->
      SheikhDownloadSummary(sheikh, onQariItemClicked)
    }
    item(key = "bottom_spacer") {
      Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
  }
}
