package com.quran.labs.test

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Extensions for testing Kotlin Flows.
 *
 * These extensions provide convenient ways to test Flow emissions
 * with proper timeout handling and assertion support.
 */

/**
 * Collects all emissions from a Flow with a timeout.
 *
 * Usage:
 * ```
 * val emissions = myFlow.collectWithTimeout(5.seconds)
 * assertThat(emissions).containsExactly(1, 2, 3)
 * ```
 *
 * @param timeout Maximum time to wait for flow completion. Default is 5 seconds.
 * @return List of all emitted values.
 */
suspend fun <T> Flow<T>.collectWithTimeout(timeout: Duration = 5.seconds): List<T> {
  return withTimeout(timeout) {
    toList()
  }
}

/**
 * Tests a Flow and asserts the first emission equals the expected value.
 *
 * Usage:
 * ```
 * myFlow.assertFirstEmission(expectedValue)
 * ```
 */
suspend fun <T> Flow<T>.assertFirstEmission(expected: T) {
  test {
    assertThat(awaitItem()).isEqualTo(expected)
    cancelAndIgnoreRemainingEvents()
  }
}

/**
 * Tests a Flow and asserts all emissions in order.
 *
 * Usage:
 * ```
 * myFlow.assertEmissions(listOf(1, 2, 3))
 * ```
 */
suspend fun <T> Flow<T>.assertEmissions(expected: List<T>) {
  test {
    expected.forEach { expectedItem ->
      assertThat(awaitItem()).isEqualTo(expectedItem)
    }
    awaitComplete()
  }
}

/**
 * Tests a Flow expecting it to complete without any emissions.
 *
 * Usage:
 * ```
 * emptyFlow.assertNoEmissions()
 * ```
 */
suspend fun <T> Flow<T>.assertNoEmissions() {
  test {
    awaitComplete()
  }
}

/**
 * Tests a Flow expecting an error of the specified type.
 *
 * Usage:
 * ```
 * failingFlow.assertError<IllegalStateException>()
 * ```
 */
suspend inline fun <reified E : Throwable> Flow<*>.assertError() {
  test {
    val error = awaitError()
    assertThat(error).isInstanceOf(E::class.java)
  }
}

/**
 * Tests a Flow expecting an error with a specific message.
 *
 * Usage:
 * ```
 * failingFlow.assertErrorMessage("Something went wrong")
 * ```
 */
suspend fun Flow<*>.assertErrorMessage(expectedMessage: String) {
  test {
    val error = awaitError()
    assertThat(error.message).isEqualTo(expectedMessage)
  }
}

/**
 * Helper extension to skip N items from a Turbine receiver.
 *
 * Usage:
 * ```
 * myFlow.test {
 *   skipItems(3)
 *   assertThat(awaitItem()).isEqualTo(fourthItem)
 * }
 * ```
 */
suspend fun <T> ReceiveTurbine<T>.skipItems(count: Int) {
  repeat(count) {
    awaitItem()
  }
}
