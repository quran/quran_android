package com.quran.labs.androidquran.ui.helpers

class SingleAyahHighlight(
  key: String
) : AyahHighlight(key = key, isTransition = false) {

  constructor(surah: Int, ayah: Int) : this("$surah:$ayah")

  companion object {
    @JvmStatic
    fun createSet(ayahKeys: Set<String>): Set<AyahHighlight> {
      return ayahKeys.map { SingleAyahHighlight(it) }.toSet()
    }
  }
}
