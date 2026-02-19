package com.quran.labs.androidquran.common.audio.extension

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.common.audio.model.download.PartiallyDownloadedSura
import org.junit.Test

class PartiallyDownloadedSuraExtensionTest {

  @Test
  fun `didDownloadAyat returns true when all ayahs in range are downloaded`() {
    // Arrange
    val sura = PartiallyDownloadedSura(
      sura = 2,
      expectedAyahCount = 286,
      downloadedAyat = (1..286).toList()
    )

    // Act
    val result = sura.didDownloadAyat(currentSura = 2, start = 1, end = 286)

    // Assert
    assertThat(result).isTrue()
  }

  @Test
  fun `didDownloadAyat returns false when some ayahs in range are missing`() {
    // Arrange
    val downloadedAyat = (1..10).toList() + (12..20).toList() // ayah 11 missing
    val sura = PartiallyDownloadedSura(
      sura = 3,
      expectedAyahCount = 200,
      downloadedAyat = downloadedAyat
    )

    // Act
    val result = sura.didDownloadAyat(currentSura = 3, start = 1, end = 20)

    // Assert
    assertThat(result).isFalse()
  }

  @Test
  fun `didDownloadAyat returns false when downloadedAyat is empty`() {
    // Arrange
    val sura = PartiallyDownloadedSura(
      sura = 5,
      expectedAyahCount = 120,
      downloadedAyat = emptyList()
    )

    // Act
    val result = sura.didDownloadAyat(currentSura = 5, start = 1, end = 5)

    // Assert
    assertThat(result).isFalse()
  }

  @Test
  fun `didDownloadAyat returns true for single-ayah sura when that ayah is downloaded`() {
    // Arrange — Al-Kawthar (sura 108) has 3 ayahs; test single-ayah range
    val sura = PartiallyDownloadedSura(
      sura = 108,
      expectedAyahCount = 3,
      downloadedAyat = listOf(1, 2, 3)
    )

    // Act
    val result = sura.didDownloadAyat(currentSura = 108, start = 2, end = 2)

    // Assert
    assertThat(result).isTrue()
  }

  @Test
  fun `didDownloadAyat returns false when sura number does not match currentSura`() {
    // Arrange
    val sura = PartiallyDownloadedSura(
      sura = 1,
      expectedAyahCount = 7,
      downloadedAyat = (1..7).toList()
    )

    // Act — query for a different sura
    val result = sura.didDownloadAyat(currentSura = 2, start = 1, end = 7)

    // Assert
    assertThat(result).isFalse()
  }

  @Test
  fun `didDownloadAyat returns true when only first and last ayah are requested and both downloaded`() {
    // Arrange — middle ayahs not downloaded, but we only query boundary ayahs
    val sura = PartiallyDownloadedSura(
      sura = 4,
      expectedAyahCount = 176,
      downloadedAyat = listOf(1, 176)
    )

    // Act
    val result = sura.didDownloadAyat(currentSura = 4, start = 1, end = 1)

    // Assert
    assertThat(result).isTrue()
  }

  @Test
  fun `didDownloadAyat returns false when first ayah present but last missing`() {
    // Arrange
    val sura = PartiallyDownloadedSura(
      sura = 4,
      expectedAyahCount = 176,
      downloadedAyat = listOf(1) // only first ayah downloaded
    )

    // Act — querying range that includes ayah 176
    val result = sura.didDownloadAyat(currentSura = 4, start = 1, end = 176)

    // Assert
    assertThat(result).isFalse()
  }

  @Test
  fun `downloadedAyat count matches the size of the provided list`() {
    // Arrange
    val downloadedAyat = listOf(1, 3, 5, 7)
    val sura = PartiallyDownloadedSura(
      sura = 6,
      expectedAyahCount = 165,
      downloadedAyat = downloadedAyat
    )

    // Act + Assert
    assertThat(sura.downloadedAyat).hasSize(4)
    assertThat(sura.downloadedAyat).containsExactlyElementsIn(downloadedAyat)
  }
}
