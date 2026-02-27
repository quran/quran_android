package com.quran.labs.androidquran.test.fakes

import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider

/**
 * Fake implementation of AudioExtensionDecider for testing.
 *
 * Pattern: Simple return values with configuration
 *
 * Usage:
 * ```
 * val fakeDecider = FakeAudioExtensionDecider()
 * fakeDecider.setAudioExtension("mp3")
 * fakeDecider.setAllowedExtensions(listOf("mp3", "opus"))
 *
 * // Use in presenter
 * val presenter = AudioPresenter(..., fakeDecider, ...)
 * ```
 */
class FakeAudioExtensionDecider : AudioExtensionDecider {

  private var audioExtension: String = "mp3"
  private var allowedExtensions: List<String> = listOf("mp3")

  private val audioExtensionForQariCalls = mutableListOf<Any>()
  private val allowedAudioExtensionsCalls = mutableListOf<Any>()

  override fun audioExtensionForQari(qari: Qari): String {
    audioExtensionForQariCalls.add(qari)
    return audioExtension
  }

  override fun audioExtensionForQari(qariItem: QariItem): String {
    audioExtensionForQariCalls.add(qariItem)
    return audioExtension
  }

  override fun allowedAudioExtensions(qari: Qari): List<String> {
    allowedAudioExtensionsCalls.add(qari)
    return allowedExtensions
  }

  override fun allowedAudioExtensions(qariItem: QariItem): List<String> {
    allowedAudioExtensionsCalls.add(qariItem)
    return allowedExtensions
  }

  // Configuration methods
  fun setAudioExtension(extension: String) {
    audioExtension = extension
  }

  fun setAllowedExtensions(extensions: List<String>) {
    allowedExtensions = extensions
  }

  // Assertion helpers
  fun assertAudioExtensionForQariCalled() {
    require(audioExtensionForQariCalls.isNotEmpty()) {
      "Expected audioExtensionForQari() to be called but it wasn't"
    }
  }

  fun assertAllowedAudioExtensionsCalled() {
    require(allowedAudioExtensionsCalls.isNotEmpty()) {
      "Expected allowedAudioExtensions() to be called but it wasn't"
    }
  }

  // Query helpers
  fun getAudioExtensionForQariCallCount(): Int = audioExtensionForQariCalls.size

  fun getAllowedAudioExtensionsCallCount(): Int = allowedAudioExtensionsCalls.size

  // Reset for test isolation
  fun reset() {
    audioExtensionForQariCalls.clear()
    allowedAudioExtensionsCalls.clear()
    audioExtension = "mp3"
    allowedExtensions = listOf("mp3")
  }
}
