package com.quran.labs.androidquran.ui.helpers

class SingleAyahHighlight : AyahHighlight {

  constructor(key: String) : super(key)

  constructor(surah: Int, ayah: Int) : super("$surah:$ayah")

  companion object {
    @JvmStatic
    fun createSet(ayahKeys: Set<String>): Set<AyahHighlight> {
      return ayahKeys.map { SingleAyahHighlight(it) }.toSet()
    }
  }
}
