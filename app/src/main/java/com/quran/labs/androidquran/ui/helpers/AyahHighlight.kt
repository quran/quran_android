package com.quran.labs.androidquran.ui.helpers

sealed class AyahHighlight {

  abstract val key: String

  fun isTransition() = this is TransitionAyahHighlight

  data class SingleAyahHighlight(override val key: String) : AyahHighlight() {
    @JvmOverloads
    constructor(sura: Int, ayah: Int, word: Int = -1) :
        this(key = "$sura:$ayah${if (word < 0) "" else ":$word"}")

    companion object {
      @JvmStatic
      fun createSet(ayahKeys: Set<String>): Set<AyahHighlight> {
        return ayahKeys.map { SingleAyahHighlight(it) }.toSet()
      }
    }
  }

  data class TransitionAyahHighlight(
    val source: AyahHighlight,
    val destination: AyahHighlight
  ) : AyahHighlight() {
    override val key = "${source.key}->${destination.key}"
  }

}
