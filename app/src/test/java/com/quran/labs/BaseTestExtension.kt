package com.quran.labs

import io.reactivex.rxjava3.observers.TestObserver

/**
 * Waits until the any terminal event has been received by this TestObserver/TestSubscriber
 * or returns false if the wait has been interrupted.
 * @return true if the TestObserver/TestSubscriber terminated, false if the wait has been interrupted
 */
object BaseTestExtension {
  @JvmStatic
  fun <T> awaitTerminalEvent(testObserver: TestObserver<T>): Boolean {
    return try {
      testObserver.await()
      true
    } catch (ex: InterruptedException) {
      Thread.currentThread().interrupt()
      false
    }
  }
}

// Same as above function, but for Kotlin.
fun <T> TestObserver<T>.awaitTerminalEvent(): Boolean {
  return try {
    this.await()
    true
  } catch (ex: InterruptedException) {
    Thread.currentThread().interrupt()
    false
  }
}
