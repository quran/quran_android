package com.quran.labs.test

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit4 TestRule that overrides RxJava schedulers with immediate/trampoline schedulers.
 *
 * This is essential for testing RxJava code synchronously, ensuring that
 * observables execute on the test thread rather than background threads.
 *
 * Usage:
 * ```
 * class MyRepositoryTest {
 *   @get:Rule
 *   val rxRule = RxSchedulerRule()
 *
 *   @Test
 *   fun `test rx behavior`() {
 *     // All RxJava schedulers now execute synchronously
 *     val result = repository.getData().blockingFirst()
 *     assertThat(result).isEqualTo(expected)
 *   }
 * }
 * ```
 *
 * Note: This rule uses trampoline scheduler for all schedulers, which executes
 * work on the current thread in a FIFO manner.
 */
class RxSchedulerRule : TestWatcher() {

  private var rxAndroidAvailable = true

  override fun starting(description: Description) {
    // Override RxJava schedulers
    RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
    RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
    RxJavaPlugins.setNewThreadSchedulerHandler { Schedulers.trampoline() }
    RxJavaPlugins.setSingleSchedulerHandler { Schedulers.trampoline() }

    // Override Android main thread scheduler
    // Only catches class loading errors - other exceptions propagate
    rxAndroidAvailable = trySetupRxAndroid()
  }

  override fun finished(description: Description) {
    RxJavaPlugins.reset()
    if (rxAndroidAvailable) {
      tryResetRxAndroid()
    }
  }

  /**
   * Attempts to set up RxAndroid schedulers.
   * @return true if RxAndroid is available, false otherwise
   */
  private fun trySetupRxAndroid(): Boolean {
    return try {
      RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
      RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
      true
    } catch (e: NoClassDefFoundError) {
      // RxAndroid not in classpath - this is expected in pure JVM tests
      false
    }
  }

  /**
   * Attempts to reset RxAndroid schedulers.
   * Only called if setup succeeded.
   */
  private fun tryResetRxAndroid() {
    try {
      RxAndroidPlugins.reset()
    } catch (e: NoClassDefFoundError) {
      // Should not happen if setup succeeded, but handle gracefully
    }
  }
}
