package com.quran.labs.androidquran.extra.feature.linebyline.presenter.selection

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection
import org.junit.Test

class AyahSelectionExtensionTest {

  // ---- mergeWith: None base ----

  @Test
  fun `mergeWith on None returns the new ayah selection`() {
    // Arrange
    val base = AyahSelection.None
    val incoming = AyahSelection.Ayah(SuraAyah(1, 1))

    // Act
    val result = base.mergeWith(incoming, SuraAyah(1, 1))

    // Assert
    assertThat(result).isEqualTo(incoming)
  }

  // ---- mergeWith: Ayah base ----

  @Test
  fun `mergeWith on Ayah with same ayah returns original ayah`() {
    // Arrange
    val suraAyah = SuraAyah(2, 5)
    val base = AyahSelection.Ayah(suraAyah)
    val incoming = AyahSelection.Ayah(suraAyah)

    // Act
    val result = base.mergeWith(incoming, suraAyah)

    // Assert
    assertThat(result).isEqualTo(base)
  }

  @Test
  fun `mergeWith on Ayah with earlier incoming creates range with incoming as start`() {
    // Arrange
    val baseSuraAyah = SuraAyah(2, 10)
    val incomingSuraAyah = SuraAyah(2, 5)
    val base = AyahSelection.Ayah(baseSuraAyah)
    val incoming = AyahSelection.Ayah(incomingSuraAyah)

    // Act
    val result = base.mergeWith(incoming, baseSuraAyah)

    // Assert
    assertThat(result).isInstanceOf(AyahSelection.AyahRange::class.java)
    val range = result as AyahSelection.AyahRange
    assertThat(range.startSuraAyah).isEqualTo(incomingSuraAyah)
    assertThat(range.endSuraAyah).isEqualTo(baseSuraAyah)
  }

  @Test
  fun `mergeWith on Ayah with later incoming creates range with base as start`() {
    // Arrange
    val baseSuraAyah = SuraAyah(2, 5)
    val incomingSuraAyah = SuraAyah(2, 10)
    val base = AyahSelection.Ayah(baseSuraAyah)
    val incoming = AyahSelection.Ayah(incomingSuraAyah)

    // Act
    val result = base.mergeWith(incoming, baseSuraAyah)

    // Assert
    assertThat(result).isInstanceOf(AyahSelection.AyahRange::class.java)
    val range = result as AyahSelection.AyahRange
    assertThat(range.startSuraAyah).isEqualTo(baseSuraAyah)
    assertThat(range.endSuraAyah).isEqualTo(incomingSuraAyah)
  }

  @Test
  fun `mergeWith on Ayah with ayah from different sura creates correct range`() {
    // Arrange
    val baseSuraAyah = SuraAyah(2, 1)
    val incomingSuraAyah = SuraAyah(3, 1)
    val base = AyahSelection.Ayah(baseSuraAyah)
    val incoming = AyahSelection.Ayah(incomingSuraAyah)

    // Act
    val result = base.mergeWith(incoming, baseSuraAyah)

    // Assert
    assertThat(result).isInstanceOf(AyahSelection.AyahRange::class.java)
    val range = result as AyahSelection.AyahRange
    assertThat(range.startSuraAyah).isEqualTo(baseSuraAyah)
    assertThat(range.endSuraAyah).isEqualTo(incomingSuraAyah)
  }

  // ---- mergeWith: AyahRange base ----

  @Test
  fun `mergeWith on AyahRange with ayah before start extends start`() {
    // Arrange
    val startAyah = SuraAyah(2, 5)
    val endAyah = SuraAyah(2, 10)
    val selectionStart = startAyah
    val base = AyahSelection.AyahRange(startAyah, endAyah)
    val incoming = AyahSelection.Ayah(SuraAyah(2, 2))

    // Act
    val result = base.mergeWith(incoming, selectionStart)

    // Assert
    assertThat(result).isInstanceOf(AyahSelection.AyahRange::class.java)
    val range = result as AyahSelection.AyahRange
    assertThat(range.startSuraAyah).isEqualTo(SuraAyah(2, 2))
    assertThat(range.endSuraAyah).isEqualTo(endAyah)
  }

  @Test
  fun `mergeWith on AyahRange with ayah after start extends end`() {
    // Arrange
    val startAyah = SuraAyah(2, 5)
    val endAyah = SuraAyah(2, 10)
    val selectionStart = startAyah
    val base = AyahSelection.AyahRange(startAyah, endAyah)
    val incoming = AyahSelection.Ayah(SuraAyah(2, 12))

    // Act
    val result = base.mergeWith(incoming, selectionStart)

    // Assert
    assertThat(result).isInstanceOf(AyahSelection.AyahRange::class.java)
    val range = result as AyahSelection.AyahRange
    assertThat(range.startSuraAyah).isEqualTo(startAyah)
    assertThat(range.endSuraAyah).isEqualTo(SuraAyah(2, 12))
  }

  @Test
  fun `mergeWith on AyahRange with ayah equal to start collapses to single ayah`() {
    // Arrange
    val startAyah = SuraAyah(2, 5)
    val endAyah = SuraAyah(2, 10)
    val selectionStart = startAyah
    val base = AyahSelection.AyahRange(startAyah, endAyah)
    val incoming = AyahSelection.Ayah(startAyah)

    // Act
    val result = base.mergeWith(incoming, selectionStart)

    // Assert
    assertThat(result).isInstanceOf(AyahSelection.Ayah::class.java)
    val ayah = result as AyahSelection.Ayah
    assertThat(ayah.suraAyah).isEqualTo(startAyah)
  }
}
