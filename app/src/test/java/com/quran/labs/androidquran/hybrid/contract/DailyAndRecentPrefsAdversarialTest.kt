package com.quran.labs.androidquran.hybrid.contract

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
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
class DailyAndRecentPrefsAdversarialTest {

  private lateinit var wirdPrefs: SharedPreferences
  private lateinit var recentPrefs: SharedPreferences
  private var epochDay: Long = 20_500L

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    wirdPrefs = context.getSharedPreferences("adv_daily_wird_prefs", Context.MODE_PRIVATE)
    recentPrefs = context.getSharedPreferences("adv_recent_search_prefs", Context.MODE_PRIVATE)
    wirdPrefs.edit().clear().commit()
    recentPrefs.edit().clear().commit()
    epochDay = 20_500L
  }

  @Test
  fun `DailyWirdRepository clamps extreme goalPages, ignores invalid pages, and survives clock rollback`() = runTest {
    val repo = DailyWirdRepository(wirdPrefs) { epochDay }

    repo.setGoalPages(Int.MIN_VALUE)
    assertThat(repo.stateFlow.value.goalPages).isEqualTo(1)

    repo.setGoalPages(Int.MAX_VALUE)
    assertThat(repo.stateFlow.value.goalPages).isEqualTo(604)

    repo.recordPageRead(Int.MIN_VALUE)
    repo.recordPageRead(-1)
    repo.recordPageRead(0)
    repo.recordPageRead(605)
    repo.recordPageRead(Int.MAX_VALUE)
    assertThat(repo.stateFlow.value.pagesReadToday).isEqualTo(0)

    repo.recordPageReadAndLocation(page = 42, suraName = "البقرة", ayah = -10, juz = 99)
    val afterLoc = repo.stateFlow.value
    assertThat(afterLoc.pagesReadToday).isEqualTo(1)
    assertThat(afterLoc.lastPage).isEqualTo(42)
    assertThat(afterLoc.lastAyah).isEqualTo(1)
    assertThat(afterLoc.lastJuz).isEqualTo(30)

    // Clock rollback (today < storedDay) must reset daily set safely without underflow or exception
    epochDay = 20_400L
    repo.refreshIfNewDay()
    val rolledBack = repo.stateFlow.value
    assertThat(rolledBack.pagesReadToday).isEqualTo(0)
    assertThat(rolledBack.lastReadDateEpochDay).isEqualTo(20_400L)
    assertThat(rolledBack.lastPage).isEqualTo(42)
  }

  @Test
  fun `DailyWirdRepository clamps corrupted pre-populated preference values safely`() {
    wirdPrefs.edit()
      .putInt("goal_pages", -500)
      .putLong("last_read_date_epoch_day", epochDay)
      .putStringSet("pages_read_set", setOf("1", "2"))
      .putInt("last_page", 9999)
      .putString("last_sura_name", "")
      .putInt("last_ayah", 0)
      .putInt("last_juz", -3)
      .commit()

    val repo = DailyWirdRepository(wirdPrefs) { epochDay }
    val state = repo.stateFlow.value
    assertThat(state.goalPages).isEqualTo(1)
    assertThat(state.pagesReadToday).isEqualTo(2)
    assertThat(state.lastPage).isEqualTo(604)
    assertThat(state.lastAyah).isEqualTo(1)
    assertThat(state.lastJuz).isEqualTo(1)
  }

  @Test
  fun `RecentSearchRepository survives malformed JSON, non-string array items, huge queries, and maxEntries 0`() = runTest {
    recentPrefs.edit().putString("recent_queries_json", "{not_an_array").commit()
    val brokenObjRepo = RecentSearchRepository(recentPrefs, maxEntries = 5)
    assertThat(brokenObjRepo.recentQueriesFlow.value).isEmpty()

    recentPrefs.edit().putString("recent_queries_json", "[123, null, \"  \", true, \"الله\"]").commit()
    val mixedRepo = RecentSearchRepository(recentPrefs, maxEntries = 5)
    // Only String items are kept (`opt(i) as? String`); non-string & blank items are skipped
    assertThat(mixedRepo.recentQueriesFlow.value).containsExactly("الله")

    val hugeQuery = "ا".repeat(10_000)
    mixedRepo.recordQuery("  $hugeQuery  ")
    assertThat(mixedRepo.recentQueriesFlow.value.first()).hasLength(10_000)
    assertThat(mixedRepo.recentQueriesFlow.value).hasSize(2)

    mixedRepo.clearAll()
    assertThat(mixedRepo.recentQueriesFlow.value).isEmpty()

    // maxEntries = 0 forces immediate eviction cap without IndexOutOfBoundsException
    val zeroCapRepo = RecentSearchRepository(recentPrefs, maxEntries = 0)
    zeroCapRepo.recordQuery("الرحمن")
    assertThat(zeroCapRepo.recentQueriesFlow.value).isEmpty()
  }
}
