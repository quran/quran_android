package com.quran.mobile.bookmark.mapper

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Instant

class MappersTest {
  @Test
  fun `legacy recent page seconds become an instant`() {
    val recentPage = Mappers.recentPageMapper(1, 42, 1_700_000_000L)

    assertThat(recentPage.timestamp).isEqualTo(Instant.parse("2023-11-14T22:13:20Z"))
  }

  @Test
  fun `legacy recent page milliseconds keep their precision`() {
    val recentPage = Mappers.recentPageMapper(1, 42, 1_700_000_000_123L)

    assertThat(recentPage.timestamp).isEqualTo(Instant.parse("2023-11-14T22:13:20.123Z"))
  }
}
