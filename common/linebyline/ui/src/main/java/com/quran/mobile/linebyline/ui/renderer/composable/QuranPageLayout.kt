package com.quran.mobile.linebyline.ui.renderer.composable

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable

@Composable
fun QuranPageLayout(
  page: Int,
  isScrollable: Boolean,
  scrollState: ScrollState,
  showSidelines: Boolean,
  header: @Composable () -> Unit,
  footer: @Composable () -> Unit,
  sidelines: @Composable () -> Unit,
  quran: @Composable () -> Unit
) {
  if (isScrollable) {
    QuranScrollablePage(page, scrollState = scrollState, header = header, quran = quran, footer = footer, showSidelines, sidelines)
  } else {
    QuranNonScrollablePage(page, header = header, quran = quran, footer = footer, showSidelines, sidelines)
  }
}
