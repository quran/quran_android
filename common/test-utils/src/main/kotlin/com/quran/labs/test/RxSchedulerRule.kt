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

  override fun starting(description: Description) {
    // Override IO scheduler
    RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
    // Override computation scheduler
    RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
    // Override new thread scheduler
    RxJavaPlugins.setNewThreadSchedulerHandler { Schedulers.trampoline() }
    // Override single scheduler
    RxJavaPlugins.setSingleSchedulerHandler { Schedulers.trampoline() }

    // Override Android main thread scheduler (requires rxandroid)
    try {
      RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
      RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
    } catch (e: ExceptionInInitializerError) {
      // RxAndroid not available, skip
    } catch (e: NoClassDefFoundError) {
      // RxAndroid not available, skip
    }
  }

  override fun finished(description: Description) {
    // Reset all schedulers
    RxJavaPlugins.reset()
    try {
      RxAndroidPlugins.reset()
    } catch (e: ExceptionInInitializerError) {
      // RxAndroid not available, skip
    } catch (e: NoClassDefFoundError) {
      // RxAndroid not available, skip
    }
  }
}
