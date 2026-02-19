package com.quran.labs.androidquran.common.search

import com.google.common.truth.Truth.assertThat
import com.quran.common.search.DefaultSearcher
import org.junit.Before
import org.junit.Test

class DefaultSearcherTest {

  private lateinit var searcher: DefaultSearcher

  @Before
  fun setUp() {
    searcher = DefaultSearcher(
      matchStart = "<b>",
      matchEnd = "</b>",
      ellipses = "..."
    )
  }

  // region getQuery

  @Test
  fun `getQuery with FTS and snippets returns snippet function`() {
    // Arrange
    val table = "quran_text"
    val columns = "sura, ayah, page"
    val searchColumn = "text"
    // Act
    val query = searcher.getQuery(
      withSnippets = true,
      hasFTS = true,
      table = table,
      columns = columns,
      searchColumn = searchColumn
    )
    // Assert
    assertThat(query).contains("snippet(")
    assertThat(query).contains(table)
    assertThat(query).contains("MATCH")
    assertThat(query).contains(columns)
  }

  @Test
  fun `getQuery with FTS but no snippets returns search column directly`() {
    // Arrange
    val table = "quran_text"
    val columns = "sura, ayah, page"
    val searchColumn = "text"
    // Act
    val query = searcher.getQuery(
      withSnippets = false,
      hasFTS = true,
      table = table,
      columns = columns,
      searchColumn = searchColumn
    )
    // Assert
    assertThat(query).doesNotContain("snippet(")
    assertThat(query).contains("MATCH")
    assertThat(query).contains(searchColumn)
  }

  @Test
  fun `getQuery without FTS uses LIKE operator`() {
    // Arrange
    val table = "quran_text"
    val columns = "sura, ayah, page"
    val searchColumn = "text"
    // Act
    val query = searcher.getQuery(
      withSnippets = false,
      hasFTS = false,
      table = table,
      columns = columns,
      searchColumn = searchColumn
    )
    // Assert
    assertThat(query).contains("LIKE")
    assertThat(query).doesNotContain("MATCH")
    assertThat(query).doesNotContain("snippet(")
  }

  @Test
  fun `getQuery includes question mark as bind parameter placeholder`() {
    // Arrange
    val table = "quran_text"
    val columns = "sura, ayah, page"
    val searchColumn = "text"
    // Act
    val query = searcher.getQuery(
      withSnippets = false,
      hasFTS = false,
      table = table,
      columns = columns,
      searchColumn = searchColumn
    )
    // Assert
    assertThat(query).contains("?")
  }

  @Test
  fun `getQuery snippet includes matchStart and matchEnd strings`() {
    // Arrange
    val matchStart = "<<START>>"
    val matchEnd = "<<END>>"
    val searcherWithCustomMarkers = DefaultSearcher(matchStart, matchEnd, "...")
    // Act
    val query = searcherWithCustomMarkers.getQuery(
      withSnippets = true,
      hasFTS = true,
      table = "quran_text",
      columns = "sura",
      searchColumn = "text"
    )
    // Assert
    assertThat(query).contains(matchStart)
    assertThat(query).contains(matchEnd)
  }

  // region getLimit

  @Test
  fun `getLimit with snippets returns empty string (no limit)`() {
    // Act
    val limit = searcher.getLimit(withSnippets = true)
    // Assert
    assertThat(limit).isEmpty()
  }

  @Test
  fun `getLimit without snippets returns LIMIT 10`() {
    // Act
    val limit = searcher.getLimit(withSnippets = false)
    // Assert
    assertThat(limit).contains("LIMIT")
    assertThat(limit).contains("10")
  }

  // region processSearchText

  @Test
  fun `processSearchText with FTS appends asterisk wildcard`() {
    // Arrange
    val term = "rahman"
    // Act
    val processed = searcher.processSearchText(term, hasFTS = true)
    // Assert
    assertThat(processed).isEqualTo("$term*")
  }

  @Test
  fun `processSearchText without FTS wraps term in percent wildcards`() {
    // Arrange
    val term = "rahman"
    // Act
    val processed = searcher.processSearchText(term, hasFTS = false)
    // Assert
    assertThat(processed).isEqualTo("%$term%")
  }

  @Test
  fun `processSearchText with empty string and FTS returns asterisk`() {
    // Arrange
    val term = ""
    // Act
    val processed = searcher.processSearchText(term, hasFTS = true)
    // Assert
    assertThat(processed).isEqualTo("*")
  }

  @Test
  fun `processSearchText with empty string and LIKE returns double percent`() {
    // Arrange
    val term = ""
    // Act
    val processed = searcher.processSearchText(term, hasFTS = false)
    // Assert
    assertThat(processed).isEqualTo("%%")
  }
}
