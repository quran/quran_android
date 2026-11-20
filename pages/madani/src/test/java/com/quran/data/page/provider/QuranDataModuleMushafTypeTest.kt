package com.quran.data.page.provider

import com.google.common.truth.Truth.assertThat
import com.quran.data.source.MushafType
import org.junit.Test

class QuranDataModuleMushafTypeTest {

  @Test
  fun `each page type names the mushaf whose page numbers it uses`() {
    val mushafTypes = mapOf(
      "madani" to QuranDataModule.provideMadaniPageSet(),
      "new_madani" to QuranDataModule.provideNewMadaniPageSet(),
      "new_madani_1441" to QuranDataModule.provideNewerNewMadaniPageSet(),
      "new_madani_1439_lines" to QuranDataModule.provideNewerNewMadaniLinesPageSet(),
      "madani_1441_lines" to QuranDataModule.provideMadani1441LinesPageSet(),
      "shemerly" to QuranDataModule.provideShemerlyPageSet(),
      "tajweed" to QuranDataModule.provideTajweedPageSet()
    ).mapValues { (_, pageProvider) -> pageProvider.mushafType() }

    assertThat(mushafTypes).containsExactly(
      "madani", MushafType.MADANI_1405,
      "new_madani", MushafType.MADANI_1440,
      "new_madani_1441", MushafType.MADANI_1440,
      "new_madani_1439_lines", MushafType.MADANI_1440,
      "madani_1441_lines", MushafType.MADANI_1440,
      "shemerly", MushafType.SHEMERLY,
      "tajweed", MushafType.MADANI_1405
    )
  }
}
