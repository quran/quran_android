package com.quran.data.model.bookmark

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BookmarkTest {

  @Test
  fun `getCommaSeparatedValues preserves benign tag names`() {
    val bookmark = Bookmark("1", 1, 1, 1, 1_700_000_000L, tags = listOf("1"))

    val csv = bookmark.getCommaSeparatedValues(listOf(Tag("1", "CSV Tag")))

    assertThat(csv).isEqualTo("bookmark, 1, 1, 1, 1700000000, CSV Tag,")
  }

  @Test
  fun `getCommaSeparatedValues neutralizes leading formula characters`() {
    val prefixes = listOf(
      "=cmd|'/C calc'!A0",
      "+1+1",
      "-2+3",
      "@SUM(1,2)",
      "\t=1+1",
      "  =HYPERLINK(\"https://evil.example\")"
    )

    prefixes.forEach { maliciousName ->
      val bookmark = Bookmark("1", 1, 1, 1, 1_700_000_000L, tags = listOf("1"))
      val csv = bookmark.getCommaSeparatedValues(listOf(Tag("1", maliciousName)))
      val tagsField = csv.removePrefix("bookmark, 1, 1, 1, 1700000000, ").removeSuffix(",")

      assertThat(tagsField).startsWith("'")
      assertThat(tagsField).doesNotContain(",")
      assertThat(tagsField).doesNotContain("\n")
      assertThat(tagsField).doesNotContain("\r")
    }
  }

  @Test
  fun `getCommaSeparatedValues strips newlines and commas to prevent row or column injection`() {
    val bookmark = Bookmark("1", 1, 1, 1, 1_700_000_000L, tags = listOf("1"))
    val csv = bookmark.getCommaSeparatedValues(
      listOf(Tag("1", "Safe\r\n=1+1,=2+2"))
    )

    assertThat(csv).doesNotContain("\r")
    assertThat(csv).doesNotContain("\n")
    assertThat(csv).isEqualTo("bookmark, 1, 1, 1, 1700000000, Safe  =1+1 =2+2,")
  }

  @Test
  fun `getCommaSeparatedValues neutralizes formula exposed after leading comma removal`() {
    val bookmark = Bookmark("1", 1, 1, 1, 1_700_000_000L, tags = listOf("1"))
    val csv = bookmark.getCommaSeparatedValues(
      listOf(Tag("1", ",=1+1"))
    )

    assertThat(csv).isEqualTo("bookmark, 1, 1, 1, 1700000000, ' =1+1,")
  }
}
