package com.quran.data.model.selection

import com.quran.data.model.SuraAyah

sealed class AyahSelection {
  object None : AyahSelection()
  data class Ayah(val suraAyah: SuraAyah): AyahSelection()
  data class AyahRange(val startSuraAyah: SuraAyah, val endSuraAyah: SuraAyah): AyahSelection()
}
