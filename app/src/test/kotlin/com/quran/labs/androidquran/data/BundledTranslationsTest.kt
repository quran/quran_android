package com.quran.labs.androidquran.data

import com.google.common.truth.Truth
import com.quran.labs.androidquran.dao.translation.Translation
import org.junit.Test

class BundledTranslationsTest {

  private fun serverTranslation(
    id: Int,
    fileName: String = "quran.en.nah.db",
    languageCode: String = "en"
  ): Translation =
    Translation(
      id = id,
      minimumVersion = 5,
      currentVersion = 1,
      displayName = "Translation $id",
      downloadType = "translation",
      fileName = fileName,
      fileUrl = "https://example.com/$fileName",
      saveTo = "databases",
      languageCode = languageCode
    )

  @Test
  fun `bundles are appended to the server list when missing`() {
    val server = listOf(serverTranslation(1), serverTranslation(2))
    val result = BundledTranslations.mergeServerTranslations(server)

    Truth.assertThat(result).hasSize(3)
    Truth.assertThat(result[2]).isEqualTo(BundledTranslations.cebuano)
  }

  @Test
  fun `bundled translations are not duplicated when server already has the id`() {
    val server = listOf(serverTranslation(1), BundledTranslations.cebuano)
    val result = BundledTranslations.mergeServerTranslations(server)

    Truth.assertThat(result).hasSize(2)
  }

  @Test
  fun `bundled translations are skipped when server has the same file name`() {
    val server =
      listOf(serverTranslation(1), serverTranslation(2, fileName = BundledTranslations.CEBUANO_DATABASE_FILE_NAME))
    val result = BundledTranslations.mergeServerTranslations(server)

    Truth.assertThat(result).containsExactlyElementsIn(server).inOrder()
  }

  @Test
  fun `bundled translations are skipped when server has the same language code`() {
    val server = listOf(serverTranslation(1, languageCode = "ceb"))
    val result = BundledTranslations.mergeServerTranslations(server)

    Truth.assertThat(result).containsExactlyElementsIn(server).inOrder()
  }

  @Test
  fun `bundle ids cannot collide with the ids the server assigns today`() {
    // the live server currently assigns ids below 200; bundled ids stay far above
    Truth.assertThat(BundledTranslations.cebuano.id).isGreaterThan(1000)
  }
}
