package com.quran.labs.test

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule

/**
 * Base class for tests that involve coroutines.
 *
 * Provides a [TestDispatcherRule] that automatically sets [Dispatchers.Main]
 * to a test dispatcher for the duration of each test.
 *
 * Usage:
 * ```
 * class MyViewModelTest : BaseCoroutineTest() {
 *
 *   @Test
 *   fun `test coroutine behavior`() = runTestWithRule {
 *     // Dispatchers.Main is now the test dispatcher
 *     val viewModel = MyViewModel()
 *     // test your coroutine logic
 *   }
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseCoroutineTest {

  @get:Rule
  val dispatcherRule = TestDispatcherRule()

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
