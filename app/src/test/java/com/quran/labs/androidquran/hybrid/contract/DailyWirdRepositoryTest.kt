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
class DailyWirdRepositoryTest {

  private lateinit var prefs: SharedPreferences
  private var currentEpochDay: Long = 20_000L

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    prefs = context.getSharedPreferences("test_daily_wird_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    currentEpochDay = 20_000L
  }

  @Test
  fun `initial state uses default goal of 20 pages and zero progress`() {
    val repo = DailyWirdRepository(prefs) { currentEpochDay }
    val state = repo.stateFlow.value
    assertThat(state.goalPages).isEqualTo(20)
    assertThat(state.pagesReadToday).isEqualTo(0)
    assertThat(state.progressFraction).isEqualTo(0f)
    assertThat(state.progressPercent).isEqualTo(0)
  }

  @Test
  fun `recordPageRead counts unique pages idempotently and ignores out of range pages`() = runTest {
    val repo = DailyWirdRepository(prefs) { currentEpochDay }

    repo.stateFlow.test {
      assertThat(awaitItem().pagesReadToday).isEqualTo(0)

      repo.recordPageRead(0)
      repo.recordPageRead(605)
      expectNoEvents()

      repo.recordPageRead(5)
      val afterPage5 = awaitItem()
      assertThat(afterPage5.pagesReadToday).isEqualTo(1)
      assertThat(afterPage5.lastPage).isEqualTo(5)
      assertThat(afterPage5.progressFraction).isWithin(0.001f).of(0.05f)

      // Duplicate page read on same day must not double-count
      repo.recordPageRead(5)
      expectNoEvents()

      repo.recordPageRead(6)
      val afterPage6 = awaitItem()
      assertThat(afterPage6.pagesReadToday).isEqualTo(2)
      assertThat(afterPage6.lastPage).isEqualTo(6)
      assertThat(afterPage6.progressPercent).isEqualTo(10)
    }
  }

  @Test
  fun `setGoalPages clamps bounds and recalculates progress`() = runTest {
    val repo = DailyWirdRepository(prefs) { currentEpochDay }
    repo.recordPageRead(1)
    repo.recordPageRead(2)
    repo.setGoalPages(4)

    val state = repo.stateFlow.value
    assertThat(state.goalPages).isEqualTo(4)
    assertThat(state.progressFraction).isWithin(0.001f).of(0.5f)
    assertThat(state.progressPercent).isEqualTo(50)

    repo.setGoalPages(0)
    assertThat(repo.stateFlow.value.goalPages).isEqualTo(1)
    repo.setGoalPages(999)
    assertThat(repo.stateFlow.value.goalPages).isEqualTo(604)
  }

  @Test
  fun `midnight rollover resets daily page count while preserving goal and last location`() = runTest {
    val repo = DailyWirdRepository(prefs) { currentEpochDay }
    repo.setGoalPages(10)
    repo.updateLastLocation(suraName = "سورة البقرة", ayah = 142, page = 22, juz = 2)
    repo.recordPageRead(22)
    repo.recordPageRead(23)
    assertThat(repo.stateFlow.value.pagesReadToday).isEqualTo(2)

    // Advance to next local day
    currentEpochDay += 1L
    repo.refreshIfNewDay()

    val rolledOver = repo.stateFlow.value
    assertThat(rolledOver.pagesReadToday).isEqualTo(0)
    assertThat(rolledOver.goalPages).isEqualTo(10)
    assertThat(rolledOver.lastSuraName).isEqualTo("سورة البقرة")
    assertThat(rolledOver.lastAyah).isEqualTo(142)
    assertThat(rolledOver.lastPage).isEqualTo(23)
    assertThat(rolledOver.lastJuz).isEqualTo(2)

    // Re-instantiating from same SharedPreferences preserves persisted goal & location
    val rehydrated = DailyWirdRepository(prefs) { currentEpochDay }
    assertThat(rehydrated.stateFlow.value.goalPages).isEqualTo(10)
    assertThat(rehydrated.stateFlow.value.lastSuraName).isEqualTo("سورة البقرة")
    assertThat(rehydrated.stateFlow.value.pagesReadToday).isEqualTo(0)
  }
}
