package com.quran.labs.androidquran.common.search

import com.google.common.truth.Truth.assertThat
import com.quran.common.search.ArabicSearcher
import com.quran.common.search.DefaultSearcher
import com.quran.common.search.Searcher
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ArabicSearcher] covering pure-Kotlin logic:
 * - [ArabicSearcher.getQuery] delegates to [DefaultSearcher] with withSnippets=false
 * - [ArabicSearcher.getLimit] always returns empty string
 * - [ArabicSearcher.processSearchText] normalises Arabic chars to LIKE wildcards
 *
 * [ArabicSearcher.runQuery] is excluded because it requires an Android [SQLiteDatabase].
 */
class ArabicSearcherTest {

  /** A fake [Searcher] that records what arguments were passed to it. */
  private class FakeSearcher(
    private val queryResponse: String = "fake_query",
    private val limitResponse: String = "LIMIT 5",
    private val processedText: String = "fake_processed"
  ) : Searcher {
    var lastGetQueryWithSnippets: Boolean? = null
    var lastGetQueryHasFTS: Boolean? = null

    override fun getQuery(
      withSnippets: Boolean,
      hasFTS: Boolean,
      table: String,
      columns: String,
      searchColumn: String
    ): String {
      lastGetQueryWithSnippets = withSnippets
      lastGetQueryHasFTS = hasFTS
      return queryResponse
    }

    override fun getLimit(withSnippets: Boolean): String = limitResponse

    override fun processSearchText(searchText: String, hasFTS: Boolean): String = processedText

    override fun runQuery(
      database: android.database.sqlite.SQLiteDatabase,
      query: String,
      searchText: String,
      originalSearchText: String,
      withSnippets: Boolean,
      columns: Array<String>
    ): android.database.Cursor {
      throw UnsupportedOperationException("not needed in unit tests")
    }
  }

  private lateinit var fakeDefaultSearcher: FakeSearcher
  private lateinit var arabicSearcher: ArabicSearcher

  @Before
  fun setUp() {
    fakeDefaultSearcher = FakeSearcher()
    arabicSearcher = ArabicSearcher(
      defaultSearcher = fakeDefaultSearcher,
      matchStart = "<b>",
      matchEnd = "</b>"
    )
  }

  // region getQuery

  @Test
  fun `getQuery always delegates with withSnippets false regardless of caller value`() {
    // Arrange / Act
    arabicSearcher.getQuery(
      withSnippets = true,
      hasFTS = true,
      table = "quran_text",
      columns = "sura, ayah",
      searchColumn = "text"
    )
    // Assert: Arabic searcher must pass false for snippets to the underlying searcher
    assertThat(fakeDefaultSearcher.lastGetQueryWithSnippets).isFalse()
  }

  @Test
  fun `getQuery passes hasFTS value through to default searcher`() {
    // Arrange / Act
    arabicSearcher.getQuery(
      withSnippets = false,
      hasFTS = true,
      table = "quran_text",
      columns = "sura, ayah",
      searchColumn = "text"
    )
    // Assert
    assertThat(fakeDefaultSearcher.lastGetQueryHasFTS).isTrue()
  }

  @Test
  fun `getQuery returns the value produced by the default searcher`() {
    // Arrange
    val expectedQuery = "fake_query"
    // Act
    val result = arabicSearcher.getQuery(
      withSnippets = false,
      hasFTS = false,
      table = "quran_text",
      columns = "sura",
      searchColumn = "text"
    )
    // Assert
    assertThat(result).isEqualTo(expectedQuery)
  }

  // region getLimit

  @Test
  fun `getLimit returns empty string when withSnippets is true`() {
    // Act
    val limit = arabicSearcher.getLimit(withSnippets = true)
    // Assert
    assertThat(limit).isEmpty()
  }

  @Test
  fun `getLimit returns empty string when withSnippets is false`() {
    // Act
    val limit = arabicSearcher.getLimit(withSnippets = false)
    // Assert
    assertThat(limit).isEmpty()
  }

