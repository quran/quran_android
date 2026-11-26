package com.quran.mobile.feature.sync

import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QuranSyncManagerLogoutTest {

  @Test
  fun `sync manager defers graph initialization when unconfigured`() = runTest {
    val unconfiguredContext = ContextWrapper(null)

    val manager = QuranSyncManager(unconfiguredContext)

    assertThat(manager.isGraphInitialized()).isFalse()
    manager.startForegroundSync()
    manager.warmUpIfConfigured()
    assertThat(manager.isConfigured).isFalse()
    assertThat(manager.canTriggerSync).isFalse()
    assertThat(manager.canTriggerSyncFlow.first()).isFalse()
    assertThat(manager.showBookmarksSignInCardFlow.first()).isFalse()
    assertThat(manager.isGraphInitialized()).isFalse()
  }

  @Test
  fun `logout clears recovery marker when logout succeeds`() = runTest {
    var pendingMarked = false
    var pendingCleared = false
    var errorCleared = false

    executeLogoutWithRecovery(
      markPending = {
        pendingMarked = true
        true
      },
      clearPending = { pendingCleared = true },
      clearError = { errorCleared = true },
      performLogout = {}
    )

    assertThat(pendingMarked).isTrue()
    assertThat(pendingCleared).isTrue()
    assertThat(errorCleared).isFalse()
  }

  @Test
  fun `logout keeps recovery marker when logout throws`() = runTest {
    var pendingMarked = false
    var pendingCleared = false
    var errorCleared = false

    val thrown = try {
      executeLogoutWithRecovery(
        markPending = {
          pendingMarked = true
          true
        },
        clearPending = { pendingCleared = true },
        clearError = { errorCleared = true },
        performLogout = { throw IllegalStateException("network failure") }
      )
      null
    } catch (e: IllegalStateException) {
      e
    }

    assertThat(thrown).isNotNull()
    assertThat(pendingMarked).isTrue()
    assertThat(pendingCleared).isFalse()
    assertThat(errorCleared).isTrue()
  }

  @Test
  fun `logout aborts before calling service when recovery marker fails to persist`() = runTest {
    var logoutCalled = false
    var pendingCleared = false

    val thrown = try {
      executeLogoutWithRecovery(
        markPending = { false },
        clearPending = { pendingCleared = true },
        clearError = {},
        performLogout = { logoutCalled = true }
      )
      null
    } catch (e: IllegalStateException) {
      e
    }

    assertThat(thrown).isNotNull()
    assertThat(logoutCalled).isFalse()
    assertThat(pendingCleared).isFalse()
  }
}
