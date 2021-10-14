package com.quran.page.common.toolbar

import com.quran.data.model.selection.SelectionIndicator

interface AyahSelectionReactor {
  fun onSelectionChanged(selectionIndicator: SelectionIndicator, reset: Boolean)
  fun updateBookmarkStatus(isBookmarked: Boolean)
}
