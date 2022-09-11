package com.quran.mobile.feature.qarilist.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.mobile.feature.qarilist.model.QariUiModel

@Composable
fun QariList(qaris: List<QariUiModel>, selectedQariId: Int, onQariSelected: ((QariItem) -> Unit), modifier: Modifier) {
  val qarisBySection = qaris.groupBy { it.sectionHeader }
  LazyColumn(modifier = modifier) {
    qarisBySection.forEach { (section, sectionQaris) ->
      stickyHeader {
        QariSection(section)
      }

      items(sectionQaris) { qariUiModel ->
        QariRow(qariUiModel.qariItem, selectedQariId == qariUiModel.qariItem.id, onQariSelected)
      }
    }
  }
}
