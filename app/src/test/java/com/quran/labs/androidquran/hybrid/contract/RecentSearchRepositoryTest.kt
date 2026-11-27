package com.quran.labs.androidquran.hybrid.contract

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class RecentSearchRepositoryTest {

  private lateinit var prefs: SharedPreferences

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    prefs = context.getSharedPreferences("test_recent_search_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
  }

  @Test
  fun `recordQuery trims, ignores blanks, deduplicates MRU order, and caps max entries`() = runTest {
    val repo = RecentSearchRepository(prefs, maxEntries = 3)

    repo.recentQueriesFlow.test {
      assertThat(awaitItem()).isEmpty()

      repo.recordQuery("   ")
      expectNoEvents()

      repo.recordQuery("الله")
      assertThat(awaitItem()).containsExactly("الله").inOrder()

      repo.recordQuery(" الرحمن ")
      assertThat(awaitItem()).containsExactly("الرحمن", "الله").inOrder()

      repo.recordQuery("الكهف")
      assertThat(awaitItem()).containsExactly("الكهف", "الرحمن", "الله").inOrder()

      // Duplicate moves to index 0 without increasing count
      repo.recordQuery("الله")
      assertThat(awaitItem()).containsExactly("الله", "الكهف", "الرحمن").inOrder()

      // Exceeding maxEntries evicts oldest ("الرحمن")
      repo.recordQuery("الصلاة")
      assertThat(awaitItem()).containsExactly("الصلاة", "الله", "الكهف").inOrder()
    }

    // Persisted across repository re-creation
    val rehydrated = RecentSearchRepository(prefs, maxEntries = 3)
    assertThat(rehydrated.recentQueriesFlow.value)
      .containsExactly("الصلاة", "الله", "الكهف")
      .inOrder()
  }

  @Test
  fun `removeQuery and clearAll update flow and persistence`() = runTest {
    val repo = RecentSearchRepository(prefs)
    repo.recordQuery("الله")
    repo.recordQuery("الرحمن")

    repo.removeQuery("الله")
    assertThat(repo.recentQueriesFlow.value).containsExactly("الرحمن")

    repo.clearAll()
    assertThat(repo.recentQueriesFlow.value).isEmpty()
    assertThat(RecentSearchRepository(prefs).recentQueriesFlow.value).isEmpty()
  }

  @Test
  fun `loadQueries safely ignores null or non-string entries and non-string preference corruption`() {
    prefs.edit().putString("recent_queries_json", "[null, 123, \"  \", \"الله\", true, \"الرحمن\"]").commit()
    val repo = RecentSearchRepository(prefs)
    assertThat(repo.recentQueriesFlow.value).containsExactly("الله", "الرحمن").inOrder()

    prefs.edit().putInt("recent_queries_json", 42).commit()
    val recovered = RecentSearchRepository(prefs)
    assertThat(recovered.recentQueriesFlow.value).isEmpty()
  }
}
