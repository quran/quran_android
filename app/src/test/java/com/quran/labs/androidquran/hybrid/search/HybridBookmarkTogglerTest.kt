package com.quran.labs.androidquran.hybrid.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HybridBookmarkTogglerTest {

  @Test
  fun `toggleAyahBookmark adds and removes ayah bookmark and updates bookmarkedKeysFlow`() = runTest {
    val dao = FakeBookmarksDao { suraAyah -> if (suraAyah.sura == 1) 1 else 42 }
    val toggler = HybridBookmarkToggler(dao)

    toggler.bookmarkedKeysFlow.test {
      assertThat(awaitItem()).isEmpty()

      val added = toggler.toggleAyahBookmark(sura = 2, ayah = 255)
      assertThat(added).isTrue()
      assertThat(awaitItem()).containsExactly(ayahBookmarkKey(2, 255))

      val removed = toggler.toggleAyahBookmark(sura = 2, ayah = 255)
      assertThat(removed).isFalse()
      assertThat(awaitItem()).isEmpty()
    }
  }

  @Test
  fun `toggleAyahBookmark ignores non-positive sura or ayah`() = runTest {
    val dao = FakeBookmarksDao()
    val toggler = HybridBookmarkToggler(dao)

    assertThat(toggler.toggleAyahBookmark(0, 1)).isFalse()
    assertThat(toggler.toggleAyahBookmark(1, 0)).isFalse()
    assertThat(dao.currentBookmarks()).isEmpty()
  }
}
