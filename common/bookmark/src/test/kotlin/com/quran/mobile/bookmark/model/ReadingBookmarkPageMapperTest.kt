package com.quran.mobile.bookmark.model

import com.google.common.truth.Truth.assertThat
import com.quran.data.dao.Settings
import com.quran.data.model.PageAppearance
import com.quran.data.source.MushafType
import com.quran.shared.persistence.model.Mushaf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test

class ReadingBookmarkPageMapperTest {

  // indopak is the fallback page type, so a result of madani's mushaf comes from the reference page
  // type and one of indopak's from the fallback page type
  private val mapper = ReadingBookmarkPageMapper(
    settings = UnavailableSettings(),
    pageProviders = mapOf(
      "madani" to TestPageProvider(MushafType.MADANI_1405),
      "indopak" to TestPageProvider(MushafType.INDOPAK_15_LINES),
      "shemerly_layout" to TestPageProvider(MushafType.SHEMERLY)
    ),
    fallbackPageType = "indopak"
  )

  @Test
  fun `a page type's pages are in its provider's mushaf`() {
    assertThat(mapper.mushafFor("shemerly_layout")).isEqualTo(Mushaf.SHEMERLY)
  }

  @Test
  fun `an unregistered page type's pages are in the fallback page type's mushaf`() {
    assertThat(mapper.mushafFor("unregistered")).isEqualTo(Mushaf.INDOPAK_15_LINES)
  }

  @Test
  fun `a backup of a registered page type is imported in its provider's mushaf`() {
    assertThat(mapper.sourceMushafFor("shemerly_layout")).isEqualTo(Mushaf.SHEMERLY)
  }

  @Test
  fun `a backup of a page type this build lacks is imported in the reference page type's mushaf`() {
    assertThat(mapper.sourceMushafFor("unregistered")).isEqualTo(Mushaf.MADANI_1405)
  }

  @Test
  fun `a backup without a page type is imported in the reference page type's mushaf`() {
    assertThat(listOf(null, "").map(mapper::sourceMushafFor))
      .containsExactly(Mushaf.MADANI_1405, Mushaf.MADANI_1405)
  }

  /** The mushaf lookups never read settings. */
  private class UnavailableSettings : Settings {
    override suspend fun setVersion(version: Int) = unused()

    override suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean) = unused()

    override suspend fun lastPage(): Int = unused()

    override suspend fun pageAppearance(): PageAppearance = unused()

    override suspend fun pageTextBrightness(): Int = unused()

    override suspend fun nightModeBackgroundBrightness(): Int = unused()

    override suspend fun dayPageBackgroundColor(): Int? = unused()

    override suspend fun shouldShowHeaderFooter(): Boolean = unused()

    override suspend fun shouldShowBookmarks(): Boolean = unused()

    override suspend fun pageType(): String = unused()

    override suspend fun setPageType(pageType: String) = unused()

    override suspend fun showSidelines(): Boolean = unused()

    override suspend fun setShowSidelines(show: Boolean) = unused()

    override suspend fun showLineDividers(): Boolean = unused()

    override suspend fun setShouldShowLineDividers(show: Boolean) = unused()

    override suspend fun setAyahTextSize(value: Int) = unused()

    override suspend fun translationTextSize(): Int = unused()

    override fun preferencesFlow(): Flow<String> = emptyFlow()

    private fun unused(): Nothing = throw UnsupportedOperationException()
  }
}
