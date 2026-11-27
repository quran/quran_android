package com.quran.labs.androidquran.dao.translation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranslationItemTest {

  @Test
  fun `empty query matches every item`() {
    assertThat(makeItem().matchesQuery("")).isTrue()
    assertThat(makeItem().matchesQuery("   ")).isTrue()
  }

  @Test
  fun `query matches display name ignoring case`() {
    val item = makeItem(displayName = "English Saheeh International")

    assertThat(item.matchesQuery("saheeh")).isTrue()
    assertThat(item.matchesQuery("ENGLISH")).isTrue()
    assertThat(item.matchesQuery("  Saheeh International  ")).isTrue()
  }

  @Test
  fun `query matches translator`() {
    val item = makeItem(translator = "Dr. Mustafa Khattab")

    assertThat(item.matchesQuery("khattab")).isTrue()
    assertThat(item.matchesQuery("Mustafa")).isTrue()
  }

  @Test
  fun `query matches localized translator name`() {
    val item = makeItem(translatorNameLocalized = "تفسير ابن كثير")

    assertThat(item.matchesQuery("ابن كثير")).isTrue()
  }

  @Test
  fun `query without any match returns false`() {
    val item = makeItem(
      displayName = "Albanian Translation",
      translator = "Sherif Ahmeti"
    )

    assertThat(item.matchesQuery("urdu")).isFalse()
  }

  @Test
  fun `query does not match when translator names are missing`() {
    val item = makeItem(translator = null, translatorNameLocalized = null)

    assertThat(item.matchesQuery("ahmeti")).isFalse()
  }

  private fun makeItem(
    displayName: String = "Albanian Translation",
    translator: String? = "Sherif Ahmeti",
    translatorNameLocalized: String? = ""
  ): TranslationItem {
    return TranslationItem(
      Translation(
        id = 1,
        minimumVersion = 1,
        currentVersion = 1,
        displayName = displayName,
        downloadType = "database",
        fileName = "translation.db",
        fileUrl = "https://example.com/translation.db",
        saveTo = "translation.db",
        languageCode = "sq",
        translator = translator,
        translatorNameLocalized = translatorNameLocalized
      )
    )
  }
}
