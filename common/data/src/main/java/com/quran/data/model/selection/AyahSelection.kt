package com.quran.data.model.selection

import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection.Ayah
import com.quran.data.model.selection.AyahSelection.AyahRange
import com.quran.data.model.selection.AyahSelection.None

sealed class AyahSelection {
  object None : AyahSelection()
  data class Ayah(
    val suraAyah: SuraAyah,
    val selectionIndicator: SelectionIndicator = SelectionIndicator.None
  ): AyahSelection()

  data class AyahRange(
    val startSuraAyah: SuraAyah,
    val endSuraAyah: SuraAyah,
    val selectionIndicator: SelectionIndicator = SelectionIndicator.None
  ): AyahSelection()
}

fun AyahSelection.withSelectionIndicator(selectionIndicator: SelectionIndicator): AyahSelection =
  when (this) {
    is Ayah -> copy(selectionIndicator = selectionIndicator)
    is AyahRange -> copy(selectionIndicator = selectionIndicator)
    None -> this
  }

fun AyahSelection.selectionIndicator(): SelectionIndicator =
  when (this) {
    is Ayah -> this.selectionIndicator
    is AyahRange -> this.selectionIndicator
    None -> SelectionIndicator.None
  }

fun AyahSelection.startSuraAyah(): SuraAyah? =
  when (this) {
    is Ayah -> suraAyah
    is AyahRange -> startSuraAyah
    None -> null
  }

fun AyahSelection.endSuraAyah(): SuraAyah? =
  when (this) {
    is Ayah -> suraAyah
    is AyahRange -> endSuraAyah
    None -> null
  }
