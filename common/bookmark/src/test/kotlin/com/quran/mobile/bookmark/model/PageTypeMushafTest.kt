package com.quran.mobile.bookmark.model

import com.google.common.truth.Truth.assertThat
import com.quran.shared.persistence.model.Mushaf
import org.junit.Test

class PageTypeMushafTest {

  @Test
  fun `madani data source page types use the 1405 mushaf`() {
    assertThat(listOf("madani", "tajweed").map(::mushafForPageType))
      .containsExactly(Mushaf.MADANI_1405, Mushaf.MADANI_1405)
  }

  @Test
  fun `new madani data source page types use the 1440 mushaf`() {
    val pageTypes = listOf("new_madani", "new_madani_1441", "new_madani_1439_lines", "madani_1441_lines")

    assertThat(pageTypes.map(::mushafForPageType).distinct()).containsExactly(Mushaf.MADANI_1440)
  }

  @Test
  fun `naskh uses the 15-line IndoPak mushaf`() {
    assertThat(mushafForPageType("naskh")).isEqualTo(Mushaf.INDOPAK_15_LINES)
  }

  @Test
  fun `shemerly uses the Shemerly mushaf`() {
    assertThat(mushafForPageType("shemerly")).isEqualTo(Mushaf.SHEMERLY)
  }

  @Test
  fun `warsh and qaloon use their riwayah mushafs`() {
    assertThat(listOf("warsh", "qaloon").map(::mushafForPageType))
      .containsExactly(Mushaf.WARSH, Mushaf.QALOUN)
      .inOrder()
  }

  @Test
  fun `an unknown page type has no mushaf`() {
    assertThat(mushafForPageType("unknown")).isNull()
  }
}
