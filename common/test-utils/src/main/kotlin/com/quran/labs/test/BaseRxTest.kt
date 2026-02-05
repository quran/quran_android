package com.quran.labs.test

import org.junit.Rule

/**
 * Base class for tests that involve RxJava.
 *
 * Provides an [RxSchedulerRule] that automatically overrides RxJava schedulers
 * with synchronous trampolines for the duration of each test.
 *
 * Usage:
 * ```
 * class MyRepositoryTest : BaseRxTest() {
 *
 *   @Test
 *   fun `test rx behavior`() {
 *     // All RxJava schedulers now execute synchronously
 *     val result = repository.getData().blockingFirst()
 *     assertThat(result).isEqualTo(expected)
 *   }
 * }
 * ```
 */
abstract class BaseRxTest {

  @get:Rule
  val rxSchedulerRule = RxSchedulerRule()
}
