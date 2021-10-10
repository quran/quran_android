package com.quran.data.model.selection

sealed class SelectionIndicator {
  object None : SelectionIndicator()
  data class SelectionPosition(val selectedAyahPosition: SelectedAyahPosition) : SelectionIndicator()
}
