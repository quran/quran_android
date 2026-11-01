package com.quran.mobile.feature.sync.presenter

import com.quran.mobile.feature.sync.state.SignOutWarning
import com.quran.mobile.feature.sync.state.signOutWarning
import com.quran.shared.pipeline.UnsyncedItemCounts
import org.junit.Assert.assertEquals
import org.junit.Test

class SignOutWarningTest {
  @Test fun `unknown counts warn while zero counts are synced`() {
    assertEquals(SignOutWarning.Unknown, signOutWarning(null, 0))
    assertEquals(SignOutWarning.Synced, signOutWarning(UnsyncedItemCounts(0, 0), 2))
    assertEquals(SignOutWarning.Unsynced(3, 0, 0), signOutWarning(UnsyncedItemCounts(3, 0), 0))
  }

  @Test fun `held counts distinguish blockers from items waiting on them`() {
    assertEquals(SignOutWarning.Unsynced(2, 1, 2), signOutWarning(UnsyncedItemCounts(2, 3), 1))
    assertEquals(SignOutWarning.Unsynced(0, 1, 0), signOutWarning(UnsyncedItemCounts(0, 1), 1))
  }

  @Test fun `missing or stale blockers cannot hide held items or produce negative waiting counts`() {
    assertEquals(SignOutWarning.Unsynced(0, 3, 0), signOutWarning(UnsyncedItemCounts(0, 3), 0))
    assertEquals(SignOutWarning.Unsynced(0, 1, 0), signOutWarning(UnsyncedItemCounts(0, 1), 3))
  }
}
