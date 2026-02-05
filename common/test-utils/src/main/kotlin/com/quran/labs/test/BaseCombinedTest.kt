package com.quran.labs.test

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule

/**
 * Base class for tests that involve both coroutines and RxJava.
 *
 * This is useful for testing code that bridges between the two
 * reactive paradigms, such as during migration from Rx to coroutines.
 *
 * Provides both [TestDispatcherRule] and [RxSchedulerRule].
 *
 * Usage:
 * ```
 * class MyHybridTest : BaseCombinedTest() {
 *
 *   @Test
 *   fun `test hybrid behavior`() = runTestWithRule {
 *     // Both Dispatchers.Main and RxJava schedulers are test-friendly
 *     val result = repository.getMixedData()
 *     assertThat(result).isEqualTo(expected)
 *   }
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseCombinedTest {

  @get:Rule
  val dispatcherRule = TestDispatcherRule()

  @get:Rule
  val rxSchedulerRule = RxSchedulerRule()

  /**
   * Runs a test with the test dispatcher.
   * This is a convenience method that wraps [runTest] and uses the rule's dispatcher.
   */
  protected fun runTestWithRule(
    testBody: suspend TestScope.() -> Unit
  ) = runTest(dispatcherRule.testDispatcher) {
    testBody()
  }
}
