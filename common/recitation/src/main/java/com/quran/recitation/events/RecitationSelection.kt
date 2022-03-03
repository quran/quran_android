package com.quran.recitation.events

import com.quran.data.model.SuraAyah
import com.quran.recitation.common.RecitedAyah
import com.quran.recitation.common.RecitedSection
import com.quran.recitation.common.RecitedWord

sealed class RecitationSelection {
  object None : RecitationSelection()

  data class Ayah(
    val ayah: RecitedAyah,
  ): RecitationSelection()

  data class Section(
    val section: RecitedSection,
  ): RecitationSelection()

  data class Word(
    val section: RecitedSection,
    val word: RecitedWord,
  ): RecitationSelection()

  fun ayah(): SuraAyah? = when (this) {
    is Ayah -> ayah.ayah()
    is Section -> section.ayah()
    is Word -> section.ayah()
    None -> null
  }

  fun startTime(): Double? = when (this) {
    is Ayah -> ayah.startTime()
    is Section -> section.startTime()
    is Word -> word.startTime()
    None -> null
  }

  fun endTime(): Double? = when (this) {
    is Ayah -> ayah.endTime()
    is Section -> section.endTime()
    is Word -> word.endTime()
    None -> null
  }
}
