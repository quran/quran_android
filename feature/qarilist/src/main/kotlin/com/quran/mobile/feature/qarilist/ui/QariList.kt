package com.quran.mobile.feature.qarilist.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.mobile.feature.qarilist.model.QariUiModel
import kotlinx.collections.immutable.ImmutableList

internal fun qariItemKey(sectionHeader: Int, qariId: Int): String = "${sectionHeader}_$qariId"

@Composable
fun QariList(
  qaris: ImmutableList<QariUiModel>,
  selectedQariId: Int,
  onQariSelected: ((QariItem) -> Unit),
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues()
) {
  val qarisBySection = remember(qaris) { qaris.groupBy { it.sectionHeader } }
  LazyColumn(
    modifier = modifier,
    contentPadding = contentPadding
  ) {
    qarisBySection.forEach { (section, sectionQaris) ->
      stickyHeader(key = "section_$section", contentType = "header") {
        QariSection(section)
      }

      items(
        items = sectionQaris,
        key = { qariItemKey(section, it.qariItem.id) },
        contentType = { "qari" }
      ) { qariUiModel ->
        QariRow(
          qariUiModel.qariItem,
          selectedQariId == qariUiModel.qariItem.id,
          onQariSelected,
          modifier = Modifier.animateItem()
        )
      }
    }
  }
}
