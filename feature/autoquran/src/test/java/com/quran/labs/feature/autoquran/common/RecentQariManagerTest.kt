package com.quran.labs.feature.autoquran.common

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecentQariManagerTest {

  private lateinit var manager: RecentQariManager

  @Before
  fun setUp() {
    manager = RecentQariManager(RuntimeEnvironment.getApplication())
  }

  @Test
  fun `empty initial state`() {
    assertThat(manager.getRecentQaris()).isEmpty()
  }

  @Test
  fun `record and retrieve single qari`() {
    manager.recordQari(qariId = 1, sura = 36)
    val recents = manager.getRecentQaris()
    assertThat(recents).hasSize(1)
    assertThat(recents[0].qariId).isEqualTo(1)
    assertThat(recents[0].lastSura).isEqualTo(36)
  }

  @Test
  fun `same qari and sura deduplicates and moves to front`() {
    manager.recordQari(qariId = 1, sura = 36)
    manager.recordQari(qariId = 2, sura = 1)
    manager.recordQari(qariId = 1, sura = 36)

    val recents = manager.getRecentQaris()
    assertThat(recents).hasSize(2)
    assertThat(recents[0].qariId).isEqualTo(1)
    assertThat(recents[0].lastSura).isEqualTo(36)
    assertThat(recents[1].qariId).isEqualTo(2)
  }

  @Test
  fun `different suras from same qari are kept separately`() {
    manager.recordQari(qariId = 1, sura = 36)
    manager.recordQari(qariId = 1, sura = 67)

    val recents = manager.getRecentQaris()
    assertThat(recents).hasSize(2)
    assertThat(recents[0].lastSura).isEqualTo(67)
    assertThat(recents[1].lastSura).isEqualTo(36)
  }

  @Test
  fun `maintains recency order`() {
    manager.recordQari(qariId = 1, sura = 1)
    manager.recordQari(qariId = 2, sura = 2)
    manager.recordQari(qariId = 3, sura = 3)

    val recents = manager.getRecentQaris()
    assertThat(recents.map { it.qariId }).containsExactly(3, 2, 1).inOrder()
  }

  @Test
  fun `evicts oldest beyond max entries`() {
    manager.recordQari(qariId = 1, sura = 1)
    manager.recordQari(qariId = 2, sura = 2)
    manager.recordQari(qariId = 3, sura = 3)
    manager.recordQari(qariId = 4, sura = 4)
    manager.recordQari(qariId = 5, sura = 5)
    manager.recordQari(qariId = 6, sura = 6)

    val recents = manager.getRecentQaris()
    assertThat(recents).hasSize(5)
    assertThat(recents.map { it.qariId }).containsExactly(6, 5, 4, 3, 2).inOrder()
  }

  @Test
  fun `handles corrupt json gracefully`() {
    val context = RuntimeEnvironment.getApplication()
    val prefs = context.getSharedPreferences("autoquran_recent", Context.MODE_PRIVATE)
    prefs.edit().putString("recent_qaris", "not valid json").commit()

    val freshManager = RecentQariManager(context)
    assertThat(freshManager.getRecentQaris()).isEmpty()
  }
}
