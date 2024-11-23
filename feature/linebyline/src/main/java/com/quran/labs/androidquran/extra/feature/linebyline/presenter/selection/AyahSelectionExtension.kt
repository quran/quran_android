package com.quran.labs.androidquran.extra.feature.linebyline.presenter.selection

import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection

fun AyahSelection.mergeWith(
  ayahSelection: AyahSelection.Ayah,
  selectionStartAyah: SuraAyah
): AyahSelection {
  return when (this) {
    is AyahSelection.Ayah -> mergeAyahWithAyah(this, ayahSelection)
    is AyahSelection.AyahRange -> mergeAyahIntoAyahRange(
      ayahSelection,
      this,
      selectionStartAyah
    )
    AyahSelection.None -> ayahSelection
  }
}

private fun mergeAyahWithAyah(firstAyah: AyahSelection.Ayah, secondAyah: AyahSelection.Ayah): AyahSelection {
  return if (firstAyah.suraAyah == secondAyah.suraAyah) {
    firstAyah
  } else {
    if (firstAyah.suraAyah > secondAyah.suraAyah) {
      AyahSelection.AyahRange(secondAyah.suraAyah, firstAyah.suraAyah, firstAyah.selectionIndicator)
    } else {
      AyahSelection.AyahRange(
        firstAyah.suraAyah,
        secondAyah.suraAyah,
        secondAyah.selectionIndicator
      )
    }
  }
}

private fun mergeAyahIntoAyahRange(
  ayah: AyahSelection.Ayah,
  ayahRange: AyahSelection.AyahRange,
  selectionStartAyah: SuraAyah,
): AyahSelection {
  val suraAyah = ayah.suraAyah
  return if (suraAyah < selectionStartAyah) {
    ayahRange.copy(startSuraAyah = suraAyah)
  } else if (suraAyah > selectionStartAyah) {
    ayahRange.copy(endSuraAyah = suraAyah)
  } else {
    ayah
  }
}
