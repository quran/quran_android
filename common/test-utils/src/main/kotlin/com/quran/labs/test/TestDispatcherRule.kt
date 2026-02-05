package com.quran.labs.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit4 TestRule that sets [Dispatchers.Main] to a test dispatcher.
 *
 * This rule is essential for testing coroutines that use [Dispatchers.Main],
 * which is common in Android ViewModels and presenters.
 *
 * Usage:
 * ```
 * class MyViewModelTest {
 *   @get:Rule
 *   val dispatcherRule = TestDispatcherRule()
 *
 *   @Test
 *   fun `test coroutine behavior`() = runTest {
 *     // Dispatchers.Main is now the test dispatcher
 *     val viewModel = MyViewModel()
 *     // test your coroutine logic
 *   }
 * }
 * ```
 *
 * @param testDispatcher The [TestDispatcher] to use. Defaults to [StandardTestDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherRule(
  val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

  override fun starting(description: Description) {
    Dispatchers.setMain(testDispatcher)
  }

  override fun finished(description: Description) {
    Dispatchers.resetMain()
  }
}