  // region processSearchText

  @Test
  fun `processSearchText replaces arabic chars with LIKE wildcard underscore`() {
    // Arrange: ا (\u0627) is in arabicRegexChars, so it should become '_'
    val arabicInput = "\u0627\u0644\u0644\u0647"
    // Act
    val result = arabicSearcher.processSearchText(arabicInput, hasFTS = false)
    // Assert: every Arabic replacement char should have become _
    // The result is then wrapped by DefaultSearcher (our fake returns "fake_processed"),
    // but we need to call the real DefaultSearcher to verify the wrapping.
    // Instead, test with the real DefaultSearcher wired in.
    val realSearcher = ArabicSearcher(
      defaultSearcher = DefaultSearcher("<b>", "</b>", "..."),
      matchStart = "<b>",
      matchEnd = "</b>"
    )
    val realResult = realSearcher.processSearchText(arabicInput, hasFTS = false)
    // The plain-alef char should have been replaced by _ before LIKE wrapping
    assertThat(realResult).contains("_")
    // Should be wrapped in % for LIKE
    assertThat(realResult).startsWith("%")
    assertThat(realResult).endsWith("%")
  }

  @Test
  fun `processSearchText on empty string produces double percent for LIKE`() {
    // Arrange
    val realSearcher = ArabicSearcher(
      defaultSearcher = DefaultSearcher("<b>", "</b>", "..."),
      matchStart = "<b>",
      matchEnd = "</b>"
    )
    // Act
    val result = realSearcher.processSearchText("", hasFTS = false)
    // Assert
    assertThat(result).isEqualTo("%%")
  }

  @Test
  fun `processSearchText keeps non-arabic characters intact`() {
    // Arrange: Latin letters are not in arabicRegexChars
    val latinInput = "abc"
    val realSearcher = ArabicSearcher(
      defaultSearcher = DefaultSearcher("<b>", "</b>", "..."),
      matchStart = "<b>",
      matchEnd = "</b>"
    )
    // Act
    val result = realSearcher.processSearchText(latinInput, hasFTS = false)
    // Assert: wrapped in % but the core text is untouched
    assertThat(result).isEqualTo("%$latinInput%")
  }

  @Test
  fun `processSearchText converts all recognized arabic chars to underscores`() {
    // Arrange: all arabicRegexChars = اأءتةهوىئ (\u0627\u0623\u0621\u062a\u0629\u0647\u0648\u0649\u0626)
    val allArabicRegexChars = "\u0627\u0623\u0621\u062a\u0629\u0647\u0648\u0649\u0626"
    val realSearcher = ArabicSearcher(
      defaultSearcher = DefaultSearcher("<b>", "</b>", "..."),
      matchStart = "<b>",
      matchEnd = "</b>"
    )
    // Act
    val result = realSearcher.processSearchText(allArabicRegexChars, hasFTS = false)
    // Assert: inner content should be all underscores (one per input char), wrapped in %
    val innerContent = result.removeSurrounding("%")
    assertThat(innerContent).isEqualTo("_".repeat(allArabicRegexChars.length))
  }

  @Test
  fun `processSearchText always uses LIKE wildcards regardless of hasFTS value`() {
    // Arrange: ArabicSearcher hard-codes hasFTS=false when delegating to DefaultSearcher,
    // so the result is always LIKE-formatted even when the caller passes hasFTS=true.
    val realSearcher = ArabicSearcher(
      defaultSearcher = DefaultSearcher("<b>", "</b>", "..."),
      matchStart = "<b>",
      matchEnd = "</b>"
    )
    // Act
    val resultWhenFts = realSearcher.processSearchText("abc", hasFTS = true)
    val resultWhenNoFts = realSearcher.processSearchText("abc", hasFTS = false)
    // Assert: both cases produce LIKE-style %...% wrapping, never FTS-style *
    assertThat(resultWhenFts).isEqualTo("%abc%")
    assertThat(resultWhenNoFts).isEqualTo("%abc%")
  }
}
